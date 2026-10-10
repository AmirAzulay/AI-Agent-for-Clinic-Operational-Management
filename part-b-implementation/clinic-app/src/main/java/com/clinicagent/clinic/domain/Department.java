package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.DepartmentStatus;
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

    public int getOverloadThreshold() {
        return overloadThreshold;
    }
}
