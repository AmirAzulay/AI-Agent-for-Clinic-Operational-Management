package com.clinicagent.clinic.history;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * Keeps the database under its size budget (PLAN.md §3.6), as a safety net behind the normal
 * retention. Every {@code check-interval} it reads the real database size:
 *
 * <ul>
 *   <li>below the soft limit: nothing to do; history writes are allowed;</li>
 *   <li>at or above the soft limit: deletes history older than progressively shorter ages
 *       (7 days, 3 days, 1 day, 12 h, 6 h, 1 h) and compacts the history tables after each step,
 *       stopping as soon as the database is below the soft limit again;</li>
 *   <li>still at or above the hard limit after that: history writes are paused. The simulation's
 *       snapshot writer checks {@link #historyWritesAllowed()} before inserting; the clinic itself
 *       keeps working.</li>
 * </ul>
 *
 * <p>Deleted rows only shrink the reported size after compaction, so each trim step runs
 * {@code VACUUM FULL} on the history tables. That briefly locks those tables; they are small by
 * design, so the lock lasts seconds.
 */
@Component
public class DatabaseSizeGuard implements HealthIndicator {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSizeGuard.class);

    static final List<Duration> TRIM_STEPS = List.of(
            Duration.ofDays(7), Duration.ofDays(3), Duration.ofDays(1),
            Duration.ofHours(12), Duration.ofHours(6), Duration.ofHours(1));

    private static final List<String> COMPACTED_TABLES =
            List.of("department_snapshots", "medication_snapshots", "system_events");

    private final JdbcTemplate jdbc;
    private final HistoryRetentionJob retention;
    private final HistoryRetentionProperties retentionProperties;
    private final DatabaseSizeGuardProperties properties;

    private volatile boolean writesAllowed = true;
    private volatile long lastSizeBytes = -1;

    public DatabaseSizeGuard(JdbcTemplate jdbc, HistoryRetentionJob retention,
                             HistoryRetentionProperties retentionProperties, DatabaseSizeGuardProperties properties) {
        this.jdbc = jdbc;
        this.retention = retention;
        this.retentionProperties = retentionProperties;
        this.properties = properties;
    }

    /** False while the database is over its hard limit; the snapshot writer must skip inserts then. */
    public boolean historyWritesAllowed() {
        return writesAllowed;
    }

    @Scheduled(initialDelayString = "PT1M", fixedDelayString = "${history.size-guard.check-interval:PT10M}")
    public void scheduledCheck() {
        if (!properties.enabled()) {
            return;
        }
        try {
            check();
        } catch (RuntimeException e) {
            log.error("Database size check failed", e);
        }
    }

    /** Measures the database, trims if needed and returns the size in bytes afterwards. */
    public long check() {
        long size = databaseSize();
        if (size >= properties.softLimitBytes()) {
            log.warn("Database is {} MB, at or above the {} MB soft limit; trimming history",
                    mb(size), properties.softLimitMb());
            Duration evidenceAge = Duration.ofDays(retentionProperties.evidenceDays());
            for (Duration maxAge : TRIM_STEPS) {
                if (maxAge.compareTo(Duration.ofDays(retentionProperties.days())) >= 0) {
                    continue; // normal retention already keeps less than this
                }
                retention.run(maxAge, evidenceAge, "size-guard: keep " + maxAge);
                compactHistoryTables();
                size = databaseSize();
                if (size < properties.softLimitBytes()) {
                    break;
                }
            }
        }
        boolean allowed = size < properties.hardLimitBytes();
        if (allowed != writesAllowed) {
            if (allowed) {
                log.info("Database is {} MB, below the {} MB hard limit; history writes resumed",
                        mb(size), properties.hardLimitMb());
            } else {
                log.error("Database is {} MB, at or above the {} MB hard limit; history writes paused",
                        mb(size), properties.hardLimitMb());
            }
            recordStateChange(allowed, size);
        }
        writesAllowed = allowed;
        lastSizeBytes = size;
        return size;
    }

    @Override
    public Health health() {
        long size = lastSizeBytes >= 0 ? lastSizeBytes : databaseSize();
        // Stays UP while paused: the clinic still works, only history writing is held back.
        return Health.up()
                .withDetail("sizeMb", mb(size))
                .withDetail("softLimitMb", properties.softLimitMb())
                .withDetail("hardLimitMb", properties.hardLimitMb())
                .withDetail("historyWritesAllowed", writesAllowed)
                .build();
    }

    long databaseSize() {
        return jdbc.queryForObject("select pg_database_size(current_database())", Long.class);
    }

    private void compactHistoryTables() {
        // Table names are constants; VACUUM cannot run inside a transaction, JdbcTemplate autocommits.
        for (String table : COMPACTED_TABLES) {
            jdbc.execute("vacuum full " + table);
        }
    }

    private void recordStateChange(boolean allowed, long size) {
        jdbc.update("""
                insert into system_events (level, component, message, details)
                values (?, 'database-size-guard', ?, jsonb_build_object('size_mb', ?, 'hard_limit_mb', ?))
                """,
                allowed ? "INFO" : "ERROR",
                allowed ? "History writes resumed" : "History writes paused: database over its size limit",
                mb(size), properties.hardLimitMb());
    }

    private static long mb(long bytes) {
        return bytes / (1024 * 1024);
    }
}
