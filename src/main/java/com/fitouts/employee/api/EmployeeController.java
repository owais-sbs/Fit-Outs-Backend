package com.fitouts.employee.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import com.fitouts.employee.application.EmployeeService;
import com.fitouts.shared.api.BaseController;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/employees")
@Validated
@RequiredArgsConstructor
public class EmployeeController extends BaseController {

    private final EmployeeService employeeService;

    @PostMapping
    public ResponseEntity<?> create(@Valid @RequestBody EmployeeCreateRequest request) {
        try {
            return successResponse("Employee created successfully", employeeService.create(request));
        } catch (Exception exception) {
            return failureResponse("Unable to create employee", exception.getMessage());
        }
    }

    @PostMapping("/{id}/resend-invite")
    public ResponseEntity<?> resendInvite(@PathVariable Long id) {
        try {
            boolean sent = employeeService.resendInvite(id);
            if (sent) {
                return successResponse("Invite email sent", Map.of("inviteEmailSent", true));
            }
            return successResponse("Unable to send invite email", Map.of("inviteEmailSent", false));
        } catch (Exception exception) {
            return failureResponse("Unable to resend invite", exception.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAll() {
        try {
            List<EmployeeResponse> employees = employeeService.getAll();
            return successResponse(employees);
        } catch (Exception exception) {
            return failureResponse("Unable to fetch employees", exception.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getById(@PathVariable Long id) {
        try {
            return successResponse(employeeService.getById(id));
        } catch (Exception exception) {
            return failureResponse("Unable to fetch employee", exception.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> update(@PathVariable Long id,
                                    @Valid @RequestBody EmployeeUpdateRequest request) {
        try {
            return successResponse("Employee updated successfully", employeeService.update(id, request));
        } catch (Exception exception) {
            return failureResponse("Unable to update employee", exception.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> delete(@PathVariable Long id) {
        try {
            employeeService.delete(id);
            return successResponse("Employee deleted successfully", null);
        } catch (Exception exception) {
            return failureResponse("Unable to delete employee", exception.getMessage());
        }
    }
}
