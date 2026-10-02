package com.fitouts.auth.domain;

import java.time.OffsetDateTime;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.fitouts.account.domain.Account;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, Long> {

    Optional<PasswordResetToken> findByTokenHashAndConsumedAtIsNull(String tokenHash);

    Optional<PasswordResetToken> findFirstByAccountAndConsumedAtIsNullOrderByCreatedAtDesc(Account account);

    @Modifying(clearAutomatically = true)
    @Query("UPDATE PasswordResetToken t SET t.consumedAt = :now WHERE t.account = :account AND t.consumedAt IS NULL")
    int invalidateActiveForAccount(@Param("account") Account account, @Param("now") OffsetDateTime now);
}
