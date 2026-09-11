package com.fitouts.approvalconfig.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ApprovalPermitPropertyTypeRepository extends JpaRepository<ApprovalPermitPropertyType, UUID> {

    boolean existsByCompanyId(UUID companyId);

    List<ApprovalPermitPropertyType> findByCompanyId(UUID companyId);

    List<ApprovalPermitPropertyType> findByPermitTypeId(UUID permitTypeId);

    List<ApprovalPermitPropertyType> findByPropertyTypeId(UUID propertyTypeId);

    boolean existsByPermitTypeIdAndPropertyTypeId(UUID permitTypeId, UUID propertyTypeId);
}
