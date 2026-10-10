package com.clinicagent.common.history;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Map;

/** One document in the {@code system-events} index: audit and error log for evaluation and debugging. */
public record SystemEvent(
        @JsonProperty("@timestamp") Instant timestamp,
        String level,
        String component,
        String message,
        Map<String, Object> details
) {
}
