package com.clinicagent.common.model;

/**
 * The only actions the AI agent can take (project book, section 4.3.6).
 * The backend validates every call before it executes.
 */
public enum AgentTool {
    READ_CLINIC_STATE,
    CREATE_ALERT,
    SET_DEPARTMENT_STATUS,
    OPEN_ROOM,
    REQUEST_MEDICATION_RESTOCK,
    GENERATE_SUMMARY
}
