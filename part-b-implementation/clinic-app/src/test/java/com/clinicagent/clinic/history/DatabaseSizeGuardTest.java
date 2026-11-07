package com.clinicagent.clinic.history;

import com.clinicagent.clinic.PostgresIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.health.Status;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The size guard against a real PostgreSQL and real database sizes. Not @Transactional: the guard
 * runs VACUUM FULL, which PostgreSQL refuses inside a transaction, so each test cleans up after itself.
 */
class DatabaseSizeGuardTest extends PostgresIntegrationTest {

    private static final long MB = 1024 * 1024;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    HistoryRetentionJob retention;

    @Autowired
    HistoryRetentionProperties retentionProperties;

    long runId;

    @BeforeEach
    void createRun() {
        runId = jdbc.queryForObject(
                "insert into simulation_runs (seed, tick_interval_sec) values (1, 5) returning id", Long.class);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("delete from department_snapshots where run_id = ?", runId);
        jdbc.update("delete from simulation_runs where id = ?", runId);
    }

    private DatabaseSizeGuard guard(long softMb, long hardMb) {
        return new DatabaseSizeGuard(jdbc, retention, retentionProperties,
                new DatabaseSizeGuardProperties(true, softMb, hardMb, Duration.ofMinutes(10)));
    }

    /** Inserts {@code ticks} department snapshot rows taken {@code age} ago (about 290 bytes each). */
    private void snapshots(int ticks, Duration age, long firstTick) {
        jdbc.update("""
                insert into department_snapshots (run_id, tick, taken_at, department_id, status, waiting_count,
                    avg_wait_min, max_wait_min, rooms_available, rooms_occupied, rooms_inactive, doctors_available)
                select ?, ? + t, now() - ?::interval, (select min(id) from departments), 'NORMAL', 5, 12.5, 30.1, 2, 1, 1, 3
                from generate_series(0, ? - 1) t
                """, runId, firstTick, age.toSeconds() + " seconds", ticks);
    }

    private int snapshotsOfThisRun() {
        return jdbc.queryForObject("select count(*) from department_snapshots where run_id = ?", Integer.class, runId);
    }

    @Test
    void belowTheSoftLimitNothingIsDeleted() {
        snapshots(100, Duration.ofDays(10), 0);
        DatabaseSizeGuard guard = guard(100_000, 100_000);

        guard.check();

        assertThat(snapshotsOfThisRun()).isEqualTo(100);
        assertThat(guard.historyWritesAllowed()).isTrue();
        assertThat(guard.health().getStatus()).isEqualTo(Status.UP);
        assertThat(guard.health().getDetails()).containsEntry("historyWritesAllowed", true);
    }

    @Test
    void aboveTheSoftLimitTheOldestHistoryIsTrimmedUntilTheDatabaseFits() {
        snapshots(60_000, Duration.ofDays(10), 0);   // ~17 MB of old history
        snapshots(10, Duration.ofHours(2), 60_000);  // recent history that must survive
        long before = guard(100_000, 100_000).databaseSize();
        long softMb = before / MB - 5;                // below the current size, above the size without old rows
        DatabaseSizeGuard guard = guard(softMb, softMb + 50);

        long after = guard.check();

        assertThat(after).isLessThan(softMb * MB);
        assertThat(snapshotsOfThisRun()).isEqualTo(10);
        assertThat(guard.historyWritesAllowed()).isTrue();
    }

    @Test
    void ifTrimmingCannotGetBelowTheHardLimitHistoryWritesArePaused() {
        snapshots(10, Duration.ofMinutes(5), 0);      // younger than the shortest trim step: never deleted
        DatabaseSizeGuard guard = guard(1, 1);        // any real database is bigger than 1 MB

        guard.check();

        assertThat(guard.historyWritesAllowed()).isFalse();
        assertThat(snapshotsOfThisRun()).isEqualTo(10);
        assertThat(guard.health().getStatus()).isEqualTo(Status.UP);
        assertThat(guard.health().getDetails()).containsEntry("historyWritesAllowed", false);
        String level = jdbc.queryForObject(
                "select level from system_events where component = 'database-size-guard' order by id desc limit 1",
                String.class);
        assertThat(level).isEqualTo("ERROR");
    }
}
