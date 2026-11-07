package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.model.Urgency;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * How long simulated patients are willing to wait, per urgency (PLAN.md §3.4). A missing urgency
 * means those patients never leave. Defaults: LOW 45–90 min, MEDIUM 60–120 min, HIGH never.
 */
@ConfigurationProperties(prefix = "simulation.walkouts")
public record WalkoutProperties(Map<Urgency, PatienceRange> patience) {

    public WalkoutProperties {
        if (patience == null) {
            patience = new EnumMap<>(Urgency.class);
            patience.put(Urgency.LOW, new PatienceRange(45, 90));
            patience.put(Urgency.MEDIUM, new PatienceRange(60, 120));
        }
        patience = Map.copyOf(patience);
    }

    /** Inclusive range of patience in minutes. */
    public record PatienceRange(int minMinutes, int maxMinutes) {
        public PatienceRange {
            if (minMinutes <= 0 || maxMinutes < minMinutes) {
                throw new IllegalArgumentException(
                        "patience range must satisfy 0 < min <= max, got " + minMinutes + ".." + maxMinutes);
            }
        }
    }
}
