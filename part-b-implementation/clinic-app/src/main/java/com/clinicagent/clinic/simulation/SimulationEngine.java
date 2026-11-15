package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.model.StopReason;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Starts, stops and drives simulation runs. One run at a time. Ticks run on the scheduler at the
 * run's tick interval ({@code autoTick}), or on demand through {@link #step} (tests and the
 * evaluation harness). The run's random generator lives in memory, so a run cannot survive an
 * application restart: on startup any run still marked running is closed as INTERRUPTED.
 */
@Service
public class SimulationEngine {

    private static final Logger log = LoggerFactory.getLogger(SimulationEngine.class);

    private final SimulationRunRepository runs;
    private final ClinicResetService reset;
    private final TickService ticks;
    private final TaskScheduler scheduler;
    private final TransactionTemplate tx;
    private final JdbcTemplate jdbc;
    private final SimulationProperties properties;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private Long activeRunId;
    private SimulationScenario activeScenario;
    private Random random;
    private ScheduledFuture<?> scheduled;

    public SimulationEngine(SimulationRunRepository runs, ClinicResetService reset, TickService ticks,
                            TaskScheduler scheduler, TransactionTemplate tx, JdbcTemplate jdbc,
                            SimulationProperties properties, Clock clock) {
        this.runs = runs;
        this.reset = reset;
        this.ticks = ticks;
        this.scheduler = scheduler;
        this.tx = tx;
        this.jdbc = jdbc;
        this.properties = properties;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void closeInterruptedRuns() {
        tx.executeWithoutResult(status -> runs.findByStoppedAtIsNull().forEach(run -> {
            run.stop(clock.instant(), StopReason.INTERRUPTED);
            log.warn("Simulation run {} was still running at startup; marked INTERRUPTED", run.getId());
        }));
    }

    /** Resets the clinic and starts a new run. 409 if one is already running. */
    public SimulationRun start(StartRequest request) {
        lock.lock();
        try {
            if (activeRunId != null || !runs.findByStoppedAtIsNull().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "A simulation is already running; stop it first");
            }
            SimulationScenario scenario = request.scenario() != null ? request.scenario() : SimulationScenario.NORMAL;
            long seed = request.seed() != null ? request.seed() : new Random().nextLong();
            int interval = request.tickIntervalSeconds() != null
                    ? request.tickIntervalSeconds() : (int) properties.tickInterval().toSeconds();
            int minutesPerTick = request.minutesPerTick() != null ? request.minutesPerTick() : properties.minutesPerTick();
            int maxTicks = request.maxTicks() != null ? request.maxTicks() : properties.maxTicks();
            boolean autoTick = request.autoTick() == null || request.autoTick();

            SimulationRun run = tx.execute(status -> {
                reset.reset(scenario);
                Instant now = clock.instant();
                return runs.save(new SimulationRun(seed, scenario.name(), interval, minutesPerTick, maxTicks, now, now));
            });
            activeRunId = run.getId();
            activeScenario = scenario;
            random = new Random(seed);
            if (autoTick) {
                scheduled = scheduler.scheduleAtFixedRate(this::scheduledTick, Duration.ofSeconds(interval));
            }
            log.info("Simulation run {} started: scenario {}, seed {}, {} s per tick, {} min per tick, max {} ticks{}",
                    run.getId(), scenario, seed, interval, minutesPerTick, maxTicks, autoTick ? "" : ", manual ticks");
            return run;
        } finally {
            lock.unlock();
        }
    }

    /** Stops the running run. 409 if none is running. */
    public SimulationRun stop() {
        lock.lock();
        try {
            if (activeRunId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No simulation is running");
            }
            return finish(StopReason.STOPPED);
        } finally {
            lock.unlock();
        }
    }

    /** Runs {@code count} ticks right now. 409 if no run is active. */
    public List<TickService.TickResult> step(int count) {
        lock.lock();
        try {
            if (activeRunId == null) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "No simulation is running");
            }
            List<TickService.TickResult> results = new ArrayList<>();
            for (int i = 0; i < count && activeRunId != null; i++) {
                results.add(tickOnce());
            }
            return results;
        } finally {
            lock.unlock();
        }
    }

    public boolean isAutoTicking() {
        return scheduled != null;
    }

    private void scheduledTick() {
        lock.lock();
        try {
            if (activeRunId != null) {
                tickOnce();
            }
        } catch (RuntimeException e) {
            log.error("Simulation tick failed; stopping run {}", activeRunId, e);
            recordFailure(e);
            finish(StopReason.ERROR);
        } finally {
            lock.unlock();
        }
    }

    private TickService.TickResult tickOnce() {
        TickService.TickResult result = ticks.tick(activeRunId, activeScenario, random);
        if (result.runCompleted()) {
            log.info("Simulation run {} completed after {} ticks", activeRunId, result.tick());
            clearActive();
        }
        return result;
    }

    private SimulationRun finish(StopReason reason) {
        Long runId = activeRunId;
        clearActive();
        SimulationRun run = tx.execute(status -> {
            SimulationRun r = runs.findById(runId).orElseThrow();
            if (r.isRunning()) r.stop(clock.instant(), reason);
            return r;
        });
        log.info("Simulation run {} stopped ({})", runId, reason);
        return run;
    }

    private void clearActive() {
        if (scheduled != null) {
            scheduled.cancel(false);
            scheduled = null;
        }
        activeRunId = null;
        activeScenario = null;
        random = null;
    }

    private void recordFailure(RuntimeException e) {
        try {
            jdbc.update("""
                    insert into system_events (level, component, message, details)
                    values ('ERROR', 'simulation', ?, jsonb_build_object('run_id', ?, 'error', ?))
                    """, "Simulation tick failed; run stopped", activeRunId, String.valueOf(e));
        } catch (RuntimeException ignored) {
            // the database itself may be the problem; the log has the details
        }
    }

    /** Every field is optional; null means the configured default. */
    public record StartRequest(SimulationScenario scenario, Long seed, Integer tickIntervalSeconds,
                               Integer minutesPerTick, Integer maxTicks, Boolean autoTick) {
    }
}
