package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.Medication;
import com.clinicagent.clinic.model.DepartmentStatus;

import java.util.Optional;

/**
 * Every rule an agent action must pass (PLAN.md §4.1), as plain functions of the current state so
 * each rule is unit-tested. Empty means allowed; otherwise the reason it is rejected. The model
 * proposes, this class decides.
 */
public final class ToolValidator {

    public static final int MIN_REASON_LENGTH = 10;
    public static final int MAX_ESCALATION_MINUTES = 120;

    private ToolValidator() {
    }

    /** Every action must say why: the book's "every action has an explanation". */
    public static Optional<String> reason(String reason) {
        if (reason == null || reason.strip().length() < MIN_REASON_LENGTH) {
            return Optional.of("give a reason of at least " + MIN_REASON_LENGTH + " characters for this action");
        }
        return Optional.empty();
    }

    public static Optional<String> simulationRunning(boolean running) {
        return running ? Optional.empty() : Optional.of("no simulation is running");
    }

    /** open_room: the department has an inactive room and is at least WARNING. */
    public static Optional<String> openRoom(Department department, long inactiveRooms) {
        if (department == null) return Optional.of("unknown department");
        if (inactiveRooms == 0) return Optional.of(department.getName() + " has no inactive room to open");
        if (department.getStatus() == DepartmentStatus.NORMAL) {
            return Optional.of(department.getName() + " is NORMAL; rooms are only opened under pressure (WARNING or worse)");
        }
        return Optional.empty();
    }

    /** create_alert: a known department (or clinic-wide), a real message, and no identical alert already open. */
    public static Optional<String> createAlert(boolean departmentGiven, Department department, String message,
                                               boolean identicalAlertOpen) {
        if (departmentGiven && department == null) return Optional.of("unknown department");
        if (message == null || message.strip().length() < 10 || message.length() > 500) {
            return Optional.of("the alert message must be 10–500 characters");
        }
        if (identicalAlertOpen) return Optional.of("an alert of this type is already open for this department");
        return Optional.empty();
    }

    /**
     * escalate_department_status: only upward, at most one level above the current status (so the
     * escalation stays justified by the metrics), for 1–120 simulated minutes.
     */
    public static Optional<String> escalate(Department department, DepartmentStatus requested, int minutes) {
        if (department == null) return Optional.of("unknown department");
        if (requested == null || requested == DepartmentStatus.NORMAL) {
            return Optional.of("status must be WARNING, OVERLOADED or CRITICAL");
        }
        if (minutes < 1 || minutes > MAX_ESCALATION_MINUTES) {
            return Optional.of("minutes must be between 1 and " + MAX_ESCALATION_MINUTES);
        }
        DepartmentStatus current = department.getStatus();
        if (requested.ordinal() <= current.ordinal()) {
            return Optional.of(department.getName() + " is already " + current + "; escalation must raise the status");
        }
        if (requested.ordinal() > current.ordinal() + 1) {
            return Optional.of("escalate one level at a time: " + department.getName() + " is " + current
                    + ", so the most it can become now is " + DepartmentStatus.values()[current.ordinal() + 1]);
        }
        return Optional.empty();
    }

    /** request_medication_restock: stock below threshold, a sensible quantity, and no open request already. */
    public static Optional<String> restock(Medication medication, int quantity, boolean openRequestExists) {
        if (medication == null) return Optional.of("unknown medication");
        if (!medication.isBelowThreshold()) {
            return Optional.of(medication.getName() + " stock (" + medication.getStock() + ") is not below its threshold ("
                    + medication.getThreshold() + ")");
        }
        int max = Math.max(1, medication.getInitialStock() * 2);
        if (quantity < 1 || quantity > max) return Optional.of("quantity must be between 1 and " + max);
        if (openRequestExists) return Optional.of("a restock of " + medication.getName() + " is already on its way");
        return Optional.empty();
    }

    /** write_summary: a real summary, not a placeholder. */
    public static Optional<String> summary(String text) {
        if (text == null || text.strip().length() < 20 || text.length() > 4000) {
            return Optional.of("the summary must be 20–4000 characters");
        }
        return Optional.empty();
    }
}
