package com.fitouts.lead.api;

import com.fitouts.lead.application.LeadStatusHistoryService;
import com.fitouts.shared.web.BaseController;

import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lead-status-history")
public class LeadStatusHistoryController
        extends BaseController {

    private final LeadStatusHistoryService service;

    public LeadStatusHistoryController(
            LeadStatusHistoryService service
    ) {
        this.service = service;
    }

    // GET STATUS HISTORY BY LEAD ID
    @GetMapping("/{leadId}")
    public Object getByLeadId(
            @PathVariable Long leadId
    ) {

        try {

            return successResponse(
                    service.getByLeadId(leadId)
            );

        } catch (Exception e) {

            return failureResponse(
                    "Failed to fetch lead history",
                    e.getMessage()
            );
        }
    }
}