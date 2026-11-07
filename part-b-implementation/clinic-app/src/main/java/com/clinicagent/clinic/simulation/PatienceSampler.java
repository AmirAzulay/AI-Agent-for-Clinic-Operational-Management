package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.model.Urgency;
import org.springframework.stereotype.Component;

import java.util.random.RandomGenerator;

/**
 * Draws a patience for a newly arrived patient. The random generator is passed in, so the
 * simulation's seeded generator makes every run with the same seed reproducible.
 */
@Component
public class PatienceSampler {

    private final WalkoutProperties properties;

    public PatienceSampler(WalkoutProperties properties) {
        this.properties = properties;
    }

    /** Patience in minutes, or null if patients of this urgency never leave. */
    public Integer sample(Urgency urgency, RandomGenerator random) {
        WalkoutProperties.PatienceRange range = properties.patience().get(urgency);
        if (range == null) {
            return null;
        }
        return random.nextInt(range.minMinutes(), range.maxMinutes() + 1);
    }
}
