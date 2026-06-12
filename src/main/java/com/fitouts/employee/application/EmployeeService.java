package com.fitouts.employee.application;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fitouts.account.domain.Account;
import com.fitouts.account.domain.AccountRepository;
import com.fitouts.auth.domain.Role;
import com.fitouts.company.application.CompanyService;
import com.fitouts.employee.api.EmployeeCreateRequest;
import com.fitouts.employee.api.EmployeeResponse;
import com.fitouts.employee.api.EmployeeUpdateRequest;
import com.fitouts.employee.domain.Employee;
import com.fitouts.employee.domain.EmployeeRepository;
import com.fitouts.shared.context.CompanyContext;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;

@Service
public class EmployeeService {

    private static final String DEFAULT_PASSWORD = "123456";

    private final EmployeeRepository employeeRepository;
    private final AccountRepository accountRepository;
    private final PasswordEncoder passwordEncoder;
    private final CompanyService companyService;

    public EmployeeService(EmployeeRepository employeeRepository,
                           AccountRepository accountRepository,
                           PasswordEncoder passwordEncoder,
                           CompanyService companyService) {
        this.employeeRepository = employeeRepository;
        this.accountRepository = accountRepository;
        this.passwordEncoder = passwordEncoder;
        this.companyService = companyService;
    }

    @Transactional
    public EmployeeResponse create(EmployeeCreateRequest request) {
        String email = request.getEmail().trim().toLowerCase();

        if (employeeRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new ConflictException("Employee with this email already exists");
        }

        Employee employee = new Employee();
        employee.setEmployeeName(request.getEmployeeName().trim());
        employee.setEmail(email);
        employee.setPhone(request.getPhone());
        employee.setDesignation(request.getDesignation().trim());
        if (request.getFeatures() != null) {
            employee.setFeatures(new HashSet<>(request.getFeatures()));
        }
        employee.setIsActive(true);
        employee.setIsDeleted(false);
        employee.setCreatedAt(LocalDateTime.now());
        employee.setUpdatedAt(LocalDateTime.now());

        // Set company from context
        var companyId = CompanyContext.get();
        if (companyId != null) {
            employee.setCompany(companyService.getCompany(companyId));
        }

        Employee saved = employeeRepository.save(employee);

        // Auto-create Account (User) for the employee
        Account account = new Account();
        account.setFullName(request.getEmployeeName().trim());
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        account.setPhone(request.getPhone());
        account.setIsActive(true);

        Set<Role> roles = new HashSet<>();
        roles.add(Role.EMPLOYEE);
        account.setRoles(roles);

        if (companyId != null) {
            account.setCompany(companyService.getCompany(companyId));
        }

        Account savedAccount = accountRepository.save(account);

        // Link account back to employee
        saved.setAccountId(savedAccount.getId());
        employeeRepository.save(saved);

        return toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<EmployeeResponse> getAll() {
        var companyId = CompanyContext.get();
        if (companyId != null) {
            return employeeRepository.findByCompanyAndIsDeletedFalse(companyService.getCompany(companyId))
                    .stream()
                    .map(this::toResponse)
                    .toList();
        }
        return employeeRepository.findByCompanyAndIsDeletedFalse(null)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public EmployeeResponse getById(Long id) {
        Employee employee = employeeRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> new NotFoundException("Employee not found"));
        return toResponse(employee);
    }

    @Transactional
    public EmployeeResponse update(Long id, EmployeeUpdateRequest request) {
        Employee employee = employeeRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        employee.setEmployeeName(request.getEmployeeName().trim());
        employee.setPhone(request.getPhone());
        employee.setDesignation(request.getDesignation().trim());
        if (request.getFeatures() != null) {
            employee.setFeatures(new HashSet<>(request.getFeatures()));
        }
        employee.setUpdatedAt(LocalDateTime.now());

        if (request.getActive() != null) {
            employee.setIsActive(request.getActive());
            // Sync active status with linked account
            if (employee.getAccountId() != null) {
                accountRepository.findById(employee.getAccountId()).ifPresent(account -> {
                    account.setIsActive(request.getActive());
                    accountRepository.save(account);
                });
            }
        }

        return toResponse(employeeRepository.save(employee));
    }

    @Transactional
    public void delete(Long id) {
        Employee employee = employeeRepository.findById(id)
                .filter(e -> !e.getIsDeleted())
                .orElseThrow(() -> new NotFoundException("Employee not found"));

        employee.setIsDeleted(true);
        employee.setIsActive(false);
        employee.setUpdatedAt(LocalDateTime.now());
        employeeRepository.save(employee);

        // Deactivate linked account
        if (employee.getAccountId() != null) {
            accountRepository.findById(employee.getAccountId()).ifPresent(account -> {
                account.setIsActive(false);
                accountRepository.save(account);
            });
        }
    }

    private EmployeeResponse toResponse(Employee employee) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .employeeName(employee.getEmployeeName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .designation(employee.getDesignation())
                .features(employee.getFeatures())
                .active(employee.getIsActive())
                .accountId(employee.getAccountId())
                .createdAt(employee.getCreatedAt())
                .build();
    }
}
