package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "subcontractor_documents")
@Getter
@Setter
public class SubcontractorDocument implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "document_type_id")
    private String documentTypeId;

    @Column(name = "reference_no")
    private String referenceNo;

    @Column(name = "issue_date")
    private LocalDate issueDate;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "file_id")
    private String fileId;

    @Column(name = "status")
    private String status;

    @Column(name = "verified_by")
    private Long verifiedBy; // Account ID

    @Column(name = "verified_date")
    private OffsetDateTime verifiedDate;
}
