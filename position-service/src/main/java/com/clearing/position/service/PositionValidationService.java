package com.clearing.position.service;

import com.clearing.position.model.Position;
import com.clearing.position.repository.PositionRepository;
import com.clearing.shared.events.PositionUpdatedEvent;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.shared.events.TradeValidatedEvent;
import com.clearing.shared.events.TradeValidationStatus;
import com.clearing.position.outbox.OutboxRepository;
import com.clearing.position.model.ProcessedEvent;
import com.clearing.position.repository.ProcessedEventRepository;
import com.clearing.shared.outbox.OutboxEvent;
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
public class PositionValidationService {

    private static final Logger log = LoggerFactory.getLogger(PositionValidationService.class);

    private final PositionRepository positionRepository;
    private final OutboxRepository outboxRepository;
    private final ProcessedEventRepository processedEventRepository;
    private final ObjectMapper objectMapper;

    public PositionValidationService(PositionRepository positionRepository,
                                     OutboxRepository outboxRepository,
                                     ProcessedEventRepository processedEventRepository,
                                     ObjectMapper objectMapper) {
        this.positionRepository = positionRepository;
        this.outboxRepository = outboxRepository;
        this.processedEventRepository = processedEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Executes the strict validation check upon receiving a new trade explicitly.
     * 
     * CRITICAL STAFF ENGINEERING: 
     * To prevent circular wait deadlocks, we enforce a deterministic alphabetical order
     * for acquiring pessimistic locks. Regardless of the trade's semantic direction 
     * (who is buyer/seller), the account with the smaller ID is ALWAYS locked first.
     */
    @Transactional
    public void validateAndProcessTrade(TradeSubmittedEvent tradeEvent) {
        if (processedEventRepository.existsById(tradeEvent.tradeId())) {
            log.warn("Duplicate TradeSubmittedEvent received for tradeId [{}]. Skipping (idempotent).", tradeEvent.tradeId());
            return;
        }
        processedEventRepository.save(new ProcessedEvent(tradeEvent.tradeId(), Instant.now()));

        log.info("Processing Trade [{}] for Instrument [{}]. Lock ordering initialization...", 
                 tradeEvent.tradeId(), tradeEvent.instrumentId());

        final String buyerId = tradeEvent.buyerAccountId();
        final String sellerId = tradeEvent.sellerAccountId();
        final String instrumentId = tradeEvent.instrumentId();
        final BigDecimal quantity = new BigDecimal(tradeEvent.quantity());

        // 1. Determine locking order
        final boolean isSelfTrade = buyerId.equals(sellerId);
        final String firstId = buyerId.compareTo(sellerId) < 0 ? buyerId : sellerId;
        final String secondId = firstId.equals(buyerId) ? sellerId : buyerId;

        Position firstPos = getOrCreatePosition(firstId, instrumentId);
        Position secondPos = isSelfTrade ? firstPos : getOrCreatePosition(secondId, instrumentId);

        // 3. Map semantic roles back to the locked entities
        Position buyerPos = buyerId.equals(firstId) ? firstPos : secondPos;
        Position sellerPos = sellerId.equals(firstId) ? firstPos : secondPos;

        // 4. Validate Seller's sufficient quantity
        if (sellerPos.getQuantity().compareTo(quantity) < 0) {
            log.warn("Trade [{}] REJECTED: Insufficient position in Seller Account [{}]. Balance: {}, Required: {}", 
                     tradeEvent.tradeId(), sellerId, sellerPos.getQuantity(), quantity);
            
            TradeValidatedEvent rejectEvent = new TradeValidatedEvent(
                    tradeEvent.tradeId(),
                    TradeValidationStatus.REJECTED,
                    "Insufficient position in Seller account",
                    Instant.now(),
                    null,
                    null
            );
            try {
                outboxRepository.save(new OutboxEvent(
                        "TradeValidation", tradeEvent.tradeId(), "TradeValidatedEvent", objectMapper.writeValueAsString(rejectEvent)
                ));
                log.info("Successfully persisted rejection event to Outbox for Trade [{}]", tradeEvent.tradeId());
            } catch (JsonProcessingException e) {
                log.error("Failed to serialize rejectEvent for Outbox: {}", e.getMessage(), e);
                throw new RuntimeException("Failed to serialize rejectEvent", e);
            }
            return;
        }

        // 5. Atomic Debit & Credit
        sellerPos.setQuantity(sellerPos.getQuantity().subtract(quantity));
        buyerPos.setQuantity(buyerPos.getQuantity().add(quantity));

        // Save (Persistence happens at TX commit, but we call save to trigger potential flush/validation)
        positionRepository.save(sellerPos);
        if (!isSelfTrade) {
            positionRepository.save(buyerPos);
        }

        log.info("Ledger updated. Trade [{}] SETTLED. [{} -> {} | Qty: {}]",
                 tradeEvent.tradeId(), sellerId, buyerId, quantity);

        // 6. Publish PositionUpdatedEvent for each affected account side.
        //    ClearingService consumes these to confirm buyer + seller and mark settlement SETTLED.
        Instant now = Instant.now();
        PositionUpdatedEvent sellerEvent = new PositionUpdatedEvent(
                sellerId,
                instrumentId,
                quantity.negate().intValue(),          // negative = debit
                sellerPos.getQuantity().intValue(),
                tradeEvent.tradeId(),
                now
        );
        PositionUpdatedEvent buyerEvent = new PositionUpdatedEvent(
                buyerId,
                instrumentId,
                quantity.intValue(),                   // positive = credit
                buyerPos.getQuantity().intValue(),
                tradeEvent.tradeId(),
                now
        );
        TradeValidatedEvent successEvent = new TradeValidatedEvent(
                tradeEvent.tradeId(),
                TradeValidationStatus.VALIDATED,
                null,
                Instant.now(),
                tradeEvent.quantity(),
                tradeEvent.price()
        );

        try {
            outboxRepository.save(new OutboxEvent(
                    "Position", sellerId, "PositionUpdatedEvent", objectMapper.writeValueAsString(sellerEvent)
            ));
            outboxRepository.save(new OutboxEvent(
                    "Position", buyerId, "PositionUpdatedEvent", objectMapper.writeValueAsString(buyerEvent)
            ));
            outboxRepository.save(new OutboxEvent(
                    "TradeValidation", tradeEvent.tradeId(), "TradeValidatedEvent", objectMapper.writeValueAsString(successEvent)
            ));
            log.info("Successfully persisted outbox events for Trade [{}]", tradeEvent.tradeId());
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize events for Outbox: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to serialize events for Outbox", e);
        }
    }

    private Position getOrCreatePosition(String accountId, String instrumentId) {
        return positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId)
                .orElseGet(() -> {
                    log.debug("Initializing new position record for Account [{}] / Instrument [{}]", accountId, instrumentId);
                    return new Position(UUID.randomUUID(), accountId, instrumentId, BigDecimal.ZERO);
                });
    }
}
