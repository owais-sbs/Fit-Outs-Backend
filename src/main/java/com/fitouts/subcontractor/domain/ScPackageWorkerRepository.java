package com.fitouts.subcontractor.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ScPackageWorkerRepository extends JpaRepository<ScPackageWorker, UUID> {

    List<ScPackageWorker> findByPackageUuidAndCompanyIdOrderByCreatedAtAsc(UUID packageUuid, UUID companyId);

    Optional<ScPackageWorker> findByPackageUuidAndWorkerUuid(UUID packageUuid, UUID workerUuid);
}
