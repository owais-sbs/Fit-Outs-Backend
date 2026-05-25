package com.fitouts.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailAndTenantUuid(String email, UUID tenantUuid);

    Optional<Account> findByIdAndTenantUuid(Long id, UUID tenantUuid);

    List<Account> findAllByTenantUuid(UUID tenantUuid);
}
