package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.domain.AiAction;
import com.clinicagent.clinic.domain.AiActionRepository;
import com.clinicagent.clinic.domain.AiScan;
import com.clinicagent.clinic.domain.Alert;
import com.clinicagent.clinic.domain.AlertRepository;
import com.clinicagent.clinic.domain.ClinicStateService;
import com.clinicagent.clinic.domain.Department;
import com.clinicagent.clinic.domain.DepartmentRepository;
import com.clinicagent.clinic.domain.Medication;
import com.clinicagent.clinic.domain.MedicationRepository;
import com.clinicagent.clinic.domain.RestockRequest;
import com.clinicagent.clinic.domain.RestockRequestRepository;
import com.clinicagent.clinic.domain.SimulationRun;
import com.clinicagent.clinic.domain.SimulationRunRepository;
import com.clinicagent.clinic.domain.TreatmentRoom;
import com.clinicagent.clinic.domain.TreatmentRoomRepository;
import com.clinicagent.clinic.model.Actor;
import com.clinicagent.clinic.model.AgentTool;
import com.clinicagent.clinic.model.AlertSeverity;
import com.clinicagent.clinic.model.DepartmentStatus;
import com.clinicagent.clinic.model.RestockStatus;
import com.clinicagent.clinic.model.RoomStatus;
import com.clinicagent.clinic.simulation.ClinicModel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * The agent's tools (PLAN.md §4.1). Each call runs in one transaction: validate against the live
 * state, execute if allowed, and log the call (executed or rejected, with its reason) as an
 * {@code ai_actions} row under the current agent run. The MCP server and the tests call these
 * methods; nothing else may change the clinic on the agent's behalf.
 */
@Service
public class ClinicToolService {

    /** Simulated minutes between a restock request and the delivery. */
    public static final int RESTOCK_DELIVERY_MINUTES = 30;
    /** Walkouts are reported over this many simulated minutes. */
    static final int RECENT_MINUTES = 15;

    private final DepartmentRepository departments;
    private final TreatmentRoomRepository rooms;
    private final MedicationRepository medications;
    private final AlertRepository alerts;
    private final RestockRequestRepository restocks;
    private final SimulationRunRepository runs;
    private final AiActionRepository actions;
    private final AgentRunService agentRuns;
    private final ClinicStateService clinicState;
    private final JdbcTemplate jdbc;

