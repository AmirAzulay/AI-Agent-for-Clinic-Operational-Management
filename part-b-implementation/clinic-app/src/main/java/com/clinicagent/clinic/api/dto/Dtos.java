package com.clinicagent.clinic.api.dto;

import com.clinicagent.clinic.domain.Alert;
import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.Doctor;
import com.clinicagent.clinic.domain.Medication;
import com.clinicagent.clinic.domain.Patient;
import com.clinicagent.clinic.domain.TreatmentRoom;
import com.clinicagent.common.model.Actor;
import com.clinicagent.common.model.AlertSeverity;
import com.clinicagent.common.model.DepartmentStatus;
import com.clinicagent.common.model.PatientStatus;
import com.clinicagent.common.model.RoomStatus;
import com.clinicagent.common.model.Urgency;

import java.time.Instant;
import java.util.List;

/** Read models returned by the REST API. Entities never leave the service layer. */
public final class Dtos {

    private Dtos() {
    }

    public record DepartmentDto(Long id, String name, DepartmentStatus status, int maxQueue,
                                int warningThreshold, int overloadThreshold) {
        public static DepartmentDto from(Department d) {
            return new DepartmentDto(d.getId(), d.getName(), d.getStatus(), d.getMaxQueue(),
                    d.getWarningThreshold(), d.getOverloadThreshold());
        }
    }

    public record RoomDto(Long id, Long departmentId, String name, RoomStatus status) {
        public static RoomDto from(TreatmentRoom r) {
            return new RoomDto(r.getId(), r.getDepartment().getId(), r.getName(), r.getStatus());
        }
    }

    public record DoctorDto(Long id, Long departmentId, String name, boolean available, int maxConcurrentPatients) {
        public static DoctorDto from(Doctor d) {
            return new DoctorDto(d.getId(), d.getDepartment().getId(), d.getName(), d.isAvailable(),
                    d.getMaxConcurrentPatients());
        }
    }

    public record PatientDto(Long id, Long departmentId, Urgency urgency, PatientStatus status, Instant arrivedAt,
                             double waitingMinutes, Instant treatmentStartedAt, Instant treatmentEndedAt,
                             Long roomId, Long doctorId) {
        public static PatientDto from(Patient p, Instant now) {
            return new PatientDto(p.getId(), p.getDepartment().getId(), p.getUrgency(), p.getStatus(),
                    p.getArrivedAt(), p.waitingMinutes(now), p.getTreatmentStartedAt(), p.getTreatmentEndedAt(),
                    p.getRoom() != null ? p.getRoom().getId() : null,
                    p.getDoctor() != null ? p.getDoctor().getId() : null);
        }
    }

    public record MedicationDto(Long id, String name, int stock, int threshold, boolean belowThreshold) {
        public static MedicationDto from(Medication m) {
            return new MedicationDto(m.getId(), m.getName(), m.getStock(), m.getThreshold(), m.isBelowThreshold());
        }
    }

    public record AlertDto(Long id, String type, AlertSeverity severity, Long departmentId, String message,
                           boolean active, Actor createdBy, Instant createdAt, Instant resolvedAt) {
        public static AlertDto from(Alert a) {
            return new AlertDto(a.getId(), a.getType(), a.getSeverity(),
                    a.getDepartment() != null ? a.getDepartment().getId() : null,
                    a.getMessage(), a.isActive(), a.getCreatedBy(), a.getCreatedAt(), a.getResolvedAt());
        }
    }

    /** Everything the dashboard shows for one department, computed from the current state. */
    public record DepartmentState(Long id, String name, DepartmentStatus status, int maxQueue,
                                  int waitingCount, int inTreatmentCount,
                                  double avgWaitMinutes, double maxWaitMinutes,
                                  int roomsAvailable, int roomsOccupied, int roomsInactive, int roomsMaintenance,
                                  int doctorsAvailable, int doctorsTotal,
                                  List<RoomDto> rooms, List<DoctorDto> doctors) {
    }

    /** The full dashboard payload (project book FR: queues, department statuses, rooms, doctors, medications, alerts). */
    public record ClinicStateResponse(Instant generatedAt, List<DepartmentState> departments,
                                      List<MedicationDto> medications, List<AlertDto> activeAlerts) {
    }
}
