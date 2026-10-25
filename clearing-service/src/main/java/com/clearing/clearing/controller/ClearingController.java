package com.clearing.clearing.controller;

import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import com.clearing.clearing.service.ClearingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/clearing")
public class ClearingController {

    private static final Logger log = LoggerFactory.getLogger(ClearingController.class);

    private final ClearingService clearingService;

    public ClearingController(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    /**
     * GET /api/v1/clearing/settlements/{tradeId}
     * Returns the settlement record for a specific trade.
     */
    @GetMapping("/settlements/{tradeId}")
    public ResponseEntity<Settlement> getByTradeId(@PathVariable String tradeId) {
        log.info("GET /api/v1/clearing/settlements/{}", tradeId);
        return clearingService.findByTradeId(tradeId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/clearing/settlements?status=PENDING
     * Returns all settlements in a given status.
     */
    @GetMapping("/settlements")
    public ResponseEntity<List<Settlement>> getByStatus(@RequestParam SettlementStatus status) {
        log.info("GET /api/v1/clearing/settlements?status={}", status);
        return ResponseEntity.ok(clearingService.findByStatus(status));
    }

    /**
     * GET /api/v1/clearing/accounts/{accountId}/settlements
     * Returns all settlements involving a participant account on either side.
     */
    @GetMapping("/accounts/{accountId}/settlements")
    public ResponseEntity<List<Settlement>> getByAccountId(@PathVariable String accountId) {
        log.info("GET /api/v1/clearing/accounts/{}/settlements", accountId);
        return ResponseEntity.ok(clearingService.findByAccountId(accountId));
    }
}
