package com.fitouts.variation.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "variation_link")
@Getter
@Setter
public class VariationLink {

    @Id
    private UUID uuid;

    @Column(name = "variation_uuid", nullable = false)
    private UUID variationUuid;

    @Enumerated(EnumType.STRING)
    @Column(name = "link_type", nullable = false, length = 16)
    private VariationLinkType linkType;

    @Column(name = "room_id")
    private UUID roomId;

    @Column(name = "activity_uuid")
    private UUID activityUuid;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
