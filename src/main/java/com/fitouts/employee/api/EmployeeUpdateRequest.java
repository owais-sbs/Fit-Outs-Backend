package com.fitouts.employee.api;

import java.util.Set;

import com.fitouts.auth.domain.Role;
import com.fitouts.employee.domain.Feature;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmployeeUpdateRequest {

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    private String phone;

    @NotBlank(message = "Designation is required")
    private String designation;

    private Role role;

    private Set<Feature> features;

    private Boolean active;
}
