package com.clinicagent.clinic.domain;

import java.time.Instant;

import com.clinicagent.clinic.model.DepartmentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "departments")
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 64)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DepartmentStatus status = DepartmentStatus.NORMAL;

    @Column(name = "max_queue", nullable = false)
    private int maxQueue;

    @Column(name = "warning_threshold", nullable = false)
    private int warningThreshold;

    @Column(name = "overload_threshold", nullable = false)
    private int overloadThreshold;

    /** Raised by the agent's escalate_department_status tool; shown until {@link #statusOverrideUntil}. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status_override", length = 16)
    private DepartmentStatus statusOverride;

    @Column(name = "status_override_until")
    private Instant statusOverrideUntil;

    protected Department() {
    }

    public Department(String name, int maxQueue, int warningThreshold, int overloadThreshold) {
        this.name = name;
        this.maxQueue = maxQueue;
        this.warningThreshold = warningThreshold;
        this.overloadThreshold = overloadThreshold;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public DepartmentStatus getStatus() {
        return status;
    }

    public void setStatus(DepartmentStatus status) {
        this.status = status;
    }

    public int getMaxQueue() {
        return maxQueue;
    }

    public int getWarningThreshold() {
        return warningThreshold;
    }

    public DepartmentStatus getStatusOverride() {
        return statusOverride;
    }

    public Instant getStatusOverrideUntil() {
        return statusOverrideUntil;
    }

    public void escalate(DepartmentStatus status, Instant until) {
        this.statusOverride = status;
        this.statusOverrideUntil = until;
        if (status.ordinal() > this.status.ordinal()) {
            this.status = status;
        }
    }

    /**
     * Applies the simulation's own status, keeping an unexpired escalation if it is higher.
     * An expired escalation is cleared.
     */
    public void applyComputedStatus(DepartmentStatus computed, Instant now) {
        if (statusOverride != null && !statusOverrideUntil.isAfter(now)) {
            statusOverride = null;
            statusOverrideUntil = null;
        }
        this.status = statusOverride != null && statusOverride.ordinal() > computed.ordinal() ? statusOverride : computed;
    }

    public int getOverloadThreshold() {
        return overloadThreshold;
    }
}
