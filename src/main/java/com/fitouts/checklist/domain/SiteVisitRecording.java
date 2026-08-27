package com.fitouts.checklist.domain;

import java.time.OffsetDateTime;
import java.util.UUID;

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
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "site_visit_recordings")
@Getter
@Setter
public class SiteVisitRecording {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_visit_uuid", nullable = false)
    private SiteVisit siteVisit;

    @Column(name = "audio_path", nullable = false, length = 512)
    private String audioPath;

    @Column(name = "duration_seconds")
    private Integer durationSeconds;

    @Column(columnDefinition = "TEXT")
    private String transcript;

    @Column(name = "ai_summary", columnDefinition = "TEXT")
    private String aiSummary;

    @Enumerated(EnumType.STRING)
    @Column(name = "processing_status", nullable = false, length = 20)
    private RecordingProcessingStatus processingStatus = RecordingProcessingStatus.PENDING;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
