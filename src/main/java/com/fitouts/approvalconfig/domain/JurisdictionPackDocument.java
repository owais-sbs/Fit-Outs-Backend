package com.fitouts.approvalconfig.domain;

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
@Table(name = "jurisdiction_pack_documents")
@Getter
@Setter
public class JurisdictionPackDocument {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pack_permit_id", nullable = false)
    private UUID packPermitId;

    @Column(name = "document_type_id", nullable = false)
    private UUID documentTypeId;
}
