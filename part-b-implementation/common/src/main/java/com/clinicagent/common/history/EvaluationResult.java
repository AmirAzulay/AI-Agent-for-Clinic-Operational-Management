package com.clinicagent.common.history;

import com.clinicagent.common.model.AgentTool;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * One document in the {@code evaluation-results} index: the outcome of one scenario run,
 * used to compute the four success metrics from project book section 8.
 */
public record EvaluationResult(
        @JsonProperty("@timestamp") Instant timestamp,
        String scenario,
        int run,
        @JsonProperty("expected_issues") List<String> expectedIssues,
        @JsonProperty("detected_issues") List<String> detectedIssues,
        @JsonProperty("expected_action") AgentTool expectedAction,
        @JsonProperty("actual_action") AgentTool actualAction,
        boolean correct,
        boolean explained
) {
}
