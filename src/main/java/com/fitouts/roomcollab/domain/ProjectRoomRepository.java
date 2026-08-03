package com.fitouts.roomcollab.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectRoomRepository extends JpaRepository<ProjectRoom, UUID> {
    List<ProjectRoom> findByProjectIdOrderBySortOrderAscFloorLabelAscNameAsc(Long projectId);
    Optional<ProjectRoom> findByUuidAndProjectId(UUID uuid, Long projectId);
    Optional<ProjectRoom> findByProjectIdAndFloorLabelAndName(Long projectId, String floorLabel, String name);
}
