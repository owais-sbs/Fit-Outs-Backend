package com.fitouts.company.domain;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CompanyRepository extends JpaRepository<Company, UUID> {

    Optional<Company> findByDomainSlugIgnoreCase(String domainSlug);

    @Query("SELECT DISTINCT c FROM Company c LEFT JOIN FETCH c.subscriptionPlan")
    List<Company> findAllWithPlan();
}
