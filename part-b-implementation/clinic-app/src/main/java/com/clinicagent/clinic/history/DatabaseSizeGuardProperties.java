package com.clinicagent.clinic.history;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Keeps the database under a size budget (PLAN.md §3.6). Defaults fit the Supabase free plan,
 * which allows 500 MB.
 *
 * @param enabled       whether the guard runs
 * @param softLimitMb   above this, the oldest history is trimmed until the database is below it again
 * @param hardLimitMb   above this, history writes are paused until trimming brings it below the soft limit
 * @param checkInterval how often the size is checked
 */
@ConfigurationProperties(prefix = "history.size-guard")
public record DatabaseSizeGuardProperties(Boolean enabled, Long softLimitMb, Long hardLimitMb, Duration checkInterval) {

    public DatabaseSizeGuardProperties {
        if (enabled == null) enabled = true;
        if (softLimitMb == null) softLimitMb = 300L;
        if (hardLimitMb == null) hardLimitMb = 400L;
        if (checkInterval == null) checkInterval = Duration.ofMinutes(10);
        if (softLimitMb < 1 || hardLimitMb < softLimitMb) {
            throw new IllegalArgumentException("history.size-guard requires 0 < soft-limit-mb <= hard-limit-mb");
        }
    }

    public long softLimitBytes() {
        return softLimitMb * 1024 * 1024;
    }

    public long hardLimitBytes() {
        return hardLimitMb * 1024 * 1024;
    }
}
