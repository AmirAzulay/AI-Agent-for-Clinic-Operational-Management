package com.clinicagent.common.model;

/** What started an agent cycle: the 15-minute scheduler or a user pressing "Activate AI Agent". */
public enum ScanTrigger {
    SCHEDULED,
    MANUAL
}
