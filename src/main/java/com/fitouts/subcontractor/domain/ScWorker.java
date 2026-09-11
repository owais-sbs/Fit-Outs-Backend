package com.fitouts.subcontractor.domain;

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
@Table(name = "sc_worker")
@Getter
@Setter
public class ScWorker {

    @Id
    private UUID uuid;

    @Column(name = "profile_uuid", nullable = false)
    private UUID profileUuid;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 120)
    private String trade;

    @Column(name = "passport_number", length = 64)
    private String passportNumber;

    @Column(name = "visa_expiry")
    private LocalDate visaExpiry;

    @Column(name = "emirates_id_expiry")
    private LocalDate emiratesIdExpiry;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "induction_date")
    private LocalDate inductionDate;

    @Column(name = "access_card_expiry")
    private LocalDate accessCardExpiry;

    @Column(name = "trade_cert_expiry")
    private LocalDate tradeCertExpiry;

    @Column(name = "induction_completed", nullable = false)
    private boolean inductionCompleted;

    @Column(name = "access_card_number", length = 64)
    private String accessCardNumber;

    @Column(name = "photo_file_path", columnDefinition = "TEXT")
    private String photoFilePath;

    @Column(name = "organization_uuid")
    private UUID organizationUuid;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
