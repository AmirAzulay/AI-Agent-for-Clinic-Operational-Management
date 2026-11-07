package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.model.Urgency;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.EnumMap;
import java.util.Map;

/**
 * How long simulated patients are willing to wait, per urgency (PLAN.md §3.4). A missing urgency
 * means those patients never leave. Defaults: LOW 15–30 min, MEDIUM 20–45 min, HIGH 30 min–6 h.
 */
@ConfigurationProperties(prefix = "simulation.walkouts")
public record WalkoutProperties(Map<Urgency, PatienceRange> patience) {

    public WalkoutProperties {
        if (patience == null) {
            patience = new EnumMap<>(Urgency.class);
            patience.put(Urgency.LOW, new PatienceRange(15, 30));
            patience.put(Urgency.MEDIUM, new PatienceRange(20, 45));
            patience.put(Urgency.HIGH, new PatienceRange(30, 360));
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
