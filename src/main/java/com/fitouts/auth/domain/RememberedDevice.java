package com.fitouts.auth.domain;

import java.io.Serializable;
import java.time.OffsetDateTime;

import com.fitouts.account.domain.Account;
import com.fitouts.company.domain.Company;

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
public class RememberedDevice implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id")
    private Account account;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(nullable = true, unique = true, length = 128)
    private String tokenHash;

    @Column(nullable = true, length = 255)
    private String label;

    @Column(nullable = true, length = 1024)
    private String userAgent;

    @Column(length = 128)
    private String ipHash;

    @Column(nullable = true)
    private OffsetDateTime firstSeenAt;

    @Column(nullable = true)
    private OffsetDateTime lastSeenAt;

    @Column(nullable = true)
    private OffsetDateTime trustedUntil;

    @Column(nullable = true)
    private Boolean revoked = false;
}
