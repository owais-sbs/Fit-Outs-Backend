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
@Table(name = "subcontractor_registrations")
@Getter
@Setter
public class SubcontractorRegistration implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "subcontractor_id", nullable = false)
    private UUID subcontractorId;

    @Column(name = "authority_id")
    private String authorityId;

    @Column(name = "registration_no")
    private String registrationNo;

    @Column(name = "expiry_date")
    private LocalDate expiryDate;

    @Column(name = "file_id")
    private String fileId;
}
