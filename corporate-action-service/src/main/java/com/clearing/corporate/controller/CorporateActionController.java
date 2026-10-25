package com.clearing.corporate.controller;

import com.clearing.corporate.model.CorporateAction;
import com.clearing.corporate.model.CorporateActionStatus;
import com.clearing.corporate.service.CorporateActionService;
import com.clearing.shared.events.CorporateActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/corporate-actions")
public class CorporateActionController {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionController.class);

    private final CorporateActionService corporateActionService;

    public CorporateActionController(CorporateActionService corporateActionService) {
        this.corporateActionService = corporateActionService;
    }

    /**
     * POST /api/v1/corporate-actions
     * Submit a new corporate action (dividend or stock split).
     */
    @PostMapping
    public ResponseEntity<CorporateAction> submit(
            @RequestParam String instrumentId,
            @RequestParam CorporateActionType actionType,
            @RequestParam BigDecimal actionValue) {
        log.info("POST /api/v1/corporate-actions instrument={}, type={}", instrumentId, actionType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(corporateActionService.submit(instrumentId, actionType, actionValue));
    }

    /**
     * GET /api/v1/corporate-actions/{corporateActionId}
     * Look up a specific corporate action by its ID.
     */
    @GetMapping("/{corporateActionId}")
    public ResponseEntity<CorporateAction> getById(@PathVariable String corporateActionId) {
        log.info("GET /api/v1/corporate-actions/{}", corporateActionId);
        return corporateActionService.findByCorporateActionId(corporateActionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * GET /api/v1/corporate-actions/instruments/{instrumentId}
     * All corporate actions affecting a specific instrument.
     */
    @GetMapping("/instruments/{instrumentId}")
    public ResponseEntity<List<CorporateAction>> getByInstrumentId(@PathVariable String instrumentId) {
        log.info("GET /api/v1/corporate-actions/instruments/{}", instrumentId);
        return ResponseEntity.ok(corporateActionService.findByInstrumentId(instrumentId));
    }

    /**
     * GET /api/v1/corporate-actions
     * Find corporate actions by status or get all if status is omitted.
     */
    @GetMapping
    public ResponseEntity<List<CorporateAction>> getCorporateActions(
            @RequestParam(required = false) CorporateActionStatus status) {
        if (status != null) {
            log.info("GET /api/v1/corporate-actions?status={}", status);
            return ResponseEntity.ok(corporateActionService.findByStatus(status));
        } else {
            log.info("GET /api/v1/corporate-actions");
            return ResponseEntity.ok(corporateActionService.findAll());
        }
    }
}