    public ClinicToolService(DepartmentRepository departments, TreatmentRoomRepository rooms,
                             MedicationRepository medications, AlertRepository alerts,
                             RestockRequestRepository restocks, SimulationRunRepository runs,
                             AiActionRepository actions, AgentRunService agentRuns,
                             ClinicStateService clinicState, JdbcTemplate jdbc) {
        this.departments = departments;
        this.rooms = rooms;
        this.medications = medications;
        this.alerts = alerts;
        this.restocks = restocks;
        this.runs = runs;
        this.actions = actions;
        this.agentRuns = agentRuns;
        this.clinicState = clinicState;
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- read

    /** Everything the agent needs to decide, in one compact view. Logged like any other call. */
    @Transactional
    public ClinicView getClinicState() {
        SimulationRun run = runs.findFirstByOrderByIdDesc().orElse(null);
        var state = clinicState.currentState();
        Instant now = state.generatedAt();
        Map<Long, Integer> walkouts = run == null ? Map.of() : recentWalkouts(run.getId(), now);
        Map<Long, Boolean> restockOnTheWay = new HashMap<>();
        restocks.findByStatusOrderByIdAsc(RestockStatus.OPEN)
                .forEach(r -> restockOnTheWay.put(r.getMedication().getId(), true));

        List<ClinicView.DepartmentView> deptViews = state.departments().stream()
                .map(d -> new ClinicView.DepartmentView(d.id(), d.name(), d.status().name(), d.waitingCount(),
                        d.inTreatmentCount(), d.avgWaitMinutes(), d.maxWaitMinutes(), d.roomsAvailable(),
                        d.roomsOccupied(), d.roomsInactive(), d.doctorsAvailable(), d.doctorsTotal(),
                        walkouts.getOrDefault(d.id(), 0)))
                .toList();
        List<ClinicView.MedicationView> medViews = state.medications().stream()
                .map(m -> new ClinicView.MedicationView(m.id(), m.name(), m.stock(), m.threshold(),
                        m.belowThreshold(), restockOnTheWay.getOrDefault(m.id(), false)))
                .toList();
        List<ClinicView.AlertView> alertViews = state.activeAlerts().stream()
                .map(a -> new ClinicView.AlertView(a.id(), a.type(), a.severity().name(), a.departmentId(), a.message()))
                .toList();

        ClinicView view = new ClinicView(run != null && run.isRunning(), run == null ? null : simClock(run),
                deptViews, medViews, alertViews);
        log(AgentTool.READ_CLINIC_STATE, Map.of(), "read the clinic state", "OK", true);
        return view;
    }

    // ---------------------------------------------------------------- actions

    @Transactional
    public ToolResult openRoom(long departmentId, String reason) {
        Map<String, Object> params = Map.of("departmentId", departmentId);
        return act(AgentTool.OPEN_ROOM, params, reason, () -> {
            Department department = departments.findById(departmentId).orElse(null);
            long inactive = department == null ? 0 : rooms.countByDepartmentIdAndStatus(departmentId, RoomStatus.INACTIVE);
            return ToolValidator.openRoom(department, inactive);
        }, () -> {
            TreatmentRoom room = rooms.findFirstByDepartmentIdAndStatusOrderByIdAsc(departmentId, RoomStatus.INACTIVE)
                    .orElseThrow();
            room.setStatus(RoomStatus.AVAILABLE);
            return ToolResult.done("Opened room " + room.getName() + " in " + room.getDepartment().getName(),
                    Map.of("roomId", room.getId(), "room", room.getName()));
        });
    }

    @Transactional
    public ToolResult createAlert(String type, AlertSeverity severity, Long departmentId, String message, String reason) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("type", type);
        params.put("severity", severity);
        params.put("departmentId", departmentId);
        params.put("message", message);
        return act(AgentTool.CREATE_ALERT, params, reason, () -> {
            if (type == null || type.isBlank() || severity == null) {
                return Optional.of("type and severity are required");
            }
            Department department = departmentId == null ? null : departments.findById(departmentId).orElse(null);
            boolean duplicate = departmentId == null
                    ? alerts.existsByActiveTrueAndTypeAndDepartmentIsNull(type)
                    : alerts.existsByActiveTrueAndTypeAndDepartmentId(type, departmentId);
            return ToolValidator.createAlert(departmentId != null, department, message, duplicate);
        }, () -> {
            SimulationRun run = runs.findFirstByOrderByIdDesc().orElseThrow();
            Department department = departmentId == null ? null : departments.findById(departmentId).orElseThrow();
            Alert alert = alerts.save(new Alert(run.getId(), run.getSimNow(), type, severity, department,
                    message.strip(), Actor.AI));
            return ToolResult.done("Alert " + alert.getId() + " raised (" + severity + ")", Map.of("alertId", alert.getId()));
        });
    }

    @Transactional
    public ToolResult escalateDepartmentStatus(long departmentId, DepartmentStatus status, int minutes, String reason) {
        Map<String, Object> params = Map.of("departmentId", departmentId, "status", String.valueOf(status), "minutes", minutes);
        return act(AgentTool.SET_DEPARTMENT_STATUS, params, reason,
                () -> ToolValidator.escalate(departments.findById(departmentId).orElse(null), status, minutes),
                () -> {
                    SimulationRun run = runs.findFirstByOrderByIdDesc().orElseThrow();
                    Department department = departments.findById(departmentId).orElseThrow();
                    Instant until = run.getSimNow().plus(Duration.ofMinutes(minutes));
                    department.escalate(status, until);
                    return ToolResult.done(department.getName() + " escalated to " + status + " for " + minutes + " minutes",
                            Map.of("until", until.toString()));
                });
    }

    @Transactional
    public ToolResult requestMedicationRestock(long medicationId, int quantity, String reason) {
        Map<String, Object> params = Map.of("medicationId", medicationId, "quantity", quantity);
        return act(AgentTool.REQUEST_MEDICATION_RESTOCK, params, reason, () -> ToolValidator.restock(
                medications.findById(medicationId).orElse(null), quantity,
                restocks.existsByMedicationIdAndStatus(medicationId, RestockStatus.OPEN)), () -> {
            SimulationRun run = runs.findFirstByOrderByIdDesc().orElseThrow();
            Medication medication = medications.findById(medicationId).orElseThrow();
            Instant deliverAt = run.getSimNow().plus(Duration.ofMinutes(RESTOCK_DELIVERY_MINUTES));
            RestockRequest request = restocks.save(new RestockRequest(run.getId(), medication, quantity, Actor.AI,
                    run.getSimNow(), deliverAt));
            return ToolResult.done("Ordered " + quantity + " × " + medication.getName() + ", arriving in "
                    + RESTOCK_DELIVERY_MINUTES + " simulated minutes", Map.of("restockId", request.getId()));
        });
    }

