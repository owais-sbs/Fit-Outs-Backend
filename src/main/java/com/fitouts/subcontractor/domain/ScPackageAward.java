package com.fitouts.subcontractor.domain;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "sc_package_award")
@Getter
@Setter
public class ScPackageAward {

    @Id
    private UUID uuid;

    @Column(name = "package_uuid", nullable = false, unique = true)
    private UUID packageUuid;

    @Column(name = "organization_uuid", nullable = false)
    private UUID organizationUuid;

    @Column(name = "quote_uuid")
    private UUID quoteUuid;

    @Column(name = "awarded_value", precision = 18, scale = 2)
    private BigDecimal awardedValue;

    @Column(name = "awarded_at")
    private OffsetDateTime awardedAt;

    @Column(name = "contract_file_path", columnDefinition = "TEXT")
    private String contractFilePath;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "signature_audit_json", columnDefinition = "TEXT")
    private String signatureAuditJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (uuid == null) {
            uuid = UUID.randomUUID();
        }
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
