package com.clinicagent.clinic.history;

import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

/**
 * Read side of the history tables (PLAN.md §5.2). Ticks are simulated time: tick N of a run is
 * N × minutesPerTick simulated minutes after it started. Defaults to the latest run.
 */
@RestController
@RequestMapping("/api/history")
@Validated
@Tag(name = "History", description = "Snapshots and trends from the simulation history")
public class HistoryController {

    private final JdbcTemplate jdbc;
    private final SimulationRunRepository runs;

    public HistoryController(JdbcTemplate jdbc, SimulationRunRepository runs) {
        this.jdbc = jdbc;
        this.runs = runs;
    }

    @GetMapping("/snapshots")
    @Operation(summary = "Snapshot time series for charts: the last N simulated minutes of a run")
    public SnapshotSeries snapshots(@RequestParam(required = false) Long runId,
                                    @RequestParam(required = false) Long departmentId,
                                    @RequestParam(defaultValue = "120") @Min(1) @Max(10080) int lastMinutes) {
        SimulationRun run = run(runId);
        long fromTick = Math.max(0, run.getCurrentTick() - lastMinutes / run.getMinutesPerTick());
        List<DepartmentSnapshot> departments = jdbc.query("""
                select tick, department_id, status, waiting_count, avg_wait_min, max_wait_min, rooms_available,
                       rooms_occupied, rooms_inactive, doctors_available, left_count, left_rate
                from department_snapshots
                where run_id = ? and tick >= ? and (?::bigint is null or department_id = ?)
                order by tick, department_id
                """, (rs, i) -> new DepartmentSnapshot(rs.getLong(1), rs.getLong(1) * run.getMinutesPerTick(),
                rs.getLong(2), rs.getString(3), rs.getInt(4), rs.getDouble(5), rs.getDouble(6), rs.getInt(7),
                rs.getInt(8), rs.getInt(9), rs.getInt(10), rs.getInt(11), rs.getDouble(12)),
                run.getId(), fromTick, departmentId, departmentId);
        List<MedicationSnapshot> medications = jdbc.query("""
                select tick, medication_id, stock, threshold from medication_snapshots
                where run_id = ? and tick >= ? order by tick, medication_id
                """, (rs, i) -> new MedicationSnapshot(rs.getLong(1), rs.getLong(1) * run.getMinutesPerTick(),
                rs.getLong(2), rs.getInt(3), rs.getInt(4)), run.getId(), fromTick);
        return new SnapshotSeries(run.getId(), run.getMinutesPerTick(), departments, medications);
    }

    @GetMapping("/trends")
    @Operation(summary = "Per-department and per-medication change over the last N simulated minutes (agent context)")
    public Trends trends(@RequestParam(defaultValue = "15") @Min(1) @Max(1440) int minutes) {
        SimulationRun run = run(null);
        long fromTick = Math.max(0, run.getCurrentTick() - minutes / run.getMinutesPerTick());
        List<DepartmentTrend> departments = jdbc.query("""
                select department_id,
                       count(*)                                                   as samples,
                       (array_agg(waiting_count order by tick))[1]                as waiting_first,
                       (array_agg(waiting_count order by tick desc))[1]           as waiting_last,
                       round(avg(avg_wait_min)::numeric, 1)                       as avg_wait,
                       max(max_wait_min)                                          as max_wait,
                       sum(left_count)                                            as walkouts,
                       (array_agg(status order by tick desc))[1]                  as status_now
                from department_snapshots where run_id = ? and tick >= ?
                group by department_id order by department_id
                """, (rs, i) -> new DepartmentTrend(rs.getLong(1), rs.getInt(2), rs.getInt(3), rs.getInt(4),
                rs.getInt(4) - rs.getInt(3), rs.getDouble(5), rs.getDouble(6), rs.getInt(7), rs.getString(8)),
                run.getId(), fromTick);
        List<MedicationTrend> medications = jdbc.query("""
                select medication_id,
                       (array_agg(stock order by tick))[1]      as stock_first,
                       (array_agg(stock order by tick desc))[1] as stock_last,
                       max(threshold)                           as threshold
                from medication_snapshots where run_id = ? and tick >= ?
                group by medication_id order by medication_id
                """, (rs, i) -> new MedicationTrend(rs.getLong(1), rs.getInt(2), rs.getInt(3),
                rs.getInt(3) - rs.getInt(2), rs.getInt(4)), run.getId(), fromTick);
        return new Trends(run.getId(), minutes, departments, medications);
    }

    private SimulationRun run(Long runId) {
        return (runId != null ? runs.findById(runId) : runs.findFirstByOrderByIdDesc())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No simulation run found"));
    }

    public record SnapshotSeries(long runId, int minutesPerTick, List<DepartmentSnapshot> departments,
                                 List<MedicationSnapshot> medications) {
    }

    public record DepartmentSnapshot(long tick, long simMinute, long departmentId, String status, int waitingCount,
                                     double avgWaitMinutes, double maxWaitMinutes, int roomsAvailable,
                                     int roomsOccupied, int roomsInactive, int doctorsAvailable, int walkouts,
                                     double walkoutRate) {
    }

    public record MedicationSnapshot(long tick, long simMinute, long medicationId, int stock, int threshold) {
    }

    public record Trends(long runId, int minutes, List<DepartmentTrend> departments, List<MedicationTrend> medications) {
    }

    public record DepartmentTrend(long departmentId, int samples, int waitingAtStart, int waitingNow,
                                  int waitingChange, double avgWaitMinutes, double maxWaitMinutes, int walkouts,
                                  String statusNow) {
    }

    public record MedicationTrend(long medicationId, int stockAtStart, int stockNow, int stockChange, int threshold) {
    }
}
