package com.clinicagent.clinic.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "doctors")
public class Doctor {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(nullable = false)
    private boolean available = true;

    @Column(name = "max_concurrent_patients", nullable = false)
    private int maxConcurrentPatients = 1;

    protected Doctor() {
    }

    public Doctor(Department department, String name, int maxConcurrentPatients) {
        this.department = department;
        this.name = name;
        this.maxConcurrentPatients = maxConcurrentPatients;
    }

    public Long getId() {
        return id;
    }

    public Department getDepartment() {
        return department;
    }

    public String getName() {
        return name;
    }

    public boolean isAvailable() {
        return available;
    }

    public void setAvailable(boolean available) {
        this.available = available;
    }

    public int getMaxConcurrentPatients() {
        return maxConcurrentPatients;
    }
}
