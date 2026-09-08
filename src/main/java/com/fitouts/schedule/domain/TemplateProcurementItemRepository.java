package com.fitouts.schedule.domain;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TemplateProcurementItemRepository extends JpaRepository<TemplateProcurementItem, UUID> {

    List<TemplateProcurementItem> findByTemplateUuidOrderBySortOrderAsc(UUID templateUuid);

    List<TemplateProcurementItem> findByTemplateUuidIsNull();

    void deleteByTemplateUuid(UUID templateUuid);
}
