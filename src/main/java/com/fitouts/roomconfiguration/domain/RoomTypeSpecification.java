package com.fitouts.roomconfiguration.domain;

import java.util.UUID;

import org.springframework.data.jpa.domain.Specification;

import com.fitouts.roomconfiguration.api.RoomTypeFilterRequest;

public class RoomTypeSpecification {

    public static Specification<RoomType> filter(RoomTypeFilterRequest filter, UUID companyId) {

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

            if (filter.getRoomMasterId() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("roomMaster").get("id"), filter.getRoomMasterId()));
            }

            if (filter.getActive() != null) {
                predicate = cb.and(predicate,
                        cb.equal(root.get("active"), filter.getActive()));
            }

            if (filter.getSearch() != null && !filter.getSearch().isBlank()) {
                String pattern = "%" + filter.getSearch().toLowerCase() + "%";
                predicate = cb.and(predicate,
                        cb.or(
                                cb.like(cb.lower(root.get("roomTypeName")), pattern),
                                cb.like(cb.lower(root.get("roomCode")), pattern),
                                cb.like(cb.lower(root.get("description")), pattern)
                        ));
            }

            return predicate;
        };
    }
}