    /** Stores the run's operational summary. Allowed whether or not a simulation is running. */
    @Transactional
    public ToolResult writeSummary(String text) {
        Optional<String> rejection = ToolValidator.summary(text);
        AiScan scan = agentRuns.currentOrDirect();
        if (rejection.isEmpty()) {
            scan.setSummary(text.strip());
        }
        log(scan, AgentTool.GENERATE_SUMMARY, Map.of("length", text == null ? 0 : text.length()),
                text == null ? "" : text.strip(), rejection.orElse("OK"), rejection.isEmpty());
        return rejection.map(ToolResult::rejected).orElseGet(() -> ToolResult.done("Summary saved", Map.of()));
    }

    // ---------------------------------------------------------------- shared

    private ToolResult act(AgentTool tool, Map<String, Object> params, String reason,
                           Supplier<Optional<String>> validate, Supplier<ToolResult> execute) {
        boolean running = runs.findFirstByOrderByIdDesc().map(SimulationRun::isRunning).orElse(false);
        Optional<String> rejection = ToolValidator.reason(reason)
                .or(() -> ToolValidator.simulationRunning(running))
                .or(validate);
        String explanation = reason == null ? "" : reason.strip();
        if (rejection.isPresent()) {
            log(tool, params, explanation, rejection.get(), false);
            return ToolResult.rejected(rejection.get());
        }
        ToolResult result = execute.get();
        log(tool, params, explanation, "OK: " + result.result(), true);
        return result;
    }

    private void log(AgentTool tool, Map<String, Object> params, String explanation, String validation, boolean executed) {
        log(agentRuns.currentOrDirect(), tool, params, explanation, validation, executed);
    }

    private void log(AiScan scan, AgentTool tool, Map<String, Object> params, String explanation, String validation,
                     boolean executed) {
        Instant simTime = runs.findFirstByOrderByIdDesc().map(SimulationRun::getSimNow).orElse(null);
        Map<String, Object> stored = new LinkedHashMap<>();
        params.forEach((k, v) -> stored.put(k, v == null ? null : v instanceof Number || v instanceof Boolean ? v : v.toString()));
        actions.save(new AiAction(scan, tool, stored, explanation.isEmpty() ? "(no reason given)" : explanation,
                truncate(validation, 255), executed, simTime));
    }

    private Map<Long, Integer> recentWalkouts(long runId, Instant now) {
        Map<Long, Integer> result = new HashMap<>();
        jdbc.query("""
                select department_id, count(*) from patients
                where run_id = ? and status = 'LEFT' and left_at > ?
                group by department_id
                """, rs -> {
            result.put(rs.getLong(1), rs.getInt(2));
        }, runId, Timestamp.from(now.minus(Duration.ofMinutes(RECENT_MINUTES))));
        return result;
    }

    private static String simClock(SimulationRun run) {
        long minutes = run.getCurrentTick() * run.getMinutesPerTick() + ClinicModel.START_HOUR * 60L;
        return "Day %d %02d:%02d".formatted(minutes / 1440 + 1, (minutes % 1440) / 60, minutes % 60);
    }

    private static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max - 1) + "…";
    }

    /** The clinic as the agent sees it. Walkouts cover the last 15 simulated minutes. */
    public record ClinicView(boolean simulationRunning, String simClock, List<DepartmentView> departments,
                             List<MedicationView> medications, List<AlertView> activeAlerts) {

        public record DepartmentView(long id, String name, String status, int waiting, int inTreatment,
                                     double avgWaitMinutes, double maxWaitMinutes, int roomsAvailable,
                                     int roomsOccupied, int roomsInactive, int doctorsAvailable, int doctorsTotal,
                                     int walkoutsLast15Min) {
        }

        public record MedicationView(long id, String name, int stock, int threshold, boolean belowThreshold,
                                     boolean restockOnTheWay) {
        }

        public record AlertView(long id, String type, String severity, Long departmentId, String message) {
        }
    }
}
