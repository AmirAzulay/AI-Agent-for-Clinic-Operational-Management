package com.clinicagent.clinic.model;

/** Where a simulated patient is in the clinic flow. */
public enum PatientStatus {
    WAITING,
    IN_TREATMENT,
    DONE,
    /** Left the queue before treatment because the wait exceeded their patience (PLAN.md §3.4). */
    LEFT
}
