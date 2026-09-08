package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductivityNormRepository extends JpaRepository<ProductivityNorm, UUID> {

    @Query("SELECT n FROM ProductivityNorm n WHERE n.companyId IS NULL OR n.companyId = :companyId")
    List<ProductivityNorm> findVisible(@Param("companyId") UUID companyId);

    Optional<ProductivityNorm> findByWorkItemAndCompanyIdIsNull(String workItem);
}
