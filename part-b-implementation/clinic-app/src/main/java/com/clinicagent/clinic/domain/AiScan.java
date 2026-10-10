package com.clinicagent.clinic.domain;

import com.clinicagent.common.model.ScanTrigger;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

/** One agent cycle. Its row also serves as the "last scan time" the scheduler checks. */
@Entity
@Table(name = "ai_scans")
public class AiScan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 16)
    private ScanTrigger triggerType;

    @Column
    private String summary;

    protected AiScan() {
    }

    public AiScan(ScanTrigger triggerType) {
        this.triggerType = triggerType;
    }

    @PrePersist
    void onCreate() {
        if (startedAt == null) {
            startedAt = Instant.now();
        }
    }

    public void finish(Instant at, String summary) {
        this.finishedAt = at;
        this.summary = summary;
    }

    public Long getId() {
        return id;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getFinishedAt() {
        return finishedAt;
    }

    public ScanTrigger getTriggerType() {
        return triggerType;
    }

    public String getSummary() {
        return summary;
    }
}
