package com.clearing.position.controller;

import com.clearing.position.model.Position;
import com.clearing.position.repository.PositionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

/**
 * Controller explicitly created to artificialy bootstrap the ledger databases.
 * In a real production environment, funding originates from Corporate Actions 
 * or Cash Settlement engines.
 */
@RestController
@RequestMapping("/api/v1/positions")
public class PositionController {

    private static final Logger log = LoggerFactory.getLogger(PositionController.class);

    private final PositionRepository positionRepository;

    public PositionController(PositionRepository positionRepository) {
        this.positionRepository = positionRepository;
    }

    // Quick DTO explicitly for testing deposits
    public record FundRequest(String accountId, String instrumentId, BigDecimal quantity) {}

    /**
     * Endpoint utilized strictly to fund / seed initial balances so trades can be verified.
     */
    @PostMapping("/fund")
    @Transactional
    public ResponseEntity<Position> fundAccount(@RequestBody FundRequest request) {
        log.info("Received request to digitally fund Account [{}] with [{}] shares of [{}]", 
                 request.accountId(), request.quantity(), request.instrumentId());
                 
        Optional<Position> currentOpt = positionRepository.findByAccountIdAndInstrumentId(
                request.accountId(), request.instrumentId());

        Position holding;
        if (currentOpt.isPresent()) {
            holding = currentOpt.get();
            holding.setQuantity(holding.getQuantity().add(request.quantity()));
        } else {
            holding = new Position(UUID.randomUUID(), request.accountId(), request.instrumentId(), request.quantity());
        }

        Position updated = positionRepository.save(holding);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(updated);
    }
}
