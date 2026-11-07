package com.clinicagent.clinic.history;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * History retention (PLAN.md §3.5).
 *
 * @param enabled       whether the daily job runs at all
 * @param cron          when it runs (Spring cron, server time zone); default 03:30 every day
 * @param days          age after which snapshots and system events are deleted
 * @param evidenceDays  age after which AI scans/actions and evaluation results are deleted
 * @param batchSize     rows deleted per statement, so no single delete holds locks for long
 */
@ConfigurationProperties(prefix = "history.retention")
public record HistoryRetentionProperties(Boolean enabled, String cron, Integer days, Integer evidenceDays,
                                         Integer batchSize) {

    public HistoryRetentionProperties {
        if (enabled == null) enabled = true;
        if (cron == null || cron.isBlank()) cron = "0 30 3 * * *";
        if (days == null) days = 30;
        if (evidenceDays == null) evidenceDays = 180;
        if (batchSize == null) batchSize = 5_000;
        if (days < 1 || evidenceDays < 1 || batchSize < 1) {
            throw new IllegalArgumentException("history.retention days, evidence-days and batch-size must be positive");
        }
    }
}
