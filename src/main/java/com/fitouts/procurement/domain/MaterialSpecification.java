package com.fitouts.procurement.domain;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.fitouts.procurement.api.MaterialFilterRequest;

import jakarta.persistence.criteria.Predicate;

public class MaterialSpecification {

    public static Specification<Material> filter(MaterialFilterRequest filter, UUID companyId) {
        return (root, query, cb) -> {
            Predicate predicate = cb.conjunction();
            predicate = cb.and(predicate, cb.equal(root.get("deleted"), false));
            if (companyId != null) {
                predicate = cb.and(predicate, cb.equal(root.get("company").get("uuid"), companyId));
            }
            if (filter != null) {
                if (filter.getMaterialCategoryId() != null) {
                    predicate = cb.and(predicate,
                            cb.equal(root.get("materialCategory").get("id"), filter.getMaterialCategoryId()));
                }
                if (filter.getActive() != null) {
                    predicate = cb.and(predicate, cb.equal(root.get("active"), filter.getActive()));
                }
                if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                    String pattern = "%" + filter.getSearch().toLowerCase() + "%";
                    predicate = cb.and(predicate, cb.or(
                            cb.like(cb.lower(root.get("materialName")), pattern),
                            cb.like(cb.lower(root.get("materialCode")), pattern),
                            cb.like(cb.lower(root.get("sku")), pattern)));
                }
            }
            return predicate;
        };
    }
}
