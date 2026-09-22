package com.fitouts.profitloss.api;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.fitouts.profitloss.application.PnlCalculationService;
import com.fitouts.profitloss.application.PnlExportService;
import com.fitouts.shared.web.BaseController;

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CompanyPnlController extends BaseController {

    private final PnlCalculationService pnlCalculationService;
    private final PnlExportService pnlExportService;

    @GetMapping("/api/pnl/company")
    public Object companyPnl(@RequestParam(required = false) String yearMonth) {
        try {
            return successResponse(pnlCalculationService.getCompanyPnl(yearMonth));
        } catch (Exception e) {
            return failureResponse("Failed to load company P&L", e.getMessage());
        }
    }

    @GetMapping("/api/pnl/company/export")
    public ResponseEntity<?> exportCompany(
            @RequestParam(required = false) String yearMonth,
            @RequestParam(defaultValue = "csv") String format) {
        try {
            if ("pdf".equalsIgnoreCase(format)) {
                byte[] pdf = pnlExportService.companyPdf(yearMonth);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"company-pnl.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdf);
            }
            String csv = pnlExportService.companyCsv(yearMonth);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"company-pnl.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @GetMapping("/api/pnl/overhead-rule")
    public Object getOverheadRule() {
        try {
            return successResponse(pnlCalculationService.getOverheadRule());
        } catch (Exception e) {
            return failureResponse("Failed to load overhead rule", e.getMessage());
        }
    }

    @PutMapping("/api/pnl/overhead-rule")
    public Object upsertOverheadRule(@RequestBody OverheadRuleUpsertRequest request) {
        try {
            return successResponse(pnlCalculationService.upsertOverheadRule(request));
        } catch (Exception e) {
            return failureResponse("Failed to save overhead rule", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/pnl")
    public Object projectPnl(@PathVariable Long projectId) {
        try {
            return successResponse(pnlCalculationService.getProjectPnl(projectId));
        } catch (Exception e) {
            return failureResponse("Failed to load project P&L", e.getMessage());
        }
    }

    @GetMapping("/api/projects/{projectId}/pnl/export")
    public ResponseEntity<?> exportProject(
            @PathVariable Long projectId,
            @RequestParam(defaultValue = "csv") String format) {
        try {
            if ("pdf".equalsIgnoreCase(format)) {
                byte[] pdf = pnlExportService.projectPdf(projectId);
                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project-" + projectId + "-pnl.pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(pdf);
            }
            String csv = pnlExportService.projectCsv(projectId);
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"project-" + projectId + "-pnl.csv\"")
                    .contentType(MediaType.parseMediaType("text/csv"))
                    .body(csv);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
