package com.fitouts.account.domain;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.auth.domain.Role;

public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByEmail(String email);

    @Query("SELECT a FROM Account a LEFT JOIN FETCH a.company c LEFT JOIN FETCH c.subscriptionPlan WHERE LOWER(a.email) = LOWER(:email)")
    Optional<Account> findByEmailWithCompany(@Param("email") String email);

    Optional<Account> findByEmailAndCompanyUuid(String email, UUID companyUuid);

    Optional<Account> findByIdAndCompanyUuid(Long id, UUID companyUuid);

    List<Account> findAllByCompanyUuid(UUID companyUuid);

    @Query("SELECT a FROM Account a JOIN a.roles r WHERE a.company.uuid = :companyUuid AND r = :role")
    List<Account> findAllByCompanyUuidAndRole(@Param("companyUuid") UUID companyUuid, @Param("role") Role role);

    @Query("SELECT a FROM Account a JOIN FETCH a.company c JOIN a.roles r WHERE r = :role AND a.company IS NOT NULL")
    List<Account> findAllWithRole(@Param("role") Role role);

    @Query("""
            SELECT DISTINCT a FROM Account a
            LEFT JOIN FETCH a.company
            WHERE :adminRole MEMBER OF a.roles
            AND :superRole NOT MEMBER OF a.roles
            ORDER BY a.createdAt DESC
            """)
    List<Account> findAllCompanyAdmins(
            @Param("adminRole") Role adminRole,
            @Param("superRole") Role superRole);

    List<Account> findAllByPurgeAtIsNotNullAndPurgeAtAfterOrderByPurgeAtAsc(OffsetDateTime now);

    List<Account> findAllByPurgeAtIsNotNullAndPurgeAtLessThanEqual(OffsetDateTime now);

    @Query("""
            SELECT DISTINCT a FROM Account a
            LEFT JOIN FETCH a.company
            WHERE a.deletionScheduledAt IS NOT NULL
            AND a.purgeAt IS NOT NULL
            AND a.purgeAt > :now
            AND :adminRole MEMBER OF a.roles
            AND :superRole NOT MEMBER OF a.roles
            ORDER BY a.purgeAt ASC
            """)
    List<Account> findScheduledCompanyAdmins(
            @Param("now") OffsetDateTime now,
            @Param("adminRole") Role adminRole,
            @Param("superRole") Role superRole);

    @Query("""
            SELECT DISTINCT a FROM Account a
            LEFT JOIN FETCH a.company
            WHERE LOWER(a.email) IN :emails
            ORDER BY a.fullName
            """)
    List<Account> findAllByEmailInIgnoreCase(@Param("emails") Collection<String> emails);
}
