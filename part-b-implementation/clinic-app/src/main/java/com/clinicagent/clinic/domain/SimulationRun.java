package com.clinicagent.clinic.domain;

import com.clinicagent.clinic.model.StopReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * One simulation run. Simulated time is tick-based: tick N happens at
 * {@code simStartedAt + N * minutesPerTick}, independent of how fast the ticks really run.
 */
@Entity
@Table(name = "simulation_runs")
public class SimulationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "stopped_at")
    private Instant stoppedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "stop_reason", length = 16)
    private StopReason stopReason;

    @Column(nullable = false)
    private long seed;

    @Column(name = "scenario_name", length = 64)
    private String scenarioName;

    @Column(name = "tick_interval_sec", nullable = false)
    private int tickIntervalSec;

    @Column(name = "minutes_per_tick", nullable = false)
    private int minutesPerTick;

    @Column(name = "max_ticks")
    private Integer maxTicks;

    @Column(name = "current_tick", nullable = false)
    private long currentTick;

    @Column(name = "sim_started_at", nullable = false)
    private Instant simStartedAt;

    @Column(name = "sim_now", nullable = false)
    private Instant simNow;

    protected SimulationRun() {
    }

    public SimulationRun(long seed, String scenarioName, int tickIntervalSec, int minutesPerTick, Integer maxTicks,
                         Instant realNow, Instant simStartedAt) {
        this.seed = seed;
        this.scenarioName = scenarioName;
        this.tickIntervalSec = tickIntervalSec;
        this.minutesPerTick = minutesPerTick;
        this.maxTicks = maxTicks;
        this.startedAt = realNow;
        this.simStartedAt = simStartedAt;
        this.simNow = simStartedAt;
    }

    /** Simulated time of tick {@code tick}. */
    public Instant simTimeOf(long tick) {
        return simStartedAt.plus(Duration.ofMinutes(tick * minutesPerTick));
    }

    /** Moves the run to its next tick and returns that tick's number. */
    public long advance() {
        currentTick++;
        simNow = simTimeOf(currentTick);
        return currentTick;
    }

    public boolean hasReachedMaxTicks() {
        return maxTicks != null && currentTick >= maxTicks;
    }

    public void stop(Instant at, StopReason reason) {
        this.stoppedAt = at;
        this.stopReason = reason;
    }

    public boolean isRunning() {
        return stoppedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getStoppedAt() {
        return stoppedAt;
    }

    public StopReason getStopReason() {
        return stopReason;
    }

    public long getSeed() {
        return seed;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public int getTickIntervalSec() {
        return tickIntervalSec;
    }

    public int getMinutesPerTick() {
        return minutesPerTick;
    }

    public Integer getMaxTicks() {
        return maxTicks;
    }

    public long getCurrentTick() {
        return currentTick;
    }

    public Instant getSimStartedAt() {
        return simStartedAt;
    }

    public Instant getSimNow() {
        return simNow;
    }
}
