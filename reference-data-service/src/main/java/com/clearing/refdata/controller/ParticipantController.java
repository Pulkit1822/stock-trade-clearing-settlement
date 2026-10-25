package com.clearing.refdata.controller;

import com.clearing.refdata.model.Participant;
import com.clearing.refdata.model.ParticipantStatus;
import com.clearing.refdata.service.ParticipantService;
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
@RequestMapping("/participants")
public class ParticipantController {

    private static final Logger log = LoggerFactory.getLogger(ParticipantController.class);

    private final ParticipantService participantService;

    public ParticipantController(ParticipantService participantService) {
        this.participantService = participantService;
    }

    /** GET /participants — list all or filter by status */
    @GetMapping
    public ResponseEntity<List<Participant>> getAll(
            @RequestParam(required = false) ParticipantStatus status) {
        log.info("GET /participants status={}", status);
        List<Participant> result = status != null
                ? participantService.findByStatus(status)
                : participantService.findAll();
        return ResponseEntity.ok(result);
    }

    /** GET /participants/{participantId} — single participant lookup (Redis cached) */
    @GetMapping("/{participantId}")
    public ResponseEntity<Participant> getById(@PathVariable String participantId) {
        log.info("GET /participants/{}", participantId);
        return participantService.findByParticipantId(participantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** POST /participants — register new participant */
    @PostMapping
    public ResponseEntity<Participant> create(
            @RequestParam String participantId,
            @RequestParam String name) {
        log.info("POST /participants participantId={}", participantId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(participantService.create(participantId, name));
    }

    /** PATCH /participants/{participantId} — update participant name */
    @PatchMapping("/{participantId}")
    public ResponseEntity<Participant> update(
            @PathVariable String participantId,
            @RequestParam String name) {
        log.info("PATCH /participants/{}", participantId);
        return participantService.update(participantId, name)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /** PATCH /participants/{participantId}/suspend — suspend participant */
    @PatchMapping("/{participantId}/suspend")
    public ResponseEntity<Participant> suspend(@PathVariable String participantId) {
        log.info("PATCH /participants/{}/suspend", participantId);
        return participantService.suspend(participantId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
