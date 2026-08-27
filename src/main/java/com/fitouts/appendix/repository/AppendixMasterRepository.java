package com.fitouts.appendix.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.fitouts.appendix.domain.AppendixMaster;

public interface AppendixMasterRepository extends JpaRepository<AppendixMaster, UUID> {

    List<AppendixMaster> findByCompany_UuidAndActiveTrueOrderBySortOrderAscTitleAsc(UUID companyUuid);

    List<AppendixMaster> findByCompany_UuidOrderBySortOrderAscTitleAsc(UUID companyUuid);
}
