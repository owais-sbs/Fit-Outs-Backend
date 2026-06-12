package com.fitouts.account.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.auth.domain.Role;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    Optional<Account> findByEmailAndCompanyUuid(String email, UUID companyUuid);

    Optional<Account> findByIdAndCompanyUuid(Long id, UUID companyUuid);

    List<Account> findAllByCompanyUuid(UUID companyUuid);

    @Query("SELECT a FROM Account a JOIN a.roles r WHERE a.company.uuid = :companyUuid AND r = :role")
    List<Account> findAllByCompanyUuidAndRole(@Param("companyUuid") UUID companyUuid, @Param("role") Role role);
}
