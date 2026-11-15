package com.clinicagent.clinic.agent;

import java.util.Map;

/**
 * What a tool call returns to the agent. A rejected call is a normal result (executed = false)
 * with the reason, so the model can read it and choose differently.
 */
public record ToolResult(boolean executed, String result, Map<String, Object> details) {

    public static ToolResult done(String result, Map<String, Object> details) {
        return new ToolResult(true, result, details);
    }

    public static ToolResult rejected(String reason) {
        return new ToolResult(false, "Rejected: " + reason, Map.of());
    }
}
