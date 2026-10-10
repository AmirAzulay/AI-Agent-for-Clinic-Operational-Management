package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.Actor;
import com.clinicagent.common.model.AlertSeverity;
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

import java.time.Instant;

@Entity
@Table(name = "alerts")
public class Alert {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Machine-readable kind, e.g. LONG_WAIT, DEPARTMENT_OVERLOAD, ROOM_SHORTAGE, DOCTOR_OVERLOAD, MEDICATION_SHORTAGE. */
    @Column(nullable = false, length = 32)
    private String type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AlertSeverity severity;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id")
    private Department department;

    @Column(nullable = false)
    private String message;

    @Column(nullable = false)
    private boolean active = true;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 8)
    private Actor createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected Alert() {
    }

    public Alert(String type, AlertSeverity severity, Department department, String message, Actor createdBy) {
        this.type = type;
        this.severity = severity;
        this.department = department;
        this.message = message;
        this.createdBy = createdBy;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public void resolve(Instant at) {
        this.active = false;
        this.resolvedAt = at;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public AlertSeverity getSeverity() {
        return severity;
    }

    public Department getDepartment() {
        return department;
    }

    public String getMessage() {
        return message;
    }

    public boolean isActive() {
        return active;
    }

    public Actor getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }
}
