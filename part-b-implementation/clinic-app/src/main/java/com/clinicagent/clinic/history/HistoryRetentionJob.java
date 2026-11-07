package com.clinicagent.clinic.history;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Deletes old history rows once a day (PLAN.md §3.5). A hard delete by age, not a soft delete:
 * the point is to keep the append-only history tables small.
 *
 * <p>Deletes run in batches of {@code batch-size} rows, each its own short statement, so the job
 * never holds long locks while the simulation is ticking. Tables are processed in foreign-key
 * order ({@code ai_actions} before {@code ai_scans}). Each run records what it deleted as a
 * {@code system_events} row.
 */
@Component
public class HistoryRetentionJob {

    private static final Logger log = LoggerFactory.getLogger(HistoryRetentionJob.class);

    private final JdbcTemplate jdbc;
    private final HistoryRetentionProperties properties;
    private final ObjectMapper json;
    private final Clock clock;

    public HistoryRetentionJob(JdbcTemplate jdbc, HistoryRetentionProperties properties, ObjectMapper json,
                               Clock clock) {
        this.jdbc = jdbc;
        this.properties = properties;
        this.json = json;
        this.clock = clock;
    }

    @Scheduled(cron = "${history.retention.cron:0 30 3 * * *}")
    public void scheduledRun() {
        if (!properties.enabled()) {
            return;
        }
        try {
            run();
        } catch (RuntimeException e) {
            log.error("History retention failed", e);
        }
    }

    /** Deletes everything older than the configured ages and returns the row count per table. */
    public RetentionResult run() {
        Instant now = clock.instant();
        Instant cutoff = now.minus(Duration.ofDays(properties.days()));
        Instant evidenceCutoff = now.minus(Duration.ofDays(properties.evidenceDays()));

        Map<String, Integer> deleted = new LinkedHashMap<>();
        deleted.put("department_snapshots", deleteOlderThan("department_snapshots", "taken_at", cutoff, ""));
        deleted.put("medication_snapshots", deleteOlderThan("medication_snapshots", "taken_at", cutoff, ""));
        deleted.put("system_events", deleteOlderThan("system_events", "created_at", cutoff, ""));
        deleted.put("ai_actions", deleteOlderThan("ai_actions", "created_at", evidenceCutoff, ""));
        // A scan is only deleted once none of its actions remain, so a newer action never loses its scan.
        deleted.put("ai_scans", deleteOlderThan("ai_scans", "started_at", evidenceCutoff,
                "and not exists (select 1 from ai_actions a where a.scan_id = t.id)"));
        deleted.put("evaluation_results", deleteOlderThan("evaluation_results", "created_at", evidenceCutoff, ""));

        RetentionResult result = new RetentionResult(now, cutoff, evidenceCutoff, deleted);
        record(result);
        log.info("History retention removed {} row(s): {}", result.total(), deleted);
        return result;
    }

    private int deleteOlderThan(String table, String timeColumn, Instant cutoff, String extraCondition) {
        // Table and column names come from the constants above, never from input.
        String sql = """
                delete from %1$s where id in (
                    select t.id from %1$s t where t.%2$s < ? %3$s order by t.id limit ?)
                """.formatted(table, timeColumn, extraCondition);
        int total = 0;
        int batch;
        do {
            batch = jdbc.update(sql, Timestamp.from(cutoff), properties.batchSize());
            total += batch;
        } while (batch == properties.batchSize());
        return total;
    }

    private void record(RetentionResult result) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("cutoff", result.cutoff().toString());
        details.put("evidence_cutoff", result.evidenceCutoff().toString());
        details.put("deleted", result.deleted());
        try {
            jdbc.update("insert into system_events (level, component, message, details) values ('INFO', ?, ?, ?::jsonb)",
                    "history-retention", "Deleted " + result.total() + " history row(s)", json.writeValueAsString(details));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    public record RetentionResult(Instant ranAt, Instant cutoff, Instant evidenceCutoff, Map<String, Integer> deleted) {
        public int total() {
            return deleted.values().stream().mapToInt(Integer::intValue).sum();
        }
    }
}
