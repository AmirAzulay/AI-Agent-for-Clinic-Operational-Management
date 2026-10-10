package com.clinicagent.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

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

    @Column(nullable = false)
    private long seed;

    @Column(name = "scenario_name", length = 64)
    private String scenarioName;

    @Column(name = "tick_interval_sec", nullable = false)
    private int tickIntervalSec;

    protected SimulationRun() {
    }

    public SimulationRun(long seed, String scenarioName, int tickIntervalSec) {
        this.seed = seed;
        this.scenarioName = scenarioName;
        this.tickIntervalSec = tickIntervalSec;
    }

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void stop(Instant at) {
        this.stoppedAt = at;
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

    public long getSeed() {
        return seed;
    }

    public String getScenarioName() {
        return scenarioName;
    }

    public int getTickIntervalSec() {
        return tickIntervalSec;
    }
}
