package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.model.StopReason;
import com.clinicagent.clinic.simulation.SimulationEngine.StartRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The engine against a real PostgreSQL, with manual ticks so runs are fast and deterministic. */
class SimulationEngineTest extends PostgresIntegrationTest {

    @Autowired
    SimulationEngine engine;

    @Autowired
    SimulationRunRepository runs;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        cleanSimulationData(engine, jdbc);
    }

    /** Stops any run and removes everything simulations wrote, so other test classes see the seeded clinic. */
    static void cleanSimulationData(SimulationEngine engine, JdbcTemplate jdbc) {
        try {
            engine.stop();
        } catch (ResponseStatusException notRunning) {
            // nothing running
        }
        jdbc.update("delete from department_snapshots");
        jdbc.update("delete from medication_snapshots");
        jdbc.update("delete from patients");
        jdbc.update("delete from simulation_runs");
        jdbc.update("update treatment_rooms set status = initial_status");
        jdbc.update("update doctors set available = initially_available");
        jdbc.update("update medications set stock = initial_stock");
        jdbc.update("update departments set status = 'NORMAL'");
    }

    private SimulationRun start(SimulationScenario scenario, long seed) {
        return engine.start(new StartRequest(scenario, seed, null, null, null, false));
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    @Test
    void theSameSeedProducesTheSameRun() {
        String query = """
                select string_agg(concat_ws(',', tick, department_id, status, waiting_count, avg_wait_min,
                                            max_wait_min, rooms_available, left_count), ';' order by tick, department_id)
                from department_snapshots where run_id = ?
                """;
        long first = start(SimulationScenario.NORMAL, 7).getId();
        engine.step(300);
        engine.stop();
        long second = start(SimulationScenario.NORMAL, 7).getId();
        engine.step(300);

        String a = jdbc.queryForObject(query, String.class, first);
        String b = jdbc.queryForObject(query, String.class, second);
        assertThat(a).isNotBlank().isEqualTo(b);
    }

    @Test
    void aNormalDayProducesOverloadsWalkoutsRoomShortagesAndLowStockOnItsOwn() {
        long run = start(SimulationScenario.NORMAL, 42).getId();
        engine.step(1440);

        assertThat(count("select count(*) from department_snapshots where run_id = ? and status <> 'NORMAL'", run))
                .as("snapshots with a department under pressure").isPositive();
        assertThat(count("select count(*) from department_snapshots where run_id = ? and status in ('OVERLOADED', 'CRITICAL')", run))
                .as("overloaded or critical snapshots").isPositive();
        assertThat(count("select count(*) from department_snapshots where run_id = ? and rooms_available = 0", run))
                .as("snapshots with every room taken").isPositive();
        assertThat(count("select count(*) from patients where run_id = ? and status = 'LEFT'", run))
                .as("walkouts").isPositive();
        assertThat(count("select count(*) from medication_snapshots where run_id = ? and stock < threshold", run))
                .as("medication below threshold").isPositive();
    }

    @Test
    void aRunStopsByItselfAfterMaxTicksWithASnapshotEveryFiveMinutes() {
        long run = engine.start(new StartRequest(SimulationScenario.NORMAL, 3L, null, null, 60, false)).getId();

        List<TickService.TickResult> results = engine.step(100);

        assertThat(results).hasSize(60);
        assertThat(results.getLast().runCompleted()).isTrue();
        SimulationRun finished = runs.findById(run).orElseThrow();
        assertThat(finished.isRunning()).isFalse();
        assertThat(finished.getStopReason()).isEqualTo(StopReason.COMPLETED);
        assertThat(finished.getCurrentTick()).isEqualTo(60);
        assertThat(count("select count(distinct tick) from department_snapshots where run_id = ?", run)).isEqualTo(12);
    }

    @Test
    void startingAnotherRunResetsTheClinic() {
        start(SimulationScenario.RUSH, 1);
        engine.step(120);
        engine.stop();
        assertThat(count("select count(*) from patients")).isPositive();

        long second = start(SimulationScenario.NORMAL, 2).getId();

        assertThat(count("select count(*) from patients")).isZero();
        assertThat(count("select count(*) from treatment_rooms where status <> initial_status")).isZero();
        assertThat(count("select count(*) from medications where stock <> initial_stock")).isZero();
        assertThat(count("select count(*) from departments where status <> 'NORMAL'")).isZero();
        assertThat(runs.findById(second).orElseThrow().getCurrentTick()).isZero();
    }

    @Test
    void scenariosChangeTheStartingConditions() {
        start(SimulationScenario.STAFF_SHORTAGE, 1);
        assertThat(count("select count(*) from doctors where not available")).isEqualTo(4);
        engine.stop();

        start(SimulationScenario.MEDICATION_SHORTAGE, 1);
        assertThat(count("select count(*) from medications where stock <> threshold + 5")).isZero();
    }

    @Test
    void onlyOneRunAtATimeAndStoppingNeedsARunningOne() {
        start(SimulationScenario.NORMAL, 1);
        assertThatThrownBy(() -> start(SimulationScenario.NORMAL, 2))
                .isInstanceOf(ResponseStatusException.class).hasMessageContaining("already running");

        engine.stop();
        assertThatThrownBy(engine::stop).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> engine.step(1)).isInstanceOf(ResponseStatusException.class);
    }
}
