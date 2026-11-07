package com.clinicagent.clinic.simulation;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Defaults for new simulation runs; a start request can override the per-run values.
 *
 * @param tickInterval         real time between ticks (default 5 s)
 * @param minutesPerTick       simulated minutes per tick (default 1, so 12× real time at 5 s)
 * @param maxTicks             a run stops by itself after this many ticks (default 1440 = one simulated day)
 * @param snapshotEveryMinutes simulated minutes between history snapshots (default 5; PLAN.md §3.6)
 */
@ConfigurationProperties(prefix = "simulation")
public record SimulationProperties(Duration tickInterval, Integer minutesPerTick, Integer maxTicks,
                                   Integer snapshotEveryMinutes) {

    public SimulationProperties {
        if (tickInterval == null) tickInterval = Duration.ofSeconds(5);
        if (minutesPerTick == null) minutesPerTick = 1;
        if (maxTicks == null) maxTicks = 1440;
        if (snapshotEveryMinutes == null) snapshotEveryMinutes = 5;
    }
}
