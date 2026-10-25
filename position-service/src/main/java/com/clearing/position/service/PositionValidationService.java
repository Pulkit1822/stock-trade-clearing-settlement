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
     * 
     * CRITICAL STAFF ENGINEERING: 
     * To prevent circular wait deadlocks, we enforce a deterministic alphabetical order
     * for acquiring pessimistic locks. Regardless of the trade's semantic direction 
     * (who is buyer/seller), the account with the smaller ID is ALWAYS locked first.
     */
    @Transactional
    public void validateAndProcessTrade(TradeSubmittedEvent tradeEvent) {
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
                    Instant.now()
            );
            validationPublisher.publishValidationResult(rejectEvent);
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

        // 6. Emit success event
        TradeValidatedEvent successEvent = new TradeValidatedEvent(
                tradeEvent.tradeId(),
                TradeValidationStatus.VALIDATED,
                null,
                Instant.now()
        );
        validationPublisher.publishValidationResult(successEvent);
    }

    private Position getOrCreatePosition(String accountId, String instrumentId) {
        return positionRepository.findByAccountIdAndInstrumentId(accountId, instrumentId)
                .orElseGet(() -> {
                    log.debug("Initializing new position record for Account [{}] / Instrument [{}]", accountId, instrumentId);
                    return new Position(UUID.randomUUID(), accountId, instrumentId, BigDecimal.ZERO);
                });
    }
}
