package com.fitouts.lead.api;

import com.fitouts.lead.application.LeadService;
import com.fitouts.lead.domain.*;
import com.fitouts.shared.web.BaseController;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping({"/api/lead", "/api/leads"})
public class LeadController extends BaseController {

    private final LeadService leadService;

    public LeadController(LeadService leadService) {
        this.leadService = leadService;
    }

    // CREATE
    @PostMapping
    public Object create(@RequestBody Lead request) {

        try {

            return successResponse(
                    leadService.create(request)
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to create lead",
                    e.getMessage()
            );
        }
    }

    // FILTER + PAGINATION
    @PostMapping("/filter")
    public Object getAll(@RequestBody LeadFilterDTO filter,
                         @RequestParam(defaultValue = "0") int page,
                         @RequestParam(defaultValue = "10") int size) {

        try {

            return successResponse(
                    leadService.getAll(filter, page, size)
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to fetch leads",
                    e.getMessage()
            );
        }
    }

    // UPDATE STATUS
    @PutMapping("/{id}/status")
    public Object updateStatus(@PathVariable Long id,
                               @RequestParam LeadStatus status,
                               @RequestParam Long updatedBy,
                               @RequestParam(required = false) String notes,
                               @RequestParam(required = false) String lostReason) {

        try {

            return successResponse(
                    leadService.updateStatus(
                            id,
                            status,
                            updatedBy,
                            notes,
                            lostReason
                    )
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to update status",
                    e.getMessage()
            );
        }
    }

    // GET BY ID
    @GetMapping("/{id}")
    public Object getById(@PathVariable Long id) {

        try {

            return successResponse(
                    leadService.getById(id)
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to fetch lead",
                    e.getMessage()
            );
        }
    }

    // DELETE
    @DeleteMapping("/{id}")
    public Object delete(@PathVariable Long id) {

        try {

            return successResponse(
                    leadService.delete(id)
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to delete lead",
                    e.getMessage()
            );
        }
    }
}