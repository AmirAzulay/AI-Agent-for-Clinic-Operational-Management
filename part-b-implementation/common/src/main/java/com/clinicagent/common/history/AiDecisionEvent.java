package com.clinicagent.common.history;

import com.clinicagent.common.model.AgentTool;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/**
 * One document in the {@code ai-decisions} index: what the agent saw, what it decided,
 * whether the backend allowed it, and why.
 */
public record AiDecisionEvent(
        @JsonProperty("@timestamp") Instant timestamp,
        @JsonProperty("scan_id") String scanId,
        @JsonProperty("context_json") String contextJson,
        String model,
        AgentTool tool,
        Map<String, Object> parameters,
        String explanation,
        @JsonProperty("validation_result") String validationResult,
        boolean executed,
        @JsonProperty("latency_ms") long latencyMs,
        @JsonProperty("input_tokens") Integer inputTokens,
        @JsonProperty("output_tokens") Integer outputTokens
) {
}
