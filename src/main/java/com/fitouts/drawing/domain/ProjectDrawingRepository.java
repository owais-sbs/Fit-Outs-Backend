package com.fitouts.drawing.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDrawingRepository extends JpaRepository<ProjectDrawing, UUID> {
    List<ProjectDrawing> findByProjectIdAndDeletedFalseOrderByCreatedAtDesc(Long projectId);
    Optional<ProjectDrawing> findByIdAndDeletedFalse(UUID id);
}
