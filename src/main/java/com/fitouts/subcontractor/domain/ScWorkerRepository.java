package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScWorkerRepository extends JpaRepository<ScWorker, UUID> {

    List<ScWorker> findByProfileUuidOrderByFullNameAsc(UUID profileUuid);

    Optional<ScWorker> findByUuidAndProfileUuid(UUID uuid, UUID profileUuid);
}
