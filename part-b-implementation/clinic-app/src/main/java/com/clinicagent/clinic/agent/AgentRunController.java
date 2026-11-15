package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.domain.AiAction;
import com.clinicagent.clinic.domain.AiActionRepository;
import com.clinicagent.clinic.domain.AiScan;
import com.clinicagent.clinic.domain.AiScanRepository;
import com.clinicagent.clinic.model.ScanTrigger;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Agent runs. The agent service opens and finishes runs (service token, /api/agent/service/**);
 * staff read the decisions (/api/agent/runs, staff JWT).
 */
@RestController
@Tag(name = "Agent", description = "AI agent runs and decisions")
public class AgentRunController {

    private final AgentRunService agentRuns;
    private final AiScanRepository scans;
    private final AiActionRepository actions;

    public AgentRunController(AgentRunService agentRuns, AiScanRepository scans, AiActionRepository actions) {
        this.agentRuns = agentRuns;
        this.scans = scans;
        this.actions = actions;
    }

    @PostMapping("/api/agent/service/runs")
    @Operation(summary = "Agent service: open a run before executing a skill (service token)")
    public RunDto start(@Valid @RequestBody StartRun request) {
        return RunDto.of(agentRuns.start(request.skill(), request.trigger() == null ? ScanTrigger.MANUAL : request.trigger()), List.of());
    }

    @PostMapping("/api/agent/service/runs/{id}/finish")
    @Operation(summary = "Agent service: close a run with its usage (service token)")
    public RunDto finish(@PathVariable long id, @RequestBody FinishRun request) {
        AiScan scan = agentRuns.finish(id, request.summary(), request.model(), request.latencyMs(),
                request.inputTokens(), request.outputTokens(), request.costUsd());
        return RunDto.of(scan, List.of());
    }

    @GetMapping("/api/agent/runs")
    @Transactional(readOnly = true)
    @Operation(summary = "The latest 20 agent runs with every tool call, newest first")
    public List<RunDto> runs() {
        List<AiScan> latest = scans.findTop20ByOrderByIdDesc();
        Map<Long, List<ActionDto>> byScan = actions.findByScanIdInOrderByIdAsc(latest.stream().map(AiScan::getId).toList())
                .stream().collect(Collectors.groupingBy(a -> a.getScan().getId(),
                        Collectors.mapping(ActionDto::of, Collectors.toList())));
        return latest.stream().map(s -> RunDto.of(s, byScan.getOrDefault(s.getId(), List.of()))).toList();
    }

    public record StartRun(@NotBlank @Size(max = 64) String skill, ScanTrigger trigger) {
    }

    public record FinishRun(String summary, String model, Long latencyMs, Integer inputTokens, Integer outputTokens,
                            BigDecimal costUsd) {
    }

    public record RunDto(long id, String skill, ScanTrigger trigger, Long simulationRunId, Instant startedAt,
                         Instant finishedAt, Instant simStartedAt, String summary, String model, Long latencyMs,
                         Integer inputTokens, Integer outputTokens, BigDecimal costUsd, List<ActionDto> actions) {
        static RunDto of(AiScan s, List<ActionDto> actions) {
            return new RunDto(s.getId(), s.getSkill(), s.getTriggerType(), s.getRunId(), s.getStartedAt(),
                    s.getFinishedAt(), s.getSimStartedAt(), s.getSummary(), s.getModel(), s.getLatencyMs(),
                    s.getInputTokens(), s.getOutputTokens(), s.getCostUsd(), actions);
        }
    }

    public record ActionDto(long id, String tool, Map<String, Object> parameters, String explanation,
                            String validationResult, boolean executed, Instant simTime) {
        static ActionDto of(AiAction a) {
            return new ActionDto(a.getId(), a.getTool().name(), a.getParameters(), a.getExplanation(),
                    a.getValidationResult(), a.isExecuted(), a.getSimTime());
        }
    }
}
