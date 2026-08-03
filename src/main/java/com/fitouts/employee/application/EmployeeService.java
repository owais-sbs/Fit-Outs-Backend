package com.fitouts.employee.application;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

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
import com.fitouts.shared.error.BadRequestException;
import com.fitouts.shared.error.ConflictException;
import com.fitouts.shared.error.NotFoundException;

@Service
public class EmployeeService {

    private static final String DEFAULT_PASSWORD = "123456";

    private static final Set<Role> STAFF_CREATE_ROLES = EnumSet.of(
            Role.PROJECT_MANAGER,
            Role.BUSINESS_OWNER,
            Role.QS,
            Role.DESIGNER,
            Role.SITE_ENGINEER,
            Role.FINANCE,
            Role.SALES
    );

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
        Role role = requireStaffRole(request.getRole());

        if (employeeRepository.existsByEmailAndIsDeletedFalse(email)) {
            throw new ConflictException("Employee with this email already exists");
        }
        if (accountRepository.findByEmail(email).isPresent()) {
            throw new ConflictException("An account with this email already exists");
        }

        String designation = StringUtils.hasText(request.getDesignation())
                ? request.getDesignation().trim()
                : staffRoleLabel(role);

        Employee employee = new Employee();
        employee.setEmployeeName(request.getEmployeeName().trim());
        employee.setEmail(email);
        employee.setPhone(request.getPhone());
        employee.setDesignation(designation);
        if (request.getFeatures() != null) {
            employee.setFeatures(new HashSet<>(request.getFeatures()));
        }
        employee.setIsActive(true);
        employee.setIsDeleted(false);
        employee.setCreatedAt(LocalDateTime.now());
        employee.setUpdatedAt(LocalDateTime.now());

        var companyId = CompanyContext.get();
        if (companyId != null) {
            employee.setCompany(companyService.getCompany(companyId));
        }

        Employee saved = employeeRepository.save(employee);

        Account account = new Account();
        account.setFullName(request.getEmployeeName().trim());
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        account.setPhone(request.getPhone());
        account.setIsActive(true);
        account.setRoles(new HashSet<>(Set.of(role)));

        if (companyId != null) {
            account.setCompany(companyService.getCompany(companyId));
        }

        Account savedAccount = accountRepository.save(account);

        saved.setAccountId(savedAccount.getId());
        employeeRepository.save(saved);

        return toResponse(saved, role);
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
            if (employee.getAccountId() != null) {
                accountRepository.findById(employee.getAccountId()).ifPresent(account -> {
                    account.setIsActive(request.getActive());
                    accountRepository.save(account);
                });
            }
        }

        Role syncedRole = null;
        if (request.getRole() != null) {
            syncedRole = requireStaffRole(request.getRole());
            if (employee.getAccountId() != null) {
                Role roleToApply = syncedRole;
                accountRepository.findById(employee.getAccountId()).ifPresent(account -> {
                    account.setRoles(new HashSet<>(Set.of(roleToApply)));
                    account.setFullName(request.getEmployeeName().trim());
                    accountRepository.save(account);
                });
            }
        }

        Employee saved = employeeRepository.save(employee);
        return syncedRole != null ? toResponse(saved, syncedRole) : toResponse(saved);
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

        if (employee.getAccountId() != null) {
            accountRepository.findById(employee.getAccountId()).ifPresent(account -> {
                account.setIsActive(false);
                accountRepository.save(account);
            });
        }
    }

    private Role requireStaffRole(Role role) {
        if (role == null || !STAFF_CREATE_ROLES.contains(role)) {
            throw new BadRequestException(
                    "Role must be one of: PROJECT_MANAGER, BUSINESS_OWNER, QS, DESIGNER, SITE_ENGINEER, FINANCE, SALES");
        }
        return role;
    }

    private static String staffRoleLabel(Role role) {
        return switch (role) {
            case PROJECT_MANAGER -> "Project Manager";
            case BUSINESS_OWNER -> "Project Director";
            case QS -> "Quantity Surveyor";
            case DESIGNER -> "Designer";
            case SITE_ENGINEER -> "Site Engineer";
            case FINANCE -> "Finance / Accounts";
            case SALES -> "Sales";
            default -> role.name();
        };
    }

    private EmployeeResponse toResponse(Employee employee) {
        Role role = null;
        if (employee.getAccountId() != null) {
            role = accountRepository.findById(employee.getAccountId())
                    .flatMap(account -> account.getRoles().stream().findFirst())
                    .orElse(null);
        }
        return toResponse(employee, role);
    }

    private EmployeeResponse toResponse(Employee employee, Role role) {
        return EmployeeResponse.builder()
                .id(employee.getId())
                .employeeName(employee.getEmployeeName())
                .email(employee.getEmail())
                .phone(employee.getPhone())
                .designation(employee.getDesignation())
                .role(role)
                .features(employee.getFeatures())
                .active(employee.getIsActive())
                .accountId(employee.getAccountId())
                .createdAt(employee.getCreatedAt())
                .build();
    }
}
