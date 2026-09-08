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
@Table(name = "jurisdiction_pack_permits")
@Getter
@Setter
public class JurisdictionPackPermit {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "pack_id", nullable = false)
    private UUID packId;

    @Column(name = "permit_type_id", nullable = false)
    private UUID permitTypeId;

    @Column(name = "issuing_authority_id")
    private UUID issuingAuthorityId;

    @Column(name = "inclusion_rule", nullable = false, length = 40)
    private String inclusionRule = "ALWAYS";

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
