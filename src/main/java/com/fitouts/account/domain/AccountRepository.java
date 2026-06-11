package com.fitouts.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailAndCompanyUuid(String email, UUID companyUuid);

    Optional<Account> findByIdAndCompanyUuid(Long id, UUID companyUuid);

    List<Account> findAllByCompanyUuid(UUID companyUuid);
}
