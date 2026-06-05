package com.fitouts.auth.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface OtpChallengeRepository extends JpaRepository<OtpChallenge, Long> {

    Optional<OtpChallenge> findByChallengeId(String challengeId);

    Optional<OtpChallenge> findByChallengeIdAndTenantUuid(String challengeId, UUID tenantUuid);
}
