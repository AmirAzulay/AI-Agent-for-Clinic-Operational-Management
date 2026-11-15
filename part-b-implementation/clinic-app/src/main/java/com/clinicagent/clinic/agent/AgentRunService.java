package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.domain.AiScan;
import com.clinicagent.clinic.domain.AiScanRepository;
import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.model.ScanTrigger;
import com.clinicagent.clinic.simulation.SimulationClock;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Clock;

/**
 * Agent runs (one skill execution each, stored in {@code ai_scans}). The agent service opens a run
 * before it starts and finishes it at the end; every tool call in between is logged under the open
 * run. A tool called with no open run (for example from Claude Desktop) gets a run of its own.
 */
@Service
public class AgentRunService {

    static final String DIRECT_SKILL = "direct-mcp";

    private final AiScanRepository scans;
    private final SimulationRunRepository runs;
    private final SimulationClock simulationClock;
    private final Clock clock;

    public AgentRunService(AiScanRepository scans, SimulationRunRepository runs, SimulationClock simulationClock,
                           Clock clock) {
        this.scans = scans;
        this.runs = runs;
        this.simulationClock = simulationClock;
        this.clock = clock;
    }

    @Transactional
    public synchronized AiScan start(String skill, ScanTrigger trigger) {
        // Only one agent run at a time: an unfinished one is closed first.
        scans.findFirstByFinishedAtIsNullOrderByIdDesc()
                .ifPresent(open -> open.finish(clock.instant(), open.getSummary()));
        Long runId = runs.findFirstByOrderByIdDesc().map(SimulationRun::getId).orElse(null);
        return scans.save(new AiScan(trigger, skill, runId, simulationClock.now()));
    }

    @Transactional
    public synchronized AiScan finish(long scanId, String summary, String model, Long latencyMs, Integer inputTokens,
                                      Integer outputTokens, BigDecimal costUsd) {
        AiScan scan = scans.findById(scanId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Agent run " + scanId + " not found"));
        if (summary != null && !summary.isBlank()) {
            scan.setSummary(summary);
        }
        scan.recordUsage(model, latencyMs, inputTokens, outputTokens, costUsd);
        if (!scan.isFinished()) {
            scan.finish(clock.instant(), scan.getSummary());
        }
        return scan;
    }

    /** The open run, or a new direct one. Called inside the tool's transaction. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public synchronized AiScan currentOrDirect() {
        return scans.findFirstByFinishedAtIsNullOrderByIdDesc()
                .orElseGet(() -> scans.save(new AiScan(ScanTrigger.MANUAL, DIRECT_SKILL,
                        runs.findFirstByOrderByIdDesc().map(SimulationRun::getId).orElse(null), simulationClock.now())));
    }
}
