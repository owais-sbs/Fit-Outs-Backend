package com.fitouts.lead.domain;

import org.springframework.data.jpa.domain.Specification;

public class LeadSpecification {

    public static Specification<Lead> filterLeads(LeadFilterDTO filter) {

        return (root, query, cb) -> {

            var predicate = cb.conjunction();

            predicate = cb.and(predicate,
                    cb.equal(root.get("isdeleted"), false));

            if (filter.getCompanyUuid() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("companyEntity").get("uuid"), filter.getCompanyUuid()));
            }

            if (filter.getStatus() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("status"), filter.getStatus()));
            }

            if (filter.getSourceId() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("sourceId"), filter.getSourceId()));
            }

            if (filter.getAssignedTo() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("assignedTo"), filter.getAssignedTo()));
            }

            if (filter.getSearch() != null &&
                    !filter.getSearch().isEmpty()) {

                predicate = cb.and(predicate,
                        cb.or(
                                cb.like(cb.lower(root.get("clientName")),
                                        "%" + filter.getSearch().toLowerCase() + "%"),

                                cb.like(cb.lower(root.get("phone")),
                                        "%" + filter.getSearch().toLowerCase() + "%"),

                                cb.like(cb.lower(root.get("email")),
                                        "%" + filter.getSearch().toLowerCase() + "%")
                        ));
            }

            return predicate;
        };
    }
}