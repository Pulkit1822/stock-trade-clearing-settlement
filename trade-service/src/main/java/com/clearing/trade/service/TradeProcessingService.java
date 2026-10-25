package com.clearing.trade.service;

import com.clearing.shared.dto.TradeRequest;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.trade.kafka.TradeEventPublisher;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
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
    private final TradeEventPublisher tradeEventPublisher;

    public TradeProcessingService(TradeRepository tradeRepository, TradeEventPublisher tradeEventPublisher) {
        this.tradeRepository = tradeRepository;
        this.tradeEventPublisher = tradeEventPublisher;
    }

    /**
     * Processes an incoming trade request.
     * Enforced by strict @Transactional boundary: If Kafka send fails (non-async exception)
     * or DB write fails, the entire transaction rolls back cleanly.
     * 
     * @param request The inbound TradeRequest payload containing matched execution details.
     * @return The persisted Trade entity.
     */
    @Transactional
    public Trade processTradeRequest(TradeRequest request) {
        log.info("Processing new TradeRequest for Instrument [{}] between Buyer [{}] and Seller [{}]", 
                 request.instrumentId(), request.buyerAccountId(), request.sellerAccountId());

        // 1. Generate core identifying data
        UUID generatedTradeId = UUID.randomUUID(); // A UUID string equivalent logic is needed
        Instant now = Instant.now();
        BigDecimal quantityDecimal = new BigDecimal(request.quantity());

        Trade newTrade = new Trade(
                UUID.randomUUID(), 
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

        // 4. Publish Event to Kafka.
        // It's inside the transaction boundary to ensure atomicity via Kafka's integration (if 2PC is configured)
        // Since we are using basic async Kafka template, the message is dispatched after or during commit.
        tradeEventPublisher.publishTradeSubmitted(event);
        log.info("Successfully handed TradeSubmittedEvent [{}] over to the event publisher.", event.tradeId());

        return savedTrade;
    }
}
