package com.clinicagent.common.history;

import com.clinicagent.common.model.DepartmentStatus;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.List;

/**
 * One document in the {@code clinic-state-snapshots} index: the clinic as it looked at one
 * simulation tick. Sent by clinic-app to history-service after each tick is committed.
 */
public record ClinicStateSnapshot(
        @JsonProperty("@timestamp") Instant timestamp,
        @JsonProperty("run_id") String runId,
        long tick,
        List<DepartmentSnapshot> departments,
        List<MedicationSnapshot> medications
) {

    public record DepartmentSnapshot(
            @JsonProperty("department_id") long departmentId,
            String name,
            DepartmentStatus status,
            @JsonProperty("waiting_count") int waitingCount,
            @JsonProperty("avg_wait_min") double avgWaitMinutes,
            @JsonProperty("max_wait_min") double maxWaitMinutes,
            @JsonProperty("rooms_available") int roomsAvailable,
            @JsonProperty("rooms_occupied") int roomsOccupied,
            @JsonProperty("rooms_inactive") int roomsInactive,
            @JsonProperty("doctors_available") int doctorsAvailable
    ) {
    }

    public record MedicationSnapshot(
            @JsonProperty("medication_id") long medicationId,
            String name,
            int stock,
            int threshold
    ) {
    }
}
