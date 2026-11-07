package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.model.StopReason;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/simulation")
@Validated
@Tag(name = "Simulation", description = "Start, stop and watch the clinic simulation")
public class SimulationController {

    private final SimulationEngine engine;
    private final SimulationRunRepository runs;

    public SimulationController(SimulationEngine engine, SimulationRunRepository runs) {
        this.engine = engine;
        this.runs = runs;
    }

    @PostMapping("/start")
    @PreAuthorize("hasRole('CLINIC_MANAGER')")
    @Operation(summary = "Reset the clinic and start a run (clinic manager). Every field is optional")
    public SimulationStatus start(@Valid @RequestBody(required = false) StartSimulationRequest request) {
        StartSimulationRequest r = request != null ? request : new StartSimulationRequest(null, null, null, null, null, null);
        engine.start(new SimulationEngine.StartRequest(r.scenario(), r.seed(), r.tickIntervalSeconds(),
                r.minutesPerTick(), r.maxTicks(), r.autoTick()));
        return status();
    }

    @PostMapping("/stop")
    @PreAuthorize("hasRole('CLINIC_MANAGER')")
    @Operation(summary = "Stop the running simulation (clinic manager)")
    public SimulationStatus stop() {
        engine.stop();
        return status();
    }

    @PostMapping("/step")
    @PreAuthorize("hasRole('CLINIC_MANAGER')")
    @Operation(summary = "Run ticks right now (clinic manager); for testing and runs started with autoTick=false")
    public List<TickService.TickResult> step(@RequestParam(defaultValue = "1") @Min(1) @Max(1440) int ticks) {
        return engine.step(ticks);
    }

    @GetMapping("/status")
    @Operation(summary = "The latest run: running or not, simulated clock, settings")
    public SimulationStatus status() {
        return runs.findFirstByOrderByIdDesc()
                .map(run -> SimulationStatus.of(run, run.isRunning() && engine.isAutoTicking()))
                .orElse(SimulationStatus.NONE);
    }

    public record StartSimulationRequest(
            SimulationScenario scenario,
            Long seed,
            @Min(1) @Max(60) Integer tickIntervalSeconds,
            @Min(1) @Max(15) Integer minutesPerTick,
            @Min(1) @Max(10080) Integer maxTicks,
            Boolean autoTick) {
    }

    /**
     * @param simDay   1 for the first simulated day
     * @param simClock simulated time of day, HH:mm (runs start at 08:00)
     */
    public record SimulationStatus(Long runId, boolean running, boolean autoTick, String scenario, Long seed,
                                   long tick, Integer maxTicks, Integer minutesPerTick, Integer tickIntervalSeconds,
                                   int simDay, String simClock, Instant simNow, Instant startedAt, Instant stoppedAt,
                                   StopReason stopReason) {

        static final SimulationStatus NONE =
                new SimulationStatus(null, false, false, null, null, 0, null, null, null, 0, null, null, null, null, null);

        static SimulationStatus of(SimulationRun run, boolean autoTick) {
            long minutes = run.getCurrentTick() * run.getMinutesPerTick() + ClinicModel.START_HOUR * 60L;
            int day = (int) (minutes / 1440) + 1;
            String clock = "%02d:%02d".formatted((minutes % 1440) / 60, minutes % 60);
            return new SimulationStatus(run.getId(), run.isRunning(), autoTick, run.getScenarioName(), run.getSeed(),
                    run.getCurrentTick(), run.getMaxTicks(), run.getMinutesPerTick(), run.getTickIntervalSec(),
                    day, clock, run.getSimNow(), run.getStartedAt(), run.getStoppedAt(), run.getStopReason());
        }
    }
}
