package com.clinicagent.clinic.history;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Retention against a real PostgreSQL. The job runs with a pinned clock and a batch size of 2, so
 * the batching loop is exercised. Each test runs in a transaction that is rolled back.
 */
@Transactional
class HistoryRetentionJobTest extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    ObjectMapper json;

    HistoryRetentionJob job;
    Instant now;
    long runId;
    long departmentId;
    long medicationId;

    @BeforeEach
    void setUp() {
        now = Instant.parse("2026-06-01T12:00:00Z");
        job = new HistoryRetentionJob(jdbc, new HistoryRetentionProperties(true, null, 30, 180, 2), json,
                Clock.fixed(now, ZoneOffset.UTC));
        runId = jdbc.queryForObject(
                "insert into simulation_runs (seed, tick_interval_sec) values (1, 5) returning id", Long.class);
        departmentId = jdbc.queryForObject("select min(id) from departments", Long.class);
        medicationId = jdbc.queryForObject("select min(id) from medications", Long.class);
    }

    private Timestamp daysAgo(int days) {
        return Timestamp.from(now.minus(Duration.ofDays(days)).truncatedTo(ChronoUnit.SECONDS));
    }

    private void departmentSnapshot(long tick, int daysAgo) {
        jdbc.update("""
                insert into department_snapshots (run_id, tick, taken_at, department_id, status, waiting_count,
                    avg_wait_min, max_wait_min, rooms_available, rooms_occupied, rooms_inactive, doctors_available)
                values (?, ?, ?, ?, 'NORMAL', 0, 0, 0, 3, 0, 1, 3)
                """, runId, tick, daysAgo(daysAgo), departmentId);
    }

    private void medicationSnapshot(long tick, int daysAgo) {
        jdbc.update("""
                insert into medication_snapshots (run_id, tick, taken_at, medication_id, stock, threshold)
                values (?, ?, ?, ?, 100, 10)
                """, runId, tick, daysAgo(daysAgo), medicationId);
    }

    private long scan(int daysAgo) {
        return jdbc.queryForObject(
                "insert into ai_scans (started_at, trigger_type) values (?, 'SCHEDULED') returning id",
                Long.class, daysAgo(daysAgo));
    }

    private void action(long scanId, int daysAgo) {
        jdbc.update("""
                insert into ai_actions (scan_id, tool, explanation, validation_result, created_at)
                values (?, 'GENERATE_SUMMARY', 'test', 'OK', ?)
                """, scanId, daysAgo(daysAgo));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void snapshotsAndEventsOlderThanTheRetentionAreDeletedInBatches() {
        for (int tick = 0; tick < 5; tick++) {
            departmentSnapshot(tick, 31);         // 5 old rows: three batches of 2, 2, 1
        }
        departmentSnapshot(5, 29);                // kept
        medicationSnapshot(0, 45);
        medicationSnapshot(1, 1);                 // kept
        jdbc.update("insert into system_events (created_at, level, component, message) values (?, 'INFO', 't', 'old')", daysAgo(40));
        jdbc.update("insert into system_events (created_at, level, component, message) values (?, 'INFO', 't', 'new')", daysAgo(2));

        HistoryRetentionJob.RetentionResult result = job.run();

        assertThat(result.deleted()).containsEntry("department_snapshots", 5)
                .containsEntry("medication_snapshots", 1)
                .containsEntry("system_events", 1);
        assertThat(count("select count(*) from department_snapshots where run_id = ?", runId)).isEqualTo(1);
        assertThat(count("select count(*) from medication_snapshots where run_id = ?", runId)).isEqualTo(1);
        assertThat(count("select count(*) from system_events where message = 'new'")).isEqualTo(1);
        assertThat(count("select count(*) from system_events where message = 'old'")).isZero();
    }

    @Test
    void aiDecisionsAndEvaluationsUseTheLongerEvidenceRetention() {
        long oldScan = scan(200);
        action(oldScan, 200);
        long recentScan = scan(100);               // older than 30 days, younger than 180: kept
        action(recentScan, 100);
        jdbc.update("insert into evaluation_results (created_at, scenario, run, correct, explained) values (?, 's', 1, true, true)", daysAgo(181));
        jdbc.update("insert into evaluation_results (created_at, scenario, run, correct, explained) values (?, 's', 2, true, true)", daysAgo(90));

        HistoryRetentionJob.RetentionResult result = job.run();

        assertThat(result.deleted()).containsEntry("ai_actions", 1)
                .containsEntry("ai_scans", 1)
                .containsEntry("evaluation_results", 1);
        assertThat(count("select count(*) from ai_scans where id = ?", recentScan)).isEqualTo(1);
        assertThat(count("select count(*) from ai_actions where scan_id = ?", recentScan)).isEqualTo(1);
        assertThat(count("select count(*) from evaluation_results where scenario = 's'")).isEqualTo(1);
    }

    @Test
    void anOldScanIsKeptWhileItStillHasANewerAction() {
        long scan = scan(200);
        action(scan, 10);

        HistoryRetentionJob.RetentionResult result = job.run();

        assertThat(result.deleted()).containsEntry("ai_scans", 0).containsEntry("ai_actions", 0);
        assertThat(count("select count(*) from ai_scans where id = ?", scan)).isEqualTo(1);
    }

    @Test
    void eachRunIsRecordedAsASystemEvent() {
        departmentSnapshot(0, 31);

        job.run();

        Map<String, Object> event = jdbc.queryForMap("""
                select level, message, details->'deleted'->>'department_snapshots' as deleted
                from system_events where component = 'history-retention' order by id desc limit 1
                """);
        assertThat(event).containsEntry("level", "INFO")
                .containsEntry("message", "Deleted 1 history row(s)")
                .containsEntry("deleted", "1");
    }
}
