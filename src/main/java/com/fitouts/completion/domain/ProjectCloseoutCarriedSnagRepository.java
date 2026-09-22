package com.fitouts.completion.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProjectCloseoutCarriedSnagRepository extends JpaRepository<ProjectCloseoutCarriedSnag, UUID> {

    List<ProjectCloseoutCarriedSnag> findByChecklistUuid(UUID checklistUuid);

    @Modifying(clearAutomatically = true)
    @Query("delete from ProjectCloseoutCarriedSnag c where c.checklistUuid = :checklistUuid")
    void deleteByChecklistUuid(@Param("checklistUuid") UUID checklistUuid);
}
