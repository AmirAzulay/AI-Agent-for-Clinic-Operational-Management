package com.clinicagent.clinic.domain;

import com.clinicagent.clinic.api.dto.Dtos.AlertDto;
import com.clinicagent.clinic.api.dto.Dtos.ClinicStateResponse;
import com.clinicagent.clinic.api.dto.Dtos.DepartmentState;
import com.clinicagent.clinic.api.dto.Dtos.DoctorDto;
import com.clinicagent.clinic.api.dto.Dtos.MedicationDto;
import com.clinicagent.clinic.api.dto.Dtos.RoomDto;
import com.clinicagent.common.model.PatientStatus;
import com.clinicagent.common.model.RoomStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Assembles the dashboard payload from the current state. This is also the state the
 * Context Builder will feed to the agent in phase 4, so it is computed in one place.
 */
@Service
public class ClinicStateService {

    private final DepartmentRepository departments;
    private final TreatmentRoomRepository rooms;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final MedicationRepository medications;
    private final AlertRepository alerts;
    private final Clock clock;

    public ClinicStateService(DepartmentRepository departments, TreatmentRoomRepository rooms,
                              DoctorRepository doctors, PatientRepository patients,
                              MedicationRepository medications, AlertRepository alerts, Clock clock) {
        this.departments = departments;
        this.rooms = rooms;
        this.doctors = doctors;
        this.patients = patients;
        this.medications = medications;
        this.alerts = alerts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public ClinicStateResponse currentState() {
        Instant now = clock.instant();

        Map<Long, List<TreatmentRoom>> roomsByDept = rooms.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(r -> r.getDepartment().getId()));
        Map<Long, List<Doctor>> doctorsByDept = doctors.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(d -> d.getDepartment().getId()));
        Map<Long, List<Patient>> activePatientsByDept = patients
                .findByStatusInOrderByArrivedAtAsc(EnumSet.of(PatientStatus.WAITING, PatientStatus.IN_TREATMENT))
                .stream()
                .collect(Collectors.groupingBy(p -> p.getDepartment().getId()));

        List<DepartmentState> departmentStates = departments.findAllByOrderByIdAsc().stream()
                .map(d -> departmentState(d,
                        roomsByDept.getOrDefault(d.getId(), List.of()),
                        doctorsByDept.getOrDefault(d.getId(), List.of()),
                        activePatientsByDept.getOrDefault(d.getId(), List.of()),
                        now))
                .toList();

        return new ClinicStateResponse(
                now,
                departmentStates,
                medications.findAllByOrderByIdAsc().stream().map(MedicationDto::from).toList(),
                alerts.findByActiveTrueOrderByCreatedAtDesc().stream().map(AlertDto::from).toList());
    }

    private static DepartmentState departmentState(Department d, List<TreatmentRoom> rooms, List<Doctor> doctors,
                                                   List<Patient> activePatients, Instant now) {
        List<Patient> waiting = activePatients.stream().filter(p -> p.getStatus() == PatientStatus.WAITING).toList();
        int inTreatment = activePatients.size() - waiting.size();
        double avgWait = waiting.stream().mapToDouble(p -> p.waitingMinutes(now)).average().orElse(0.0);
        double maxWait = waiting.stream().mapToDouble(p -> p.waitingMinutes(now)).max().orElse(0.0);

        Map<RoomStatus, Long> roomCounts = rooms.stream()
                .collect(Collectors.groupingBy(TreatmentRoom::getStatus, Collectors.counting()));
        Function<RoomStatus, Integer> count = s -> roomCounts.getOrDefault(s, 0L).intValue();

        int doctorsAvailable = (int) doctors.stream().filter(Doctor::isAvailable).count();

        return new DepartmentState(
                d.getId(), d.getName(), d.getStatus(), d.getMaxQueue(),
                waiting.size(), inTreatment,
                round1(avgWait), round1(maxWait),
                count.apply(RoomStatus.AVAILABLE), count.apply(RoomStatus.OCCUPIED),
                count.apply(RoomStatus.INACTIVE), count.apply(RoomStatus.MAINTENANCE),
                doctorsAvailable, doctors.size(),
                rooms.stream().map(RoomDto::from).toList(),
                doctors.stream().map(DoctorDto::from).toList());
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
