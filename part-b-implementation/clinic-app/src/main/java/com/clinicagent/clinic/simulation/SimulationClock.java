package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;

/**
 * The clinic's "now". Patient times are simulated, so waiting times must be measured against the
 * latest run's simulated clock (frozen when the run stops). Before any run exists, real time.
 */
@Component
public class SimulationClock {

    private final SimulationRunRepository runs;
    private final Clock clock;

    public SimulationClock(SimulationRunRepository runs, Clock clock) {
        this.runs = runs;
        this.clock = clock;
    }

    public Instant now() {
        return runs.findFirstByOrderByIdDesc().map(SimulationRun::getSimNow).orElseGet(clock::instant);
    }
}
