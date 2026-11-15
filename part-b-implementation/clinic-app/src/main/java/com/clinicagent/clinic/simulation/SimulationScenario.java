package com.clinicagent.clinic.simulation;

/**
 * Starting conditions for a run. Each scenario resets the clinic to its initial state first,
 * then applies its own changes.
 */
public enum SimulationScenario {
    /** Normal day: the clinic copes off-peak and comes under pressure around the late-morning peak. */
    NORMAL(1.0, false, false),
    /** 1.8× arrivals all day: overloads and walkouts early and often. */
    RUSH(1.8, false, false),
    /** One doctor per department is off for the whole run. */
    STAFF_SHORTAGE(1.0, true, false),
    /** Every medication starts just above its threshold, so shortages come within hours. */
    MEDICATION_SHORTAGE(1.0, false, true);

    private final double arrivalMultiplier;
    private final boolean oneDoctorOffPerDepartment;
    private final boolean lowMedicationStock;

    SimulationScenario(double arrivalMultiplier, boolean oneDoctorOffPerDepartment, boolean lowMedicationStock) {
        this.arrivalMultiplier = arrivalMultiplier;
        this.oneDoctorOffPerDepartment = oneDoctorOffPerDepartment;
        this.lowMedicationStock = lowMedicationStock;
    }

    public double arrivalMultiplier() {
        return arrivalMultiplier;
    }

    public boolean oneDoctorOffPerDepartment() {
        return oneDoctorOffPerDepartment;
    }

    public boolean lowMedicationStock() {
        return lowMedicationStock;
    }
}
