package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.model.DepartmentStatus;
import com.clinicagent.clinic.model.Urgency;

import java.time.Duration;
import java.util.Map;
import java.util.random.RandomGenerator;

/**
 * The simulation's assumptions in one place, so they are easy to tune and to describe in the
 * book. Operational only: no medical logic. All randomness comes from the run's seeded generator.
 *
 * <p>Tuning: off-peak, arrivals stay below what each department can treat; around the late
 * morning peak they exceed it, so queues, overloads and walkouts appear on their own and recede
 * in the evening. Medication use depletes stock within the first simulated day.
 */
public final class ClinicModel {

    private ClinicModel() {
    }

    /** Simulated clock time a run starts at. */
    public static final int START_HOUR = 8;

    /** Base arrivals per simulated minute, by department name. Unknown departments use the default. */
    static final Map<String, Double> ARRIVALS_PER_MINUTE = Map.of(
            "General Medicine", 0.10,
            "Pediatrics", 0.07,
            "Orthopedics", 0.065,
            "Emergency", 0.10);
    static final double DEFAULT_ARRIVALS_PER_MINUTE = 0.07;

    /** Arrival load by hour of day: 1.75× at the 11:00 peak, 0.25× at 23:00. */
    public static double hourlyLoad(double hourOfDay) {
        return 1.0 + 0.75 * Math.cos(2 * Math.PI * (hourOfDay - 11) / 24);
    }

    public static double hourOfDay(long simMinutesSinceStart) {
        return (START_HOUR + simMinutesSinceStart / 60.0) % 24;
    }

    /** Expected arrivals in one tick for a department. */
    public static double expectedArrivals(String department, double scenarioMultiplier, long simMinutesSinceStart,
                                          int minutesPerTick) {
        double base = ARRIVALS_PER_MINUTE.getOrDefault(department, DEFAULT_ARRIVALS_PER_MINUTE);
        return base * scenarioMultiplier * hourlyLoad(hourOfDay(simMinutesSinceStart)) * minutesPerTick;
    }

    /** Poisson-distributed count with the given mean (Knuth's method; means here are small). */
    public static int poisson(double mean, RandomGenerator random) {
        double limit = Math.exp(-mean);
        double product = random.nextDouble();
        int count = 0;
        while (product > limit) {
            count++;
            product *= random.nextDouble();
        }
        return count;
    }

    /** Urgency mix: 50 % LOW, 35 % MEDIUM, 15 % HIGH. */
    public static Urgency urgency(RandomGenerator random) {
        double r = random.nextDouble();
        return r < 0.50 ? Urgency.LOW : r < 0.85 ? Urgency.MEDIUM : Urgency.HIGH;
    }

    /** Treatment length: LOW 10–20 min, MEDIUM 15–30 min, HIGH 25–50 min. */
    public static Duration treatmentDuration(Urgency urgency, RandomGenerator random) {
        int minutes = switch (urgency) {
            case LOW -> random.nextInt(10, 21);
            case MEDIUM -> random.nextInt(15, 31);
            case HIGH -> random.nextInt(25, 51);
        };
        return Duration.ofMinutes(minutes);
    }

    /** Relative use of each medication by name; heavier for common drugs. */
    static final Map<String, Double> MEDICATION_WEIGHTS = Map.of(
            "Paracetamol 500mg", 0.30,
            "Ibuprofen 400mg", 0.22,
            "Amoxicillin 500mg", 0.12,
            "Saline 0.9% 500ml", 0.12,
            "Adrenaline 1mg", 0.04,
            "Insulin (rapid)", 0.06,
            "Salbutamol inhaler", 0.07,
            "Ondansetron 4mg", 0.07);
    static final double DEFAULT_MEDICATION_WEIGHT = 0.05;

    /** 85 % of treatments use medication: 1–2 different drugs, 1–3 units each. */
    public static int medicationsUsed(RandomGenerator random) {
        return random.nextDouble() < 0.85 ? random.nextInt(1, 3) : 0;
    }

    public static int unitsUsed(RandomGenerator random) {
        return random.nextInt(1, 4);
    }

    public static double medicationWeight(String name) {
        return MEDICATION_WEIGHTS.getOrDefault(name, DEFAULT_MEDICATION_WEIGHT);
    }

    /** Longest wait (minutes) that makes a department CRITICAL whatever its queue. */
    public static final double CRITICAL_WAIT_MIN = 45;
    /** With every room taken, longest wait that makes a department OVERLOADED. */
    public static final double OVERLOADED_WAIT_MIN = 25;
    /** With every room taken, longest wait that makes a department WARNING. */
    public static final double WARNING_WAIT_MIN = 15;

    /**
     * Department status from its queue and from room pressure. Patients give up after 15–30
     * minutes, so a short queue can still be a crisis: the pressure rules catch full rooms with
     * long waits even when the queue never reaches its thresholds.
     * <ul>
     *   <li>CRITICAL: queue at max, or someone has waited {@value #CRITICAL_WAIT_MIN} min;</li>
     *   <li>OVERLOADED: queue at the overload threshold, or no free room and the longest wait
     *       is {@value #OVERLOADED_WAIT_MIN} min;</li>
     *   <li>WARNING: queue at the warning threshold, or no free room and the longest wait is
     *       {@value #WARNING_WAIT_MIN} min;</li>
     *   <li>otherwise NORMAL.</li>
     * </ul>
     */
    public static DepartmentStatus statusFor(int waiting, double maxWaitMinutes, boolean freeRoom,
                                             int warningThreshold, int overloadThreshold, int maxQueue) {
        if (waiting >= maxQueue || maxWaitMinutes >= CRITICAL_WAIT_MIN) return DepartmentStatus.CRITICAL;
        if (waiting >= overloadThreshold || (!freeRoom && maxWaitMinutes >= OVERLOADED_WAIT_MIN)) {
            return DepartmentStatus.OVERLOADED;
        }
        if (waiting >= warningThreshold || (!freeRoom && maxWaitMinutes >= WARNING_WAIT_MIN)) {
            return DepartmentStatus.WARNING;
        }
        return DepartmentStatus.NORMAL;
    }
}
