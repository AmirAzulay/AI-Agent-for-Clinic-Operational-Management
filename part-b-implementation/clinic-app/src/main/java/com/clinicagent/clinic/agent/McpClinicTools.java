package com.clinicagent.clinic.agent;

import com.clinicagent.clinic.model.AlertSeverity;
import com.clinicagent.clinic.model.DepartmentStatus;
import org.springaicommunity.mcp.annotation.McpTool;
import org.springaicommunity.mcp.annotation.McpToolParam;
import org.springframework.stereotype.Component;

/**
 * The clinic tools as an MCP server (served at /mcp, behind the agent service token). Descriptions
 * are written for the model: when to use each tool, and what the backend will refuse. Every call
 * goes through {@link ClinicToolService}, which validates, executes and logs it.
 */
@Component
public class McpClinicTools {

    private final ClinicToolService tools;

    public McpClinicTools(ClinicToolService tools) {
        this.tools = tools;
    }

    @McpTool(name = "get_clinic_state", description = """
            Current state of the simulated clinic: per department its status, queue, waits, rooms \
            (available/occupied/inactive), doctors and walkouts in the last 15 simulated minutes; \
            medication stock with thresholds and restocks already on their way; active alerts. \
            Call this first, and again after acting to see the effect.""")
    public ClinicToolService.ClinicView getClinicState() {
        return tools.getClinicState();
    }

    @McpTool(name = "open_room", description = """
            Open one INACTIVE treatment room in a department so more patients can be treated at once. \
            Only allowed when the department is WARNING or worse and has an inactive room. Opening a \
            room helps only if the department also has a free doctor (doctorsAvailable and capacity).""")
    public ToolResult openRoom(
            @McpToolParam(description = "Department id from get_clinic_state") long departmentId,
            @McpToolParam(description = "Why, citing the numbers you saw (at least 10 characters)") String reason) {
        return tools.openRoom(departmentId, reason);
    }

    @McpTool(name = "create_alert", description = """
            Raise an alert for staff. Use for problems you cannot fix with another tool (for example a \
            department overloaded with no room left to open, or a doctor shortage). Refused if the same \
            type is already open for that department. Department alerts close by themselves when the \
            department is back to NORMAL.""")
    public ToolResult createAlert(
            @McpToolParam(description = "OVERLOAD, ROOM_SHORTAGE, STAFF_SHORTAGE, MEDICATION_SHORTAGE or OTHER") String type,
            @McpToolParam(description = "INFO, WARNING or CRITICAL") String severity,
            @McpToolParam(description = "Department id, or omit for a clinic-wide alert", required = false) Long departmentId,
            @McpToolParam(description = "What staff should know, 10–500 characters") String message,
            @McpToolParam(description = "Why you are raising it (at least 10 characters)") String reason) {
        AlertSeverity parsed = parse(AlertSeverity.class, severity);
        if (parsed == null) return ToolResult.rejected("severity must be INFO, WARNING or CRITICAL");
        return tools.createAlert(type == null ? null : type.strip().toUpperCase(), parsed, departmentId, message, reason);
    }

    @McpTool(name = "escalate_department_status", description = """
            Raise a department's status for a while, as an early warning before the queue shows it. \
            Only upward and one level at a time (NORMAL→WARNING→OVERLOADED→CRITICAL). The department \
            then shows the higher of this and its own status until the minutes run out.""")
    public ToolResult escalateDepartmentStatus(
            @McpToolParam(description = "Department id") long departmentId,
            @McpToolParam(description = "WARNING, OVERLOADED or CRITICAL") String status,
            @McpToolParam(description = "How long, 1–120 simulated minutes") int minutes,
            @McpToolParam(description = "Why, citing the trend you saw (at least 10 characters)") String reason) {
        DepartmentStatus parsed = parse(DepartmentStatus.class, status);
        if (parsed == null) return ToolResult.rejected("status must be WARNING, OVERLOADED or CRITICAL");
        return tools.escalateDepartmentStatus(departmentId, parsed, minutes, reason);
    }

    @McpTool(name = "request_medication_restock", description = """
            Order more of a medication. Only allowed when its stock is below its threshold and no \
            restock is already on its way. The delivery arrives 30 simulated minutes later.""")
    public ToolResult requestMedicationRestock(
            @McpToolParam(description = "Medication id from get_clinic_state") long medicationId,
            @McpToolParam(description = "Units to order, at least 1 and at most twice the medication's normal stock") int quantity,
            @McpToolParam(description = "Why (at least 10 characters)") String reason) {
        return tools.requestMedicationRestock(medicationId, quantity, reason);
    }

    @McpTool(name = "write_summary", description = """
            Save your operational summary for this run: what you saw, what you did and why, and what \
            staff should watch next. Call it once, at the end. 20–4000 characters.""")
    public ToolResult writeSummary(@McpToolParam(description = "The summary") String text) {
        return tools.writeSummary(text);
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value) {
        if (value == null) return null;
        try {
            return Enum.valueOf(type, value.strip().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
