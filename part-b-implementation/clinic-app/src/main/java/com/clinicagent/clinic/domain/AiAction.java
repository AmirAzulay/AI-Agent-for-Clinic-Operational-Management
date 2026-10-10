package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.AgentTool;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Map;

/** One tool call the agent requested, with the backend's validation verdict. Logged even when rejected. */
@Entity
@Table(name = "ai_actions")
public class AiAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "scan_id", nullable = false)
    private AiScan scan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AgentTool tool;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private Map<String, Object> parameters = Map.of();

    @Column(nullable = false)
    private String explanation;

    @Column(name = "validation_result", nullable = false)
    private String validationResult;

    @Column(nullable = false)
    private boolean executed;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AiAction() {
    }

    public AiAction(AiScan scan, AgentTool tool, Map<String, Object> parameters, String explanation,
                    String validationResult, boolean executed) {
        this.scan = scan;
        this.tool = tool;
        this.parameters = parameters;
        this.explanation = explanation;
        this.validationResult = validationResult;
        this.executed = executed;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public AiScan getScan() {
        return scan;
    }

    public AgentTool getTool() {
        return tool;
    }

    public Map<String, Object> getParameters() {
        return parameters;
    }

    public String getExplanation() {
        return explanation;
    }

    public String getValidationResult() {
        return validationResult;
    }

    public boolean isExecuted() {
        return executed;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
