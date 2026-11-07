package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.DepartmentRepository;
import com.clinicagent.clinic.domain.Doctor;
import com.clinicagent.clinic.domain.DoctorRepository;
import com.clinicagent.clinic.domain.Medication;
import com.clinicagent.clinic.domain.MedicationRepository;
import com.clinicagent.clinic.domain.Patient;
import com.clinicagent.clinic.domain.PatientRepository;
import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.domain.TreatmentRoom;
import com.clinicagent.clinic.domain.TreatmentRoomRepository;
import com.clinicagent.clinic.history.DatabaseSizeGuard;
import com.clinicagent.clinic.model.PatientStatus;
import com.clinicagent.clinic.model.RoomStatus;
import com.clinicagent.clinic.model.StopReason;
import com.clinicagent.clinic.model.Urgency;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;
import java.util.stream.Collectors;

/**
 * One simulation tick, in one transaction (PLAN.md phase 2):
 * <ol>
 *   <li>treatments that are due end and free their room;</li>
 *   <li>new patients arrive per department (Poisson, by time of day and scenario);</li>
 *   <li>waiting patients past their patience walk out;</li>
 *   <li>waiting patients start treatment while a room and a doctor with capacity are free,
 *       most urgent first, then longest waiting; treatment uses medication;</li>
 *   <li>each department's status follows its queue;</li>
 *   <li>every {@code snapshot-every-minutes} a history snapshot is written, unless the size guard
 *       has paused history writes;</li>
 *   <li>the run stops by itself after its maximum number of ticks.</li>
 * </ol>
 * Everything iterates in id order and draws from the run's seeded generator, so a seed always
 * produces the same run.
 */
@Service
public class TickService {

    private static final Comparator<Patient> TREATMENT_ORDER = Comparator
            .comparing((Patient p) -> urgencyRank(p.getUrgency()))
            .thenComparing(Patient::getArrivedAt)
            .thenComparing(Patient::getId);

    private final SimulationRunRepository runs;
    private final DepartmentRepository departments;
    private final TreatmentRoomRepository rooms;
    private final DoctorRepository doctors;
    private final PatientRepository patients;
    private final MedicationRepository medications;
    private final WalkoutService walkouts;
    private final PatienceSampler patience;
    private final SnapshotWriter snapshots;
    private final DatabaseSizeGuard sizeGuard;
    private final SimulationProperties properties;
    private final Clock clock;

