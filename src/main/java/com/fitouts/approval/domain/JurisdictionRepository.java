package com.fitouts.approval.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface JurisdictionRepository extends JpaRepository<Jurisdiction, UUID> {

    @Query("SELECT j FROM Jurisdiction j WHERE (j.companyId IS NULL OR j.companyId = :companyId) "
            + "ORDER BY j.emirate ASC, j.communityName ASC")
    List<Jurisdiction> findVisible(@Param("companyId") UUID companyId);

    /**
     * Tenant rows sort first so a company override beats the derived seed row.
     */
    @Query("SELECT j FROM Jurisdiction j WHERE j.communityKey = :communityKey "
            + "AND (j.companyId IS NULL OR j.companyId = :companyId) "
            + "ORDER BY CASE WHEN j.companyId IS NULL THEN 1 ELSE 0 END ASC")
    List<Jurisdiction> findByCommunityKeyVisible(@Param("communityKey") String communityKey,
                                                 @Param("companyId") UUID companyId);

    Optional<Jurisdiction> findByCommunityKeyAndBuildingNameAndCompanyIdIsNull(String communityKey, String buildingName);

    List<Jurisdiction> findByCompanyIdIsNull();

    long countByVerifiedFalse();
}
