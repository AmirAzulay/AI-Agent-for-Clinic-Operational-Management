package com.clinicagent.clinic.model;

/** Why a simulation run ended. */
public enum StopReason {
    /** A clinic manager stopped it. */
    STOPPED,
    /** It reached its configured number of ticks. */
    COMPLETED,
    /** The application restarted while it was running; its random state was lost. */
    INTERRUPTED,
    /** A tick failed; details are in system_events. */
    ERROR
}
