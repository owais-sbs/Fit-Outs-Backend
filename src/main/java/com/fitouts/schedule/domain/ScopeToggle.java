package com.fitouts.schedule.domain;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** A yes/no scope question that decides whether a block of template activities exists. */
@Entity
@Table(name = "scope_toggle")
@Getter
@Setter
public class ScopeToggle {

    @Id
    private UUID uuid;

    @Column(name = "company_id")
    private UUID companyId;

    @Column(nullable = false, length = 48)
    private String code;

    @Column(nullable = false, length = 180)
    private String label;

    @Column(name = "default_on", nullable = false)
    private boolean defaultOn = true;

    @Column(columnDefinition = "text")
    private String description;

    @PrePersist
    void onCreate() {
        if (uuid == null) uuid = UUID.randomUUID();
    }
}
