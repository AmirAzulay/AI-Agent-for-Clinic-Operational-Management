package com.clinicagent.clinic.api;

import com.clinicagent.clinic.api.dto.Dtos.AlertDto;
import com.clinicagent.clinic.api.dto.Dtos.ClinicStateResponse;
import com.clinicagent.clinic.api.dto.Dtos.DepartmentDto;
import com.clinicagent.clinic.api.dto.Dtos.DoctorDto;
import com.clinicagent.clinic.api.dto.Dtos.MedicationDto;
import com.clinicagent.clinic.api.dto.Dtos.PatientDto;
import com.clinicagent.clinic.api.dto.Dtos.RoomDto;
import com.clinicagent.clinic.domain.ClinicStateService;
import com.clinicagent.clinic.domain.Patient;
import com.clinicagent.clinic.domain.AlertRepository;
import com.clinicagent.clinic.domain.DepartmentRepository;
import com.clinicagent.clinic.domain.DoctorRepository;
import com.clinicagent.clinic.domain.MedicationRepository;
import com.clinicagent.clinic.domain.PatientRepository;
import com.clinicagent.clinic.domain.TreatmentRoomRepository;
import com.clinicagent.common.model.PatientStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

/** Read-only endpoints for the dashboard. Writes happen only through the simulation and the validated tools. */
@RestController
@RequestMapping("/api")
@Tag(name = "Clinic state", description = "Current clinic state for the dashboard (read only)")
@Transactional(readOnly = true)
public class ClinicReadController {

    private final ClinicStateService clinicState;
    private final DepartmentRepository departments;
    private final TreatmentRoomRepository rooms;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final MedicationRepository medications;
    private final AlertRepository alerts;
    private final Clock clock;

    public ClinicReadController(ClinicStateService clinicState, DepartmentRepository departments,
                                TreatmentRoomRepository rooms, DoctorRepository doctors,
                                PatientRepository patients, MedicationRepository medications,
                                AlertRepository alerts, Clock clock) {
        this.clinicState = clinicState;
        this.departments = departments;
        this.rooms = rooms;
        this.doctors = doctors;
        this.patients = patients;
        this.medications = medications;
        this.alerts = alerts;
        this.clock = clock;
    }

    @GetMapping("/clinic/state")
    @Operation(summary = "Full dashboard payload: departments with queues, rooms, doctors, medications, active alerts")
    public ClinicStateResponse clinicState() {
        return clinicState.currentState();
    }

    @GetMapping("/departments")
    public List<DepartmentDto> departments() {
        return departments.findAllByOrderByIdAsc().stream().map(DepartmentDto::from).toList();
    }

    @GetMapping("/departments/{id}")
    public DepartmentDto department(@PathVariable Long id) {
        return departments.findById(id).map(DepartmentDto::from)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Department " + id + " not found"));
    }

    @GetMapping("/rooms")
    public List<RoomDto> rooms(@RequestParam(required = false) Long departmentId) {
        var list = departmentId == null ? rooms.findAllByOrderByIdAsc() : rooms.findByDepartmentIdOrderByIdAsc(departmentId);
        return list.stream().map(RoomDto::from).toList();
    }

    @GetMapping("/doctors")
    public List<DoctorDto> doctors(@RequestParam(required = false) Long departmentId) {
        var list = departmentId == null ? doctors.findAllByOrderByIdAsc() : doctors.findByDepartmentIdOrderByIdAsc(departmentId);
        return list.stream().map(DoctorDto::from).toList();
    }

    @GetMapping("/patients")
    @Operation(summary = "Patients, optionally filtered by department and status")
    public List<PatientDto> patients(@RequestParam(required = false) Long departmentId,
                                     @RequestParam(required = false) PatientStatus status) {
        Instant now = clock.instant();
        Specification<Patient> spec = Specification.where(null);
        if (departmentId != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("department").get("id"), departmentId));
        }
        if (status != null) {
            spec = spec.and((root, q, cb) -> cb.equal(root.get("status"), status));
        }
        return patients.findAll(spec, Sort.by("arrivedAt")).stream().map(p -> PatientDto.from(p, now)).toList();
    }

    @GetMapping("/medications")
    public List<MedicationDto> medications() {
        return medications.findAllByOrderByIdAsc().stream().map(MedicationDto::from).toList();
    }

    @GetMapping("/alerts")
    @Operation(summary = "Alerts, newest first; activeOnly=true (default) hides resolved alerts")
    public List<AlertDto> alerts(@RequestParam(defaultValue = "true") boolean activeOnly) {
        var list = activeOnly ? alerts.findByActiveTrueOrderByCreatedAtDesc() : alerts.findAllByOrderByCreatedAtDesc();
        return list.stream().map(AlertDto::from).toList();
    }
}
