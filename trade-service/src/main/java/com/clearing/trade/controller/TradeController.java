package com.clearing.trade.controller;

import com.clearing.shared.dto.TradeRequest;
import com.clearing.trade.model.Trade;
import com.clearing.trade.service.TradeProcessingService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/trades")
public class TradeController {

    private static final Logger log = LoggerFactory.getLogger(TradeController.class);

    private final TradeProcessingService tradeProcessingService;

    // Constructor injection
    public TradeController(TradeProcessingService tradeProcessingService) {
        this.tradeProcessingService = tradeProcessingService;
    }

    /**
     * Endpoint for market gateways or brokers to submit a new matched trade execution
     * into the clearing platform layer.
     * 
     * @param request Validated inbound JSON payload mapping to the strict TradeRequest DTO.
     * @return 201 Created with the persisted internal trade ID and database object state.
     */
    @PostMapping
    public ResponseEntity<Trade> submitTrade(@Valid @RequestBody TradeRequest request) {
        log.info("Received HTTP POST /api/v1/trades - incoming trade execution for instrument [{}]", request.instrumentId());
        
        // Pass strictly validated DTO down to the transaction layer
        Trade savedTrade = tradeProcessingService.processTradeRequest(request);
        
        return ResponseEntity.status(HttpStatus.CREATED).body(savedTrade);
    }
}
