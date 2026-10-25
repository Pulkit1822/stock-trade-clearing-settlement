package com.clearing.position.service;

import com.clearing.position.kafka.TradeValidationPublisher;
import com.clearing.position.model.Position;
import com.clearing.position.repository.PositionRepository;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.shared.events.TradeValidatedEvent;
import com.clearing.shared.events.TradeValidationStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PositionValidationService {

    private static final Logger log = LoggerFactory.getLogger(PositionValidationService.class);

    private final PositionRepository positionRepository;
    private final TradeValidationPublisher validationPublisher;

    public PositionValidationService(PositionRepository positionRepository, TradeValidationPublisher validationPublisher) {
        this.positionRepository = positionRepository;
        this.validationPublisher = validationPublisher;
    }

    /**
     * Executes the strict validation check upon receiving a new trade explicitly.
     * The @Transactional boundary combined with PESSIMISTIC_WRITE lock on the DB retrieval
     * guarantees no two trades can deduct from the same seller's position concurrently.
     */
    @Transactional
    public void validateAndProcessTrade(TradeSubmittedEvent tradeEvent) {
        log.info("Validating funds and shares for Trade [{}]", tradeEvent.tradeId());

        BigDecimal requestedQuantity = new BigDecimal(tradeEvent.quantity());

        // 1. Lock and retrieve Seller's Position first
        Optional<Position> sellerPositionOpt = positionRepository.findByAccountIdAndInstrumentId(
                tradeEvent.sellerAccountId(), tradeEvent.instrumentId());

        if (sellerPositionOpt.isEmpty()) {
            rejectTrade(tradeEvent, "Seller has no open position for instrument " + tradeEvent.instrumentId());
            return;
        }

        Position sellerPosition = sellerPositionOpt.get();

        // 2. Validate sufficient quantity
        if (sellerPosition.getQuantity().compareTo(requestedQuantity) < 0) {
            rejectTrade(tradeEvent, "Insufficient position. Seller holds " + sellerPosition.getQuantity() + ", requested " + requestedQuantity);
            return;
        }

        // 3. Subtract from Seller
        sellerPosition.setQuantity(sellerPosition.getQuantity().subtract(requestedQuantity));
        positionRepository.save(sellerPosition);

        // 4. Lock and retrieve/create Buyer's Position
        // Note: In high traffic, always lock accounts in a consistent order (e.g. alphabetical) to prevent deadlocks.
        // For simplicity in this core PoC, we lock sequentially based on trade structure (seller then buyer).
        Position buyerPosition = positionRepository.findByAccountIdAndInstrumentId(
                tradeEvent.buyerAccountId(), tradeEvent.instrumentId())
                .orElseGet(() -> new Position(UUID.randomUUID(), tradeEvent.buyerAccountId(), tradeEvent.instrumentId(), BigDecimal.ZERO));

        // 5. Add to Buyer
        buyerPosition.setQuantity(buyerPosition.getQuantity().add(requestedQuantity));
        positionRepository.save(buyerPosition);

        log.info("Ledger updated successfully for Trade [{}]. Seller deducted: {}, Buyer credited: {}", 
                 tradeEvent.tradeId(), requestedQuantity, requestedQuantity);

        // 6. Emit definitive VALIDATED network event
        TradeValidatedEvent successEvent = new TradeValidatedEvent(
                tradeEvent.tradeId(),
                TradeValidationStatus.VALIDATED,
                null,
                Instant.now()
        );
        validationPublisher.publishValidationResult(successEvent);
    }

    private void rejectTrade(TradeSubmittedEvent tradeEvent, String reason) {
        log.warn("Trade [{}] REJECTED: {}", tradeEvent.tradeId(), reason);
        TradeValidatedEvent rejectEvent = new TradeValidatedEvent(
                tradeEvent.tradeId(),
                TradeValidationStatus.REJECTED,
                reason,
                Instant.now()
        );
        validationPublisher.publishValidationResult(rejectEvent);
    }
}
