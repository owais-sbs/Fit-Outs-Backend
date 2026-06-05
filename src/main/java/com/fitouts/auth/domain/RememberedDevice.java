package com.fitouts.auth.domain;

import java.time.OffsetDateTime;

import com.fitouts.account.domain.Account;
import com.fitouts.tenant.domain.Tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "remembered_devices")
@Getter
@Setter
public class RememberedDevice {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private Tenant tenant;

    @Column(nullable = false, unique = true, length = 128)
    private String tokenHash;

    @Column(nullable = false, length = 255)
    private String label;

    @Column(nullable = false, length = 1024)
    private String userAgent;

    @Column(length = 128)
    private String ipHash;

    @Column(nullable = false)
    private OffsetDateTime firstSeenAt;

    @Column(nullable = false)
    private OffsetDateTime lastSeenAt;

    @Column(nullable = false)
    private OffsetDateTime trustedUntil;

    @Column(nullable = false)
    private Boolean revoked = false;
}
