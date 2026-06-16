package com.fitouts.roomconfiguration.domain;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface RoomTypeRepository extends JpaRepository<RoomType, UUID>,
        JpaSpecificationExecutor<RoomType> {

    Optional<RoomType> findByIdAndDeletedFalse(UUID id);

    boolean existsByCompanyUuidAndRoomCodeAndDeletedFalse(UUID companyUuid, String roomCode);

    boolean existsByCompanyUuidAndRoomCodeAndIdNotAndDeletedFalse(UUID companyUuid, String roomCode, UUID id);
}
