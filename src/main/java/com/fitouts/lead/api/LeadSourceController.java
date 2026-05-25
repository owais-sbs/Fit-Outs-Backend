package com.fitouts.lead.api;

import com.fitouts.lead.application.LeadSourceService;
import com.fitouts.lead.domain.LeadSource;
import com.fitouts.shared.web.BaseController;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lead-sources")
public class LeadSourceController extends BaseController {

    private final LeadSourceService service;

    public LeadSourceController(LeadSourceService service) {
        this.service = service;
    }

    @PostMapping
    public Object create(@RequestBody LeadSource request) {

        try {

            return successResponse(
                    service.create(request)
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to create source",
                    e.getMessage()
            );
        }
    }

    @GetMapping
    public Object getAll() {

        try {

            return successResponse(
                    service.getAll()
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to fetch sources",
                    e.getMessage()
            );
        }
    }
}