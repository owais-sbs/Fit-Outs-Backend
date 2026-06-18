package com.fitouts.roomconfiguration.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomMasterRepository extends JpaRepository<RoomMaster, UUID>,
        JpaSpecificationExecutor<RoomMaster> {

    Optional<RoomMaster> findByIdAndDeletedFalse(UUID id);

    List<RoomMaster> findByCompanyUuidAndDeletedFalse(UUID companyUuid);

    boolean existsByCompanyUuidAndCodeAndDeletedFalse(UUID companyUuid, String code);

    boolean existsByCompanyUuidAndCodeAndIdNotAndDeletedFalse(UUID companyUuid, String code, UUID id);
}
