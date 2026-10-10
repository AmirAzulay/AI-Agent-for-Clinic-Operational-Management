package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.Actor;
import com.clinicagent.common.model.RestockStatus;
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
@Table(name = "restock_requests")
public class RestockRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "medication_id", nullable = false)
    private Medication medication;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RestockStatus status = RestockStatus.OPEN;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by", nullable = false, length = 8)
    private Actor createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "fulfilled_at")
    private Instant fulfilledAt;

    protected RestockRequest() {
    }

    public RestockRequest(Medication medication, int quantity, Actor createdBy) {
        this.medication = medication;
        this.quantity = quantity;
        this.createdBy = createdBy;
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

    public Medication getMedication() {
        return medication;
    }

    public int getQuantity() {
        return quantity;
    }

    public RestockStatus getStatus() {
        return status;
    }

    public Actor getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getFulfilledAt() {
        return fulfilledAt;
    }

    public void fulfil(Instant at) {
        this.status = RestockStatus.FULFILLED;
        this.fulfilledAt = at;
    }
}
