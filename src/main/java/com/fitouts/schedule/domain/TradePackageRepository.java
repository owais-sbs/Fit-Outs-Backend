package com.fitouts.schedule.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface TradePackageRepository extends JpaRepository<TradePackage, UUID> {

    Optional<TradePackage> findByCodeAndCompanyIdIsNull(String code);

    List<TradePackage> findByCompanyIdIsNullOrderBySortOrderAsc();

    /** Global catalogue plus this tenant's own additions. */
    @Query("select t from TradePackage t where t.active = true and (t.companyId is null or t.companyId = :companyId) "
            + "order by t.sortOrder asc")
    List<TradePackage> findVisible(UUID companyId);
}
