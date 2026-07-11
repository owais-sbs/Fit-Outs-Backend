package com.fitouts.procurement.api;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.fitouts.shared.api.BaseController;
import com.fitouts.procurement.application.StockService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/stock")
@RequiredArgsConstructor
public class StockController extends BaseController {

    private final StockService stockService;

    @GetMapping
    public ResponseEntity<?> balances() {
        try {
            return successResponse(stockService.listBalances());
        } catch (Exception e) {
            return failureResponse("Failed to fetch stock balances", e.getMessage());
        }
    }

    @GetMapping("/movements")
    public ResponseEntity<?> movements(@RequestParam(defaultValue = "0") int page,
                                       @RequestParam(defaultValue = "50") int size) {
        try {
            return successResponse(stockService.listMovements(page, size));
        } catch (Exception e) {
            return failureResponse("Failed to fetch movements", e.getMessage());
        }
    }

    @PostMapping("/receipt")
    public ResponseEntity<?> receipt(@RequestBody StockReceiptRequest request) {
        try {
            return successResponse("Stock received", stockService.receipt(request));
        } catch (Exception e) {
            return failureResponse("Failed to record receipt", e.getMessage());
        }
    }

    @PostMapping("/issue")
    public ResponseEntity<?> issue(@RequestBody StockIssueRequest request) {
        try {
            return successResponse("Stock issued", stockService.issue(request));
        } catch (Exception e) {
            return failureResponse("Failed to record issue", e.getMessage());
        }
    }

    @PostMapping("/adjust")
    public ResponseEntity<?> adjust(@RequestBody StockAdjustmentRequest request) {
        try {
            return successResponse("Stock adjusted", stockService.adjust(request));
        } catch (Exception e) {
            return failureResponse("Failed to adjust stock", e.getMessage());
        }
    }
}
