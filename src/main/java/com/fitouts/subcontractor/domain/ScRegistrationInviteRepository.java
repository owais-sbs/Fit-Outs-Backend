package com.fitouts.subcontractor.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScRegistrationInviteRepository extends JpaRepository<ScRegistrationInvite, UUID> {

    Optional<ScRegistrationInvite> findByTokenAndUsedAtIsNull(UUID token);
}
