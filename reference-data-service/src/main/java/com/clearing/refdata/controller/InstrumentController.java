package com.clearing.refdata.controller;

import com.clearing.refdata.model.Instrument;
import com.clearing.refdata.model.InstrumentStatus;
import com.clearing.refdata.service.InstrumentService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/instruments")
public class InstrumentController {

    private static final Logger log = LoggerFactory.getLogger(InstrumentController.class);

    private final InstrumentService instrumentService;

    public InstrumentController(InstrumentService instrumentService) {
        this.instrumentService = instrumentService;
    }

    /** GET /instruments — list all or filter by status */
    @GetMapping
    public ResponseEntity<List<Instrument>> getAll(
            @RequestParam(required = false) InstrumentStatus status) {
        log.info("GET /instruments status={}", status);
        List<Instrument> result = status != null
                ? instrumentService.findByStatus(status)
                : instrumentService.findAll();
        return ResponseEntity.ok(result);
    }

    /** GET /instruments/{instrumentId} — single instrument lookup (Redis cached) */
    @GetMapping("/{instrumentId}")
    public ResponseEntity<Instrument> getById(@PathVariable String instrumentId) {
        log.info("GET /instruments/{}", instrumentId);
        return instrumentService.findByInstrumentId(instrumentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /instruments — create new instrument */
    @PostMapping
    public ResponseEntity<Instrument> create(
            @RequestParam String instrumentId,
            @RequestParam String name,
            @RequestParam String assetClass,
            @RequestParam String currency) {
        log.info("POST /instruments instrumentId={}", instrumentId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(instrumentService.create(instrumentId, name, assetClass, currency));
    }

    /** PATCH /instruments/{instrumentId} — update instrument details */
    @PatchMapping("/{instrumentId}")
    public ResponseEntity<Instrument> update(
            @PathVariable String instrumentId,
            @RequestParam String name,
            @RequestParam String assetClass,
            @RequestParam String currency) {
        log.info("PATCH /instruments/{}", instrumentId);
        return instrumentService.update(instrumentId, name, assetClass, currency)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /instruments/{instrumentId}/deactivate — deactivate instrument */
    @PatchMapping("/{instrumentId}/deactivate")
    public ResponseEntity<Instrument> deactivate(@PathVariable String instrumentId) {
        log.info("PATCH /instruments/{}/deactivate", instrumentId);
        return instrumentService.deactivate(instrumentId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
