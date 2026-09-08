package com.fitouts.subcontractor.domain;

import java.io.Serializable;
import java.time.LocalDate;
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
@Table(name = "subcontractor_workers")
@Getter
@Setter
public class SubcontractorWorker implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "trade")
    private String trade;

    @Column(name = "passport_no")
    private String passportNo;

    @Column(name = "visa_expiry")
    private LocalDate visaExpiry;

    @Column(name = "eid_expiry")
    private LocalDate eidExpiry;

    @Column(name = "insurance_expiry")
    private LocalDate insuranceExpiry;

    @Column(name = "certificates_json", columnDefinition = "text")
    private String certificatesJson;

    @Column(name = "induction_date")
    private LocalDate inductionDate;

    @Column(name = "access_card_no")
    private String accessCardNo;

    @Column(name = "access_card_expiry")
    private LocalDate accessCardExpiry;

    @Column(name = "is_site_eligible", nullable = false)
    private Boolean isSiteEligible = false; // Computed eligibility result

    @Column(name = "photo_file_id")
    private String photoFileId;
}
