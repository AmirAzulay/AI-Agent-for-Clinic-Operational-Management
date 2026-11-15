package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.PostgresIntegrationTest;
import com.clinicagent.clinic.model.AlertSeverity;
import com.clinicagent.clinic.model.DepartmentStatus;
import com.clinicagent.clinic.model.ScanTrigger;
import com.clinicagent.clinic.simulation.SimulationEngine;
import com.clinicagent.clinic.simulation.SimulationEngine.StartRequest;
import com.clinicagent.clinic.simulation.SimulationEngineTest;
import com.clinicagent.clinic.simulation.SimulationScenario;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/** The tools change the running simulation for real, and every call is logged under the agent run. */
class ClinicToolServiceTest extends PostgresIntegrationTest {

    @Autowired
    ClinicToolService tools;

    @Autowired
    AgentRunService agentRuns;

    @Autowired
    SimulationEngine engine;

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void cleanUp() {
        SimulationEngineTest.cleanSimulationData(engine, jdbc);
    }

    private void startRush(int ticks) {
        engine.start(new StartRequest(SimulationScenario.RUSH, 42L, null, null, null, false));
        engine.step(ticks);
    }

    private int count(String sql, Object... args) {
        return jdbc.queryForObject(sql, Integer.class, args);
    }

    private long departmentId(String name) {
        return jdbc.queryForObject("select id from departments where name = ?", Long.class, name);
    }

    @Test
    void actionsAreRejectedWhenNoSimulationIsRunning() {
        ToolResult result = tools.openRoom(departmentId("Emergency"), "testing with no simulation running");

        assertThat(result.executed()).isFalse();
        assertThat(result.result()).contains("no simulation is running");
    }

    @Test
    void anOpenedRoomIsUsedForTreatment() {
        startRush(150);  // 10:30, the morning peak
        long gm = departmentId("General Medicine");
        assertThat(jdbc.queryForObject("select status from departments where id = ?", String.class, gm)).isNotEqualTo("NORMAL");

        ToolResult result = tools.openRoom(gm, "General Medicine under pressure with an inactive room");
        engine.step(5);

        assertThat(result.executed()).isTrue();
        assertThat(count("select count(*) from treatment_rooms where department_id = ? and status = 'INACTIVE'", gm)).isZero();
        assertThat(count("select count(*) from patients where room_id = (select id from treatment_rooms where name = 'GM-4')"))
                .as("patients treated in the opened room").isPositive();
    }

    @Test
    void aRestockArrivesThirtySimulatedMinutesLater() {
        startRush(10);
        jdbc.update("update medications set stock = 5 where name = 'Adrenaline 1mg'");
        long adrenaline = jdbc.queryForObject("select id from medications where name = 'Adrenaline 1mg'", Long.class);

        ToolResult ordered = tools.requestMedicationRestock(adrenaline, 50, "Adrenaline at 5, threshold is 15");
        ToolResult again = tools.requestMedicationRestock(adrenaline, 50, "Adrenaline still low, ordering again");
        engine.step(29);
        int beforeDelivery = count("select stock from medications where id = ?", adrenaline);
        engine.step(1);

        assertThat(ordered.executed()).isTrue();
        assertThat(again.result()).contains("already on its way");
        assertThat(count("select stock from medications where id = ?", adrenaline)).isGreaterThanOrEqualTo(beforeDelivery + 40);
        assertThat(count("select count(*) from restock_requests where medication_id = ? and status = 'FULFILLED'", adrenaline)).isEqualTo(1);
    }

    @Test
    void anEscalationHoldsUntilItExpires() {
        startRush(5);   // 08:05, everything quiet
        long ortho = departmentId("Orthopedics");
        jdbc.update("update departments set status = 'NORMAL' where id = ?", ortho);

        ToolResult result = tools.escalateDepartmentStatus(ortho, DepartmentStatus.WARNING, 10, "early warning ahead of the peak");
        engine.step(5);
        String during = jdbc.queryForObject("select status from departments where id = ?", String.class, ortho);
        engine.step(10);
        Integer overrides = count("select count(*) from departments where id = ? and status_override is not null", ortho);

        assertThat(result.executed()).isTrue();
        assertThat(during).isIn("WARNING", "OVERLOADED", "CRITICAL");
        assertThat(overrides).as("escalation cleared after it expired").isZero();
    }

    @Test
    void departmentAlertsCloseWhenTheDepartmentRecovers() {
        startRush(5);
        long ped = departmentId("Pediatrics");
        tools.createAlert("OVERLOAD", AlertSeverity.WARNING, ped, "Pediatrics needs attention soon", "testing automatic alert resolution");
        jdbc.update("update departments set status = 'WARNING' where id = ?", ped);
        assertThat(count("select count(*) from alerts where active and department_id = ?", ped)).isEqualTo(1);

        jdbc.update("delete from patients where department_id = ? and status = 'WAITING'", ped);
        engine.step(1);

        assertThat(count("select count(*) from alerts where active and department_id = ?", ped)).isZero();
    }

    @Test
    void everyCallIsLoggedUnderTheOpenAgentRunWithItsReason() {
        startRush(150);
        long run = agentRuns.start("overload-response", ScanTrigger.MANUAL).getId();

        tools.getClinicState();
        tools.openRoom(departmentId("Emergency"), "Emergency overloaded with inactive ER-4");
        tools.openRoom(departmentId("Emergency"), "no");
        tools.writeSummary("Opened ER-4 in Emergency during the peak; watch Pediatrics.");
        agentRuns.finish(run, null, "test-model", 1200L, 3000, 400, null);

        assertThat(count("select count(*) from ai_actions where scan_id = ?", run)).isEqualTo(4);
        assertThat(count("select count(*) from ai_actions where scan_id = ? and executed", run)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select validation_result from ai_actions where scan_id = ? and not executed", String.class, run))
                .contains("reason");
        assertThat(jdbc.queryForObject("select summary from ai_scans where id = ?", String.class, run)).contains("ER-4");
        assertThat(jdbc.queryForObject("select model from ai_scans where id = ?", String.class, run)).isEqualTo("test-model");
    }
}
