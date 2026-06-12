package com.fitouts.employee.domain;

import com.fitouts.company.domain.Company;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.List;
import java.util.Optional;

public interface EmployeeRepository extends JpaRepository<Employee, Long>, JpaSpecificationExecutor<Employee> {

    Optional<Employee> findByEmailAndIsDeletedFalse(String email);

    List<Employee> findByCompanyAndIsDeletedFalse(Company company);

    boolean existsByEmailAndIsDeletedFalse(String email);
}
