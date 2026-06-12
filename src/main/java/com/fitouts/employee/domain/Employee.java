package com.fitouts.employee.domain;

import com.fitouts.company.domain.Company;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "employees")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "employee_name", nullable = false)
    private String employeeName;

    @Column(nullable = false, unique = true)
    private String email;

    private String phone;

    private String designation;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "employee_features", joinColumns = @JoinColumn(name = "employee_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "feature", nullable = false)
    private Set<Feature> features = new HashSet<>();

    @Column(nullable = false)
    private Boolean isActive = true;

    @Column(nullable = false)
    private Boolean isDeleted = false;

    @Column(name = "account_id")
    private Long accountId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
