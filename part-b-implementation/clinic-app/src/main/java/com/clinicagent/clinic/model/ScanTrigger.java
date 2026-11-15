package com.clinicagent.clinic.model;

/** What started an agent cycle: the 15-minute scheduler or a user pressing "Activate AI Agent". */
public enum ScanTrigger {
    SCHEDULED,
    MANUAL,
    /** The agent's autopilot, running until a manager stops it. */
    AUTOPILOT
}
