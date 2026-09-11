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

import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

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

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_status", length = 64)
    private ScContractStatus contractStatus;

    @Column(name = "contract_file_path", columnDefinition = "TEXT")
    private String contractFilePath;

    @Column(name = "admin_signed_at")
    private OffsetDateTime adminSignedAt;

    @Column(name = "admin_signed_by")
    private Long adminSignedBy;

    @Column(name = "admin_signer_name")
    private String adminSignerName;

    @Column(name = "admin_signer_title")
    private String adminSignerTitle;

    @Column(name = "admin_signature_audit_json", columnDefinition = "TEXT")
    private String adminSignatureAuditJson;

    @Column(name = "signed_at")
    private OffsetDateTime signedAt;

    @Column(name = "subcontractor_signed_by")
    private Long subcontractorSignedBy;

    @Column(name = "subcontractor_signer_name")
    private String subcontractorSignerName;

    @Column(name = "subcontractor_signer_title")
    private String subcontractorSignerTitle;

    @Column(name = "signature_audit_json", columnDefinition = "TEXT")
    private String signatureAuditJson;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public ScContractStatus getContractStatus() {
        if (contractStatus != null) {
            return contractStatus;
        }
        if (signedAt != null) {
            return ScContractStatus.SIGNED_AND_EXECUTED;
        }
        if (adminSignedAt != null || contractFilePath != null) {
            return ScContractStatus.WAITING_FOR_CONTRACTOR_SIGNATURE;
        }
        return ScContractStatus.WAITING_FOR_ADMIN_SIGNATURE;
    }

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
