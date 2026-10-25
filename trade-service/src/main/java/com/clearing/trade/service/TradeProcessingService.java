package com.clearing.trade.service;

import com.clearing.shared.dto.TradeRequest;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.shared.outbox.OutboxEvent;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
import com.clearing.trade.outbox.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class TradeProcessingService {

    private static final Logger log = LoggerFactory.getLogger(TradeProcessingService.class);

    private final TradeRepository tradeRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public TradeProcessingService(TradeRepository tradeRepository, 
                                  OutboxRepository outboxRepository,
                                  ObjectMapper objectMapper) {
        this.tradeRepository = tradeRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Processes an incoming trade request.
     * Uses Transactional Outbox pattern to ensure atomicity.
     * 
     * @param request The inbound TradeRequest payload containing matched execution details.
     * @return The persisted Trade entity.
     */
    @Transactional
    public Trade processTradeRequest(TradeRequest request) {
        log.info("Processing new TradeRequest for Instrument [{}] between Buyer [{}] and Seller [{}]", 
                 request.instrumentId(), request.buyerAccountId(), request.sellerAccountId());

        // 1. Generate core identifying data
        UUID generatedTradeId = UUID.randomUUID();
        Instant now = Instant.now();
        BigDecimal quantityDecimal = new BigDecimal(request.quantity());

        Trade newTrade = new Trade(
                generatedTradeId, 
                request.buyerAccountId(), 
                request.sellerAccountId(), 
                request.instrumentId(),
                quantityDecimal,
                request.price(),
                now,
                TradeStatus.SUBMITTED
        );

        // 2. Persist to Database (transactional save)
        Trade savedTrade = tradeRepository.save(newTrade);
        log.info("Successfully persisted Trade [{}] to PostgreSQL.", savedTrade.getId());

        // 3. Construct the immutable Kafka Event matching the shared library contract
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                savedTrade.getId().toString(),
                savedTrade.getBuyerAccountId(),
                savedTrade.getSellerAccountId(),
                savedTrade.getInstrumentId(),
                savedTrade.getQuantity().intValue(),
                savedTrade.getPrice(),
                savedTrade.getTradeDate()
        );

        // 4. Serialize and persist to Outbox
        try {
            String payload = objectMapper.writeValueAsString(event);
            OutboxEvent outboxEvent = new OutboxEvent(
                    "Trade", 
                    savedTrade.getId().toString(), 
                    "TradeSubmittedEvent", 
                    payload
            );
            outboxRepository.save(outboxEvent);
            log.info("Successfully saved TradeSubmittedEvent for Trade [{}] to Outbox table.", event.tradeId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize TradeSubmittedEvent: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize TradeSubmittedEvent", e);
        }

        return savedTrade;
    }
}
