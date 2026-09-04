package com.fitouts.projectdoc.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectDocumentRepository extends JpaRepository<ProjectDocument, UUID> {

    List<ProjectDocument> findByProjectIdAndCompanyIdAndDeletedFalseOrderByCreatedAtDesc(
            Long projectId, UUID companyId);

    Optional<ProjectDocument> findByUuidAndCompanyIdAndDeletedFalse(UUID uuid, UUID companyId);

    List<ProjectDocument> findByProjectIdAndCompanyIdAndPublishedToClientTrueAndDeletedFalseOrderByCreatedAtDesc(
            Long projectId, UUID companyId);

    /** Used for version bump calculation (includes soft-deleted siblings so versions stay monotonic). */
    List<ProjectDocument> findByProjectIdAndCompanyIdOrderByCreatedAtDesc(Long projectId, UUID companyId);

    Optional<ProjectDocument> findFirstByFilePathAndCompanyIdAndDeletedFalse(String filePath, UUID companyId);

    Optional<ProjectDocument> findBySourceTypeAndSourceUuidAndCompanyIdAndDeletedFalse(
            String sourceType, UUID sourceUuid, UUID companyId);
}