    public TickService(SimulationRunRepository runs, DepartmentRepository departments, TreatmentRoomRepository rooms,
                       DoctorRepository doctors, PatientRepository patients, MedicationRepository medications,
                       WalkoutService walkouts, PatienceSampler patience, SnapshotWriter snapshots,
                       DatabaseSizeGuard sizeGuard, SimulationProperties properties, Clock clock) {
        this.runs = runs;
        this.departments = departments;
        this.rooms = rooms;
        this.doctors = doctors;
        this.patients = patients;
        this.medications = medications;
        this.walkouts = walkouts;
        this.patience = patience;
        this.snapshots = snapshots;
        this.sizeGuard = sizeGuard;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public TickResult tick(long runId, SimulationScenario scenario, RandomGenerator random) {
        SimulationRun run = runs.findById(runId).orElseThrow();
        if (!run.isRunning()) {
            throw new IllegalStateException("Run " + runId + " is not running");
        }
        long tick = run.advance();
        Instant tickStart = run.simTimeOf(tick - 1);
        Instant now = run.getSimNow();
        int minutesPerTick = run.getMinutesPerTick();
        long simMinutes = tick * minutesPerTick;

        List<Department> allDepartments = departments.findAllByOrderByIdAsc();
        Map<Long, List<TreatmentRoom>> roomsByDept = byDepartment(rooms.findAllByOrderByIdAsc(), r -> r.getDepartment().getId());
        Map<Long, List<Doctor>> doctorsByDept = byDepartment(doctors.findAllByOrderByIdAsc(), d -> d.getDepartment().getId());
        List<Medication> allMedications = medications.findAllByOrderByIdAsc();

        // 1. Treatments that are due end.
        int finished = 0;
        List<Patient> inTreatment = new ArrayList<>(patients.findByStatusOrderByIdAsc(PatientStatus.IN_TREATMENT));
        for (Patient p : List.copyOf(inTreatment)) {
            if (!p.getTreatmentDueAt().isAfter(now)) {
                p.finishTreatment();
                if (p.getRoom() != null && p.getRoom().getStatus() == RoomStatus.OCCUPIED) {
                    p.getRoom().setStatus(RoomStatus.AVAILABLE);
                }
                inTreatment.remove(p);
                finished++;
            }
        }

        // 2. Arrivals.
        int arrivals = 0;
        for (Department d : allDepartments) {
            int count = ClinicModel.poisson(
                    ClinicModel.expectedArrivals(d.getName(), scenario.arrivalMultiplier(), simMinutes - minutesPerTick, minutesPerTick),
                    random);
            for (int i = 0; i < count; i++) {
                Urgency urgency = ClinicModel.urgency(random);
                Integer patienceMin = patience.sample(urgency, random);
                Instant arrivedAt = now.minusSeconds(random.nextInt(0, minutesPerTick * 60));
                if (arrivedAt.isBefore(tickStart)) arrivedAt = tickStart;
                patients.save(new Patient(run.getId(), d, urgency, arrivedAt, patienceMin));
                arrivals++;
            }
        }
        patients.flush();

        // 3. Walkouts.
        int walkedOut = walkouts.processWalkouts(now).values().stream().mapToInt(Integer::intValue).sum();
        patients.flush();

        // 4. Start treatments.
        Map<Long, Integer> doctorLoad = new HashMap<>();
        for (Patient p : inTreatment) {
            if (p.getDoctor() != null) doctorLoad.merge(p.getDoctor().getId(), 1, Integer::sum);
        }
        Map<Long, List<Patient>> waitingByDept = byDepartment(
                patients.findByStatusOrderByIdAsc(PatientStatus.WAITING), p -> p.getDepartment().getId());
        int started = 0;
        for (Department d : allDepartments) {
            List<Patient> queue = new ArrayList<>(waitingByDept.getOrDefault(d.getId(), List.of()));
            queue.sort(TREATMENT_ORDER);
            List<TreatmentRoom> freeRooms = new ArrayList<>(roomsByDept.getOrDefault(d.getId(), List.of()).stream()
                    .filter(r -> r.getStatus() == RoomStatus.AVAILABLE).toList());
            List<Doctor> deptDoctors = doctorsByDept.getOrDefault(d.getId(), List.of());
            while (!queue.isEmpty() && !freeRooms.isEmpty()) {
                Doctor doctor = deptDoctors.stream()
                        .filter(Doctor::isAvailable)
                        .filter(doc -> doctorLoad.getOrDefault(doc.getId(), 0) < doc.getMaxConcurrentPatients())
                        .min(Comparator.comparing((Doctor doc) -> doctorLoad.getOrDefault(doc.getId(), 0))
                                .thenComparing(Doctor::getId))
                        .orElse(null);
                if (doctor == null) break;
                Patient patient = queue.removeFirst();
                TreatmentRoom room = freeRooms.removeFirst();
                Duration duration = ClinicModel.treatmentDuration(patient.getUrgency(), random);
                patient.startTreatment(room, doctor, now, now.plus(duration));
                room.setStatus(RoomStatus.OCCUPIED);
                doctorLoad.merge(doctor.getId(), 1, Integer::sum);
                useMedication(allMedications, random);
                started++;
            }
            waitingByDept.put(d.getId(), queue);
        }

        // 5. Department status follows the queue and room pressure.
        for (Department d : allDepartments) {
            List<Patient> waiting = waitingByDept.getOrDefault(d.getId(), List.of());
            double maxWait = waiting.stream().mapToDouble(p -> p.waitingMinutes(now)).max().orElse(0);
            boolean freeRoom = roomsByDept.getOrDefault(d.getId(), List.of()).stream()
                    .anyMatch(r -> r.getStatus() == RoomStatus.AVAILABLE);
            d.setStatus(ClinicModel.statusFor(waiting.size(), maxWait, freeRoom,
                    d.getWarningThreshold(), d.getOverloadThreshold(), d.getMaxQueue()));
        }
        patients.flush();

        // 6. History snapshot.
        boolean snapshot = simMinutes % properties.snapshotEveryMinutes() == 0;
        boolean snapshotWritten = false;
        if (snapshot && sizeGuard.historyWritesAllowed()) {
            Instant previous = now.minus(Duration.ofMinutes(properties.snapshotEveryMinutes()));
            snapshots.write(run, tick, previous, allDepartments, waitingByDept, roomsByDept, doctorsByDept, allMedications);
            snapshotWritten = true;
        }

        // 7. A run ends by itself after its maximum number of ticks.
        boolean completed = run.hasReachedMaxTicks();
        if (completed) {
            run.stop(clock.instant(), StopReason.COMPLETED);
        }
        return new TickResult(tick, now, arrivals, started, finished, walkedOut, snapshotWritten, completed);
    }

    /** Picks 0–2 different medications by weight and takes 1–3 units of each; stock never goes below 0. */
    private static void useMedication(List<Medication> all, RandomGenerator random) {
        int kinds = ClinicModel.medicationsUsed(random);
        List<Medication> candidates = new ArrayList<>(all);
        for (int i = 0; i < kinds && !candidates.isEmpty(); i++) {
            double total = candidates.stream().mapToDouble(m -> ClinicModel.medicationWeight(m.getName())).sum();
            double pick = random.nextDouble() * total;
            Medication chosen = candidates.getLast();
            for (Medication m : candidates) {
                pick -= ClinicModel.medicationWeight(m.getName());
                if (pick <= 0) {
                    chosen = m;
                    break;
                }
            }
            candidates.remove(chosen);
            chosen.setStock(Math.max(0, chosen.getStock() - ClinicModel.unitsUsed(random)));
        }
    }

    private static int urgencyRank(Urgency urgency) {
        return switch (urgency) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private static <T> Map<Long, List<T>> byDepartment(List<T> items, java.util.function.Function<T, Long> department) {
        return items.stream().collect(Collectors.groupingBy(department, LinkedHashMap::new, Collectors.toList()));
    }

    /** What one tick did, for logs, the step API and tests. */
    public record TickResult(long tick, Instant simTime, int arrivals, int treatmentsStarted, int treatmentsFinished,
                             int walkouts, boolean snapshotWritten, boolean runCompleted) {
    }
}
