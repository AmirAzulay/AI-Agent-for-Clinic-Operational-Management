package com.clinicagent.clinic.api;

import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.simulation.ClinicModel;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Patient flow of the latest simulation run: who arrived, started treatment, went home treated
 * or walked out, derived from the patients' own timestamps (no extra table). Simulated time.
 */
@RestController
@RequestMapping("/api/clinic")
@Validated
@Tag(name = "Clinic state", description = "Current clinic state for the dashboard (read only)")
@Transactional(readOnly = true)
public class ActivityController {

    private final JdbcTemplate jdbc;
    private final SimulationRunRepository runs;

    public ActivityController(JdbcTemplate jdbc, SimulationRunRepository runs) {
        this.jdbc = jdbc;
        this.runs = runs;
    }

    @GetMapping("/activity")
    @Operation(summary = "Patients arriving, starting treatment, treated and walking out in the last N simulated minutes")
    public Activity activity(@RequestParam(defaultValue = "60") @Min(1) @Max(1440) int minutes,
                             @RequestParam(defaultValue = "40") @Min(1) @Max(500) int limit) {
        SimulationRun run = runs.findFirstByOrderByIdDesc().orElse(null);
        if (run == null) {
            return new Activity(null, null, minutes, List.of(), new FlowCounts(0, 0, 0, 0), List.of());
        }
        Instant to = run.getSimNow();
        Timestamp from = Timestamp.from(to.minus(Duration.ofMinutes(minutes)));
        Timestamp upTo = Timestamp.from(to);

        // One row per event; the timestamps already exist on each patient.
        String events = """
                select p.id, p.department_id, d.name, p.urgency, e.type, e.at,
                       extract(epoch from (coalesce(p.treatment_started_at, p.left_at) - p.arrived_at)) / 60.0 as waited
                from patients p
                join departments d on d.id = p.department_id
                cross join lateral (values
                    ('ARRIVED', p.arrived_at),
                    ('TREATMENT_STARTED', p.treatment_started_at),
                    ('TREATED', p.treatment_ended_at),
                    ('WALKED_OUT', p.left_at)) as e(type, at)
                where p.run_id = ? and e.at is not null and e.at > ? and e.at <= ?
                """;

        Map<Long, FlowCounts> perDepartment = new LinkedHashMap<>();
        Map<Long, String> names = new LinkedHashMap<>();
        jdbc.query("select id, name from departments order by id", rs -> {
            names.put(rs.getLong(1), rs.getString(2));
            perDepartment.put(rs.getLong(1), new FlowCounts(0, 0, 0, 0));
        });
        jdbc.query("select department_id, type, count(*) from (" + events + ") x group by department_id, type",
                rs -> {
                    long departmentId = rs.getLong(1);
                    String type = rs.getString(2);
                    int count = rs.getInt(3);
                    perDepartment.computeIfPresent(departmentId, (id, c) -> c.plus(type, count));
                }, run.getId(), from, upTo);

        List<Event> recent = jdbc.query(events + " order by e.at desc, p.id desc, e.type desc limit ?",
                (rs, i) -> {
                    Instant at = rs.getTimestamp(6).toInstant();
                    String type = rs.getString(5);
                    Double waited = switch (type) {
                        case "TREATMENT_STARTED", "WALKED_OUT" -> Math.round(rs.getDouble(7) * 10) / 10.0;
                        default -> null;
                    };
                    return new Event(at, simClock(run, at), type, rs.getLong(1), rs.getLong(2), rs.getString(3),
                            rs.getString(4), waited);
                }, run.getId(), from, upTo, limit);

        List<DepartmentFlow> departments = new ArrayList<>();
        FlowCounts total = new FlowCounts(0, 0, 0, 0);
        for (Map.Entry<Long, FlowCounts> e : perDepartment.entrySet()) {
            departments.add(new DepartmentFlow(e.getKey(), names.get(e.getKey()), e.getValue()));
            total = total.add(e.getValue());
        }
        return new Activity(run.getId(), simClock(run, to), minutes, departments, total, recent);
    }

    private static String simClock(SimulationRun run, Instant at) {
        long minutes = Duration.between(run.getSimStartedAt(), at).toMinutes() + ClinicModel.START_HOUR * 60L;
        return "%02d:%02d".formatted(Math.floorMod(minutes, 1440) / 60, Math.floorMod(minutes, 60));
    }

    public record Activity(Long runId, String simClock, int minutes, List<DepartmentFlow> departments,
                           FlowCounts total, List<Event> events) {
    }

    public record DepartmentFlow(long departmentId, String name, FlowCounts counts) {
    }

    public record FlowCounts(int arrived, int treatmentStarted, int treated, int walkedOut) {
        FlowCounts plus(String type, int n) {
            return switch (type) {
                case "ARRIVED" -> new FlowCounts(arrived + n, treatmentStarted, treated, walkedOut);
                case "TREATMENT_STARTED" -> new FlowCounts(arrived, treatmentStarted + n, treated, walkedOut);
                case "TREATED" -> new FlowCounts(arrived, treatmentStarted, treated + n, walkedOut);
                case "WALKED_OUT" -> new FlowCounts(arrived, treatmentStarted, treated, walkedOut + n);
                default -> this;
            };
        }

        FlowCounts add(FlowCounts o) {
            return new FlowCounts(arrived + o.arrived, treatmentStarted + o.treatmentStarted, treated + o.treated,
                    walkedOut + o.walkedOut);
        }
    }

    /**
     * @param type          ARRIVED, TREATMENT_STARTED, TREATED or WALKED_OUT
     * @param simClock      simulated time of day, HH:mm
     * @param waitedMinutes how long the patient waited (for TREATMENT_STARTED and WALKED_OUT)
     */
    public record Event(Instant at, String simClock, String type, long patientId, long departmentId,
                        String department, String urgency, Double waitedMinutes) {
    }
}
