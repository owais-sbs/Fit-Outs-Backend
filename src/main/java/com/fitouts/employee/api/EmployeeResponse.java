package com.fitouts.employee.api;

import java.time.LocalDateTime;
import java.util.Set;

import com.fitouts.employee.domain.Feature;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmployeeResponse {

    private Long id;
    private String employeeName;
    private String email;
    private String phone;
    private String designation;
    private Set<Feature> features;
    private Boolean active;
    private Long accountId;
    private LocalDateTime createdAt;
}
