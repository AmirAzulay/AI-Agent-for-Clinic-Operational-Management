package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.PatientStatus;
import com.clinicagent.common.model.Urgency;
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
import jakarta.persistence.Table;

import java.time.Duration;
import java.time.Instant;

/**
 * A simulated patient. Holds operational data only: where they are in the flow and since when.
 * No clinical information is ever stored (project book, section 2.5).
 */
@Entity
@Table(name = "patients")
public class Patient {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PatientStatus status = PatientStatus.WAITING;

    @Column(name = "arrived_at", nullable = false)
    private Instant arrivedAt;

    @Column(name = "treatment_started_at")
    private Instant treatmentStartedAt;

    @Column(name = "treatment_ended_at")
    private Instant treatmentEndedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private TreatmentRoom room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private Doctor doctor;

    protected Patient() {
    }

    public Patient(Department department, Urgency urgency, Instant arrivedAt) {
        this.department = department;
        this.urgency = urgency;
        this.arrivedAt = arrivedAt;
    }

    /** Minutes spent waiting: until treatment started, or until {@code now} if still waiting. */
    public double waitingMinutes(Instant now) {
        Instant end = treatmentStartedAt != null ? treatmentStartedAt : now;
        return Duration.between(arrivedAt, end).toSeconds() / 60.0;
    }

    public Long getId() {
        return id;
    }

    public Department getDepartment() {
        return department;
    }

    public Urgency getUrgency() {
        return urgency;
    }

    public PatientStatus getStatus() {
        return status;
    }

    public void setStatus(PatientStatus status) {
        this.status = status;
    }

    public Instant getArrivedAt() {
        return arrivedAt;
    }

    public Instant getTreatmentStartedAt() {
        return treatmentStartedAt;
    }

    public void setTreatmentStartedAt(Instant treatmentStartedAt) {
        this.treatmentStartedAt = treatmentStartedAt;
    }

    public Instant getTreatmentEndedAt() {
        return treatmentEndedAt;
    }

    public void setTreatmentEndedAt(Instant treatmentEndedAt) {
        this.treatmentEndedAt = treatmentEndedAt;
    }

    public TreatmentRoom getRoom() {
        return room;
    }

    public void setRoom(TreatmentRoom room) {
        this.room = room;
    }

    public Doctor getDoctor() {
        return doctor;
    }

    public void setDoctor(Doctor doctor) {
        this.doctor = doctor;
    }
}
