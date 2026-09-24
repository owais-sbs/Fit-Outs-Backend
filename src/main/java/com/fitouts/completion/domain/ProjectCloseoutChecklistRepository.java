package com.fitouts.completion.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ProjectCloseoutChecklistRepository extends JpaRepository<ProjectCloseoutChecklist, UUID> {

    Optional<ProjectCloseoutChecklist> findByProjectIdAndCompanyId(Long projectId, UUID companyId);

    List<ProjectCloseoutChecklist> findByCompanyIdAndProjectIdIn(UUID companyId, Collection<Long> projectIds);
}
