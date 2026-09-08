package com.fitouts.approval.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** An authority comment against a submission, logged with a reason code so patterns show up. */
@Entity
@Table(name = "case_comment")
@Getter
@Setter
public class CaseComment {

    @Id
    private UUID uuid;

    @Column(name = "case_uuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "submission_uuid")
    private UUID submissionUuid;

    @Column(name = "comment_text", nullable = false, columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "reason_code", length = 64)
    private String reasonCode;

    @Column(name = "raised_date", nullable = false)
    private LocalDate raisedDate;

    @Column(name = "responded_date")
    private LocalDate respondedDate;

    @Column(name = "response_text", columnDefinition = "TEXT")
    private String responseText;

    @Column(name = "responsible_party", length = 120)
    private String responsibleParty;

    @Column(name = "created_by")
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        createdAt = OffsetDateTime.now();
    }
}
