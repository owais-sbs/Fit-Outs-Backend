package com.fitouts.employee.domain;

import com.fitouts.company.domain.Company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByEmailAndIsDeletedFalse(String email);

    List<Employee> findByCompanyAndIsDeletedFalse(Company company);

    List<Employee> findByCompany_UuidAndIsDeletedFalse(UUID companyUuid);

    List<Employee> findByIsDeletedFalse();

    /**
     * Company-scoped staff plus legacy rows that never got company_id backfilled.
     */
    @Query("""
            SELECT e FROM Employee e
            WHERE e.isDeleted = false
              AND (e.company.uuid = :companyId OR e.company IS NULL)
            """)
    List<Employee> findVisibleForCompany(@Param("companyId") UUID companyId);
    
    boolean existsByEmailAndIsDeletedFalse(String email);

    Optional<Employee> findByAccountId(Long accountId);

    List<Employee> findByAccountIdIn(List<Long> accountIds);
}
