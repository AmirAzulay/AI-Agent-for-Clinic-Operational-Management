package com.clinicagent.clinic.simulation;

import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.Doctor;
import com.clinicagent.clinic.domain.Medication;
import com.clinicagent.clinic.domain.Patient;
import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.TreatmentRoom;
import com.clinicagent.clinic.model.RoomStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Writes one history snapshot (PLAN.md §3.3, §3.4): a row per department and per medication.
 * Runs inside the tick's transaction, so state and history always agree. {@code taken_at} is real
 * time, which is what retention ages rows by; the simulated time is the run's tick.
 */
@Component
public class SnapshotWriter {

    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SnapshotWriter(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    void write(SimulationRun run, long tick, Instant previousSnapshotSimTime, List<Department> departments,
               Map<Long, List<Patient>> waitingByDepartment, Map<Long, List<TreatmentRoom>> roomsByDepartment,
               Map<Long, List<Doctor>> doctorsByDepartment, List<Medication> medications) {
        Timestamp takenAt = Timestamp.from(clock.instant());
        Instant now = run.getSimNow();
        Map<Long, Integer> walkoutsSinceLast = walkoutsBetween(run.getId(), previousSnapshotSimTime, now);
        Map<Long, Double> walkoutRates = walkoutRates(run.getId());

        for (Department d : departments) {
            List<Patient> waiting = waitingByDepartment.getOrDefault(d.getId(), List.of());
            List<TreatmentRoom> rooms = roomsByDepartment.getOrDefault(d.getId(), List.of());
            double avgWait = waiting.stream().mapToDouble(p -> p.waitingMinutes(now)).average().orElse(0);
            double maxWait = waiting.stream().mapToDouble(p -> p.waitingMinutes(now)).max().orElse(0);
            jdbc.update("""
                    insert into department_snapshots (run_id, tick, taken_at, department_id, status, waiting_count,
                        avg_wait_min, max_wait_min, rooms_available, rooms_occupied, rooms_inactive,
                        doctors_available, left_count, left_rate)
                    values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    run.getId(), tick, takenAt, d.getId(), d.getStatus().name(), waiting.size(),
                    round1(avgWait), round1(maxWait),
                    count(rooms, RoomStatus.AVAILABLE), count(rooms, RoomStatus.OCCUPIED),
                    count(rooms, RoomStatus.INACTIVE) + count(rooms, RoomStatus.MAINTENANCE),
                    (int) doctorsByDepartment.getOrDefault(d.getId(), List.of()).stream().filter(Doctor::isAvailable).count(),
                    walkoutsSinceLast.getOrDefault(d.getId(), 0),
                    walkoutRates.getOrDefault(d.getId(), 0.0));
        }
        for (Medication m : medications) {
            jdbc.update("""
                    insert into medication_snapshots (run_id, tick, taken_at, medication_id, stock, threshold)
                    values (?, ?, ?, ?, ?, ?)
                    """, run.getId(), tick, takenAt, m.getId(), m.getStock(), m.getThreshold());
        }
    }

    private Map<Long, Integer> walkoutsBetween(long runId, Instant after, Instant upTo) {
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query("""
                select department_id, count(*) from patients
                where run_id = ? and status = 'LEFT' and left_at > ? and left_at <= ?
                group by department_id
                """, rs -> {
            result.put(rs.getLong(1), rs.getInt(2));
        }, runId, Timestamp.from(after), Timestamp.from(upTo));
        return result;
    }

    private Map<Long, Double> walkoutRates(long runId) {
        Map<Long, Double> result = new HashMap<>();
        jdbc.query("""
                select department_id, count(*) filter (where status = 'LEFT')::float / count(*)
                from patients where run_id = ? group by department_id
                """, rs -> {
            result.put(rs.getLong(1), rs.getDouble(2));
        }, runId);
        return result;
    }

    private static int count(List<TreatmentRoom> rooms, RoomStatus status) {
        return (int) rooms.stream().filter(r -> r.getStatus() == status).count();
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
