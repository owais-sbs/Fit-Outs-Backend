package com.fitouts.auth.domain;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PasswordSetupTokenRepository extends JpaRepository<PasswordSetupToken, Long> {

    Optional<PasswordSetupToken> findByTokenAndConsumedAtIsNull(String token);
}
