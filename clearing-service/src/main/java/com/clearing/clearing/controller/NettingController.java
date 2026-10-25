package com.clearing.clearing.controller;

import com.clearing.clearing.service.NettingEngineService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/netting")
public class NettingController {

    private final NettingEngineService nettingEngineService;

    public NettingController(NettingEngineService nettingEngineService) {
        this.nettingEngineService = nettingEngineService;
    }

    /**
     * Triggers the End-of-Day Multilateral Netting cycle.
     * Optionally accepts a businessDate in format YYYY-MM-DD. Defaults to today.
     */
    @PostMapping("/trigger")
    public ResponseEntity<Object> triggerNetting(@RequestParam(required = false) String businessDate) {
        String effectiveDate = businessDate;
        if (effectiveDate == null || effectiveDate.isEmpty()) {
            effectiveDate = LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE);
        }

        try {
            nettingEngineService.executeEodNetting(effectiveDate);
            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "businessDate", effectiveDate,
                    "message", "EOD Netting completed successfully."
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "businessDate", effectiveDate,
                    "message", "Failed to run netting: " + e.getMessage()
            ));
        }
    }
}
