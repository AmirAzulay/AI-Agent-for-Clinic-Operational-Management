package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.Medication;
import com.clinicagent.clinic.model.DepartmentStatus;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** Every validation rule from PLAN.md §4.1, without a database. */
class ToolValidatorTest {

    private static Department department(DepartmentStatus status) {
        Department d = new Department("Pediatrics", 15, 5, 10);
        d.setStatus(status);
        return d;
    }

    @Test
    void everyActionNeedsAReason() {
        assertThat(ToolValidator.reason(null)).isPresent();
        assertThat(ToolValidator.reason("too short")).isPresent();
        assertThat(ToolValidator.reason("queue of 9 and no free room")).isEmpty();
    }

    @Test
    void actionsNeedARunningSimulation() {
        assertThat(ToolValidator.simulationRunning(false)).contains("no simulation is running");
        assertThat(ToolValidator.simulationRunning(true)).isEmpty();
    }

    @Test
    void aRoomOpensOnlyUnderPressureAndOnlyIfOneIsInactive() {
        assertThat(ToolValidator.openRoom(null, 1)).contains("unknown department");
        assertThat(ToolValidator.openRoom(department(DepartmentStatus.OVERLOADED), 0)).get().asString().contains("no inactive room");
        assertThat(ToolValidator.openRoom(department(DepartmentStatus.NORMAL), 1)).get().asString().contains("NORMAL");
        assertThat(ToolValidator.openRoom(department(DepartmentStatus.WARNING), 1)).isEmpty();
    }

    @Test
    void alertsNeedAKnownDepartmentARealMessageAndNoDuplicate() {
        assertThat(ToolValidator.createAlert(true, null, "Queue is far too long", false)).contains("unknown department");
        assertThat(ToolValidator.createAlert(false, null, "short", false)).isPresent();
        assertThat(ToolValidator.createAlert(false, null, "Clinic-wide staffing problem", true)).get().asString().contains("already open");
        assertThat(ToolValidator.createAlert(false, null, "Clinic-wide staffing problem", false)).isEmpty();
    }

    @Test
    void escalationGoesUpOneLevelAtATimeForALimitedTime() {
        Department warning = department(DepartmentStatus.WARNING);
        assertThat(ToolValidator.escalate(warning, DepartmentStatus.OVERLOADED, 30)).isEmpty();
        assertThat(ToolValidator.escalate(warning, DepartmentStatus.CRITICAL, 30)).get().asString().contains("one level at a time");
        assertThat(ToolValidator.escalate(warning, DepartmentStatus.WARNING, 30)).get().asString().contains("must raise");
        assertThat(ToolValidator.escalate(warning, DepartmentStatus.NORMAL, 30)).isPresent();
        assertThat(ToolValidator.escalate(warning, DepartmentStatus.OVERLOADED, 0)).isPresent();
        assertThat(ToolValidator.escalate(warning, DepartmentStatus.OVERLOADED, 121)).isPresent();
        assertThat(ToolValidator.escalate(department(DepartmentStatus.NORMAL), DepartmentStatus.WARNING, 10)).isEmpty();
    }

    @Test
    void restockOnlyBelowThresholdWithASensibleQuantityAndNoneOnTheWay() {
        Medication low = new Medication("Adrenaline 1mg", 40, 15);
        low.setStock(9);
        Medication fine = new Medication("Paracetamol 500mg", 400, 100);

        assertThat(ToolValidator.restock(null, 10, false)).contains("unknown medication");
        assertThat(ToolValidator.restock(fine, 10, false)).get().asString().contains("not below its threshold");
        assertThat(ToolValidator.restock(low, 0, false)).isPresent();
        assertThat(ToolValidator.restock(low, 81, false)).get().asString().contains("between 1 and 80");
        assertThat(ToolValidator.restock(low, 30, true)).get().asString().contains("already on its way");
        assertThat(ToolValidator.restock(low, 30, false)).isEmpty();
    }

    @Test
    void summariesMustSaySomething() {
        assertThat(ToolValidator.summary("ok")).isPresent();
        assertThat(ToolValidator.summary("Opened GM-4; restocked saline; watch Pediatrics.")).isEmpty();
    }
}
