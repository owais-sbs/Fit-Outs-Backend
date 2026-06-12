package com.fitouts.employee.api;

import java.util.Set;

import com.fitouts.employee.domain.Feature;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class EmployeeCreateRequest {

    @NotBlank(message = "Employee name is required")
    private String employeeName;

    @NotBlank(message = "Email is required")
    @Email(message = "Invalid email format")
    private String email;

    private String phone;

    @NotBlank(message = "Designation is required")
    private String designation;

    private Set<Feature> features;
}
