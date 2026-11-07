package com.clinicagent.clinic.domain;

import com.clinicagent.clinic.model.PatientStatus;
import com.clinicagent.clinic.model.Urgency;
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

    /** Minutes this patient is willing to wait; null means they never leave. */
    @Column(name = "patience_min")
    private Integer patienceMin;

    @Column(name = "left_at")
    private Instant leftAt;

    /** The simulation run that created this patient. */
    @Column(name = "run_id")
    private Long runId;

    /** When the current treatment will end (simulated time). */
    @Column(name = "treatment_due_at")
    private Instant treatmentDueAt;

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
        this(department, urgency, arrivedAt, null);
    }

    public Patient(Department department, Urgency urgency, Instant arrivedAt, Integer patienceMin) {
        this.department = department;
        this.urgency = urgency;
        this.arrivedAt = arrivedAt;
        this.patienceMin = patienceMin;
    }

    public Patient(Long runId, Department department, Urgency urgency, Instant arrivedAt, Integer patienceMin) {
        this(department, urgency, arrivedAt, patienceMin);
        this.runId = runId;
    }

    /** Starts treatment in {@code room} with {@code doctor} at {@code now}, ending at {@code dueAt}. */
    public void startTreatment(TreatmentRoom room, Doctor doctor, Instant now, Instant dueAt) {
        if (status != PatientStatus.WAITING) {
            throw new IllegalStateException("Only a waiting patient can start treatment, patient " + id + " is " + status);
        }
        this.status = PatientStatus.IN_TREATMENT;
        this.room = room;
        this.doctor = doctor;
        this.treatmentStartedAt = now;
        this.treatmentDueAt = dueAt;
    }

    /** Ends treatment at its due time. The caller frees the room. */
    public void finishTreatment() {
        if (status != PatientStatus.IN_TREATMENT) {
            throw new IllegalStateException("Only a patient in treatment can finish, patient " + id + " is " + status);
        }
        this.status = PatientStatus.DONE;
        this.treatmentEndedAt = treatmentDueAt;
    }

    public Long getRunId() {
        return runId;
    }

    public Instant getTreatmentDueAt() {
        return treatmentDueAt;
    }

    /** True if the patient is still waiting and has waited longer than their patience at {@code now}. */
    public boolean hasRunOutOfPatience(Instant now) {
        return status == PatientStatus.WAITING
                && patienceMin != null
                && waitingMinutes(now) > patienceMin;
    }

    /** The patient walks out: they leave the queue without treatment. */
    public void leave(Instant at) {
        if (status != PatientStatus.WAITING) {
            throw new IllegalStateException("Only a waiting patient can leave, patient " + id + " is " + status);
        }
        this.status = PatientStatus.LEFT;
        this.leftAt = at;
    }

    /** Minutes spent waiting: until treatment started or the patient left, or until {@code now}. */
    public double waitingMinutes(Instant now) {
        Instant end = treatmentStartedAt != null ? treatmentStartedAt : leftAt != null ? leftAt : now;
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

    public Integer getPatienceMin() {
        return patienceMin;
    }

    public Instant getLeftAt() {
        return leftAt;
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
