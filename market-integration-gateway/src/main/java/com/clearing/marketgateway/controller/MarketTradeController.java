package com.clearing.marketgateway.controller;

import com.clearing.marketgateway.client.TradeServiceClient;
import com.clearing.marketgateway.client.TradeServiceException;
import com.clearing.marketgateway.dto.ExternalTradeRequest;
import com.clearing.marketgateway.dto.MarketTradeResponse;
import com.clearing.marketgateway.translator.TradeTranslator;
import com.clearing.shared.dto.TradeRequest;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
@RequestMapping("/api/v1/market")
public class MarketTradeController {

    private static final Logger log = LoggerFactory.getLogger(MarketTradeController.class);

    private final TradeTranslator tradeTranslator;
    private final TradeServiceClient tradeServiceClient;

    public MarketTradeController(TradeTranslator tradeTranslator,
                                  TradeServiceClient tradeServiceClient) {
        this.tradeTranslator = tradeTranslator;
        this.tradeServiceClient = tradeServiceClient;
    }

    /**
     * POST /api/v1/market/trades
     *
     * Entry point for external brokers and market venues.
     * Accepts an {@link ExternalTradeRequest}, validates it, translates it to the
     * internal format, forwards to TradeService, and returns an acknowledgement.
     *
     * @return 201 ACCEPTED with clearing trade ID on success
     * @return 400 BAD REQUEST if validation fails
     * @return 502 BAD GATEWAY if TradeService is unavailable or rejects the trade
     */
    @PostMapping("/trades")
    public ResponseEntity<?> submitTrade(@Valid @RequestBody ExternalTradeRequest request) {
        log.info("Received external trade [brokerTradeId={}, ticker={}, buyer={}, seller={}]",
                request.brokerTradeId(), request.ticker(),
                request.buyerAccount(), request.sellerAccount());

        TradeRequest internalRequest = tradeTranslator.toInternalRequest(request);

        try {
            JsonNode tradeServiceResponse = tradeServiceClient.submitTrade(internalRequest);

            String clearingTradeId = tradeServiceResponse != null
                    && tradeServiceResponse.has("id")
                    ? tradeServiceResponse.get("id").asText()
                    : "unknown";

            MarketTradeResponse response = new MarketTradeResponse(
                    clearingTradeId,
                    request.brokerTradeId(),
                    request.ticker(),
                    "ACCEPTED",
                    Instant.now()
            );

            log.info("Trade accepted [brokerTradeId={}, clearingTradeId={}]",
                    request.brokerTradeId(), clearingTradeId);

            return ResponseEntity.status(HttpStatus.CREATED).body(response);

        } catch (TradeServiceException e) {
            log.error("Trade submission failed [brokerTradeId={}]: {}",
                    request.brokerTradeId(), e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                    .body("Trade submission failed: " + e.getMessage());
        }
    }
}
