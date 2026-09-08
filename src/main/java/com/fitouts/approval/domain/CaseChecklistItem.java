package com.fitouts.approval.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "case_checklist_item")
@Getter
@Setter
public class CaseChecklistItem {

    @Id
    private UUID uuid;

    @Column(name = "case_uuid", nullable = false)
    private UUID caseUuid;

    @Column(name = "document_type_code", nullable = false, length = 32)
    private String documentTypeCode;

    @Column(name = "document_type_name")
    private String documentTypeName;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;

    @Column(nullable = false, length = 24)
    private String status = "MISSING";

    @Column(name = "file_path", columnDefinition = "TEXT")
    private String filePath;

    /** AUTO_COLLECTED when pack assembly found it, UPLOADED when a person attached it. */
    @Column(length = 24)
    private String source;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "waived_by")
    private Long waivedBy;

    @Column(name = "waiver_reason", columnDefinition = "TEXT")
    private String waiverReason;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder = 0;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /** True when this item stops the case reaching READY_TO_SUBMIT. */
    public boolean isBlocking() {
        if (!required) return false;
        return "MISSING".equals(status) || "EXPIRED".equals(status);
    }

    /** Recomputes status from the attached file and its expiry. Waivers are left alone. */
    public void refreshStatus() {
        if ("WAIVED".equals(status) || "NOT_APPLICABLE".equals(status)) {
            return;
        }
        if (filePath == null || filePath.isBlank()) {
            status = "MISSING";
        } else if (expiryDate != null && expiryDate.isBefore(LocalDate.now())) {
            status = "EXPIRED";
        } else {
            status = "ATTACHED";
        }
    }

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
