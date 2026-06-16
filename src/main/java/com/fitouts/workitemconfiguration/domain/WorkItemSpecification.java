package com.fitouts.workitemconfiguration.domain;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.fitouts.workitemconfiguration.api.WorkItemFilterRequest;

public class WorkItemSpecification {

    public static Specification<WorkItem> filter(WorkItemFilterRequest filter, UUID companyId) {

        return (root, query, cb) -> {

            var predicate = cb.conjunction();

            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));

            if (companyId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("company").get("uuid"), companyId));
            }

            if (filter.getCategory() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("category"), filter.getCategory()));
            }

            if (filter.getWorkItemMasterId() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("workItemMaster").get("id"), filter.getWorkItemMasterId()));
            }

            if (filter.getActive() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("active"), filter.getActive()));
            }

            if (filter.getCeilingApplicable() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("ceilingApplicable"), filter.getCeilingApplicable()));
            }

            if (filter.getWallApplicable() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("wallApplicable"), filter.getWallApplicable()));
            }

            if (filter.getFloorApplicable() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("floorApplicable"), filter.getFloorApplicable()));
            }

            if (filter.getUnitType() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("unitType"), filter.getUnitType()));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().toLowerCase() + "%";
                predicate = cb.and(predicate,
                        cb.or(
                                cb.like(cb.lower(root.get("workItemName")), pattern),
                                cb.like(cb.lower(root.get("workItemCode")), pattern),
                                cb.like(cb.lower(root.get("description")), pattern)
                        ));
            }

            return predicate;
        };
    }

    public static Specification<WorkItem> filterBySurfaceType(String surfaceType, UUID companyId) {

        return (root, query, cb) -> {

            var predicate = cb.conjunction();

            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            predicate = cb.and(predicate, cb.equal(root.get("active"), true));

            if (companyId != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("company").get("uuid"), companyId));
            }

            if (surfaceType != null) {
                switch (surfaceType.toLowerCase()) {
                    case "ceiling":
                        predicate = cb.and(predicate,
                                cb.equal(root.get("ceilingApplicable"), true));
                        break;
                    case "wall":
                        predicate = cb.and(predicate,
                                cb.equal(root.get("wallApplicable"), true));
                        break;
                    case "floor":
                        predicate = cb.and(predicate,
                                cb.equal(root.get("floorApplicable"), true));
                        break;
                }
            }

            return predicate;
        };
    }
}
