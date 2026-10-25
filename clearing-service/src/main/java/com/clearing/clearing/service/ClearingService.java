package com.clearing.clearing.service;

import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import com.clearing.clearing.repository.SettlementRepository;
import com.clearing.shared.events.PositionUpdatedEvent;
import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.shared.events.TradeValidatedEvent;
import com.clearing.shared.events.TradeValidationStatus;
import com.clearing.clearing.outbox.OutboxRepository;
import com.clearing.shared.outbox.OutboxEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ClearingService {

    private static final Logger log = LoggerFactory.getLogger(ClearingService.class);

    private final SettlementRepository settlementRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public ClearingService(SettlementRepository settlementRepository,
                           OutboxRepository outboxRepository,
                           ObjectMapper objectMapper) {
        this.settlementRepository = settlementRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * Handles a trade-validated event from PositionService.
     *
     * VALIDATED → creates a PENDING settlement record, awaiting position confirmations.
     * REJECTED  → creates a FAILED settlement record and stops processing.
     *
     * Idempotent: if a settlement for this tradeId already exists, the event is skipped.
     */
    @Transactional
    public void handleTradeValidated(TradeValidatedEvent event) {
        Settlement settlement = settlementRepository.findLockedByTradeId(event.tradeId())
                .orElse(null);

        if (settlement != null) {
            // It might have been created by PositionUpdatedEvent out-of-order, or we are retrying.
            // Ensure we set price and quantity in case they are missing.
            if (event.status() == TradeValidationStatus.REJECTED) {
                settlement.setStatus(SettlementStatus.FAILED);
                settlement.setFailureReason(event.rejectionReason());
            } else {
                if (settlement.getQuantity() == 0 && settlement.getPrice() == null) {
                    settlement.setQuantity(event.quantity());
                    settlement.setPrice(event.price());
                    log.info("Updated existing late-entry Settlement for tradeId [{}] with quantity {} and price {}",
                            event.tradeId(), event.quantity(), event.price());
                    if (settlement.isBuyerConfirmed() && settlement.isSellerConfirmed()) {
                        finalizeSettlement(settlement);
                    }
                } else {
                    log.warn("Duplicate TradeValidatedEvent received for tradeId [{}]. Skipping.", event.tradeId());
                }
            }
            settlementRepository.save(settlement);
            return;
        }

        if (event.status() == TradeValidationStatus.REJECTED) {
            Settlement failed = new Settlement(UUID.randomUUID(), event.tradeId(), SettlementStatus.FAILED);
            failed.setFailureReason(event.rejectionReason());
            settlementRepository.save(failed);
            log.info("Settlement created with status FAILED for tradeId [{}]. Reason: {}",
                    event.tradeId(), event.rejectionReason());
            return;
        }

        Settlement pending = new Settlement(UUID.randomUUID(), event.tradeId(), SettlementStatus.PENDING);
        pending.setQuantity(event.quantity());
        pending.setPrice(event.price());
        settlementRepository.save(pending);
        log.info("Settlement created with status PENDING for tradeId [{}] with quantity {} and price {}", 
                 event.tradeId(), event.quantity(), event.price());
    }

    /**
     * Handles a position-updated event from PositionService.
     *
     * quantityDelta > 0 = buyer side confirmed (shares credited).
     * quantityDelta < 0 = seller side confirmed (shares debited).
     *
     * When both buyer and seller sides are confirmed, the settlement is marked SETTLED
     * and a settlement-completed event is published to NotificationService.
     *
     * PESSIMISTIC_WRITE lock on findByTradeId prevents concurrent events for the same
     * trade from racing to update confirmation flags.
     */
    @Transactional
    public void handlePositionUpdated(PositionUpdatedEvent event) {
        Settlement settlement = settlementRepository.findLockedByTradeId(event.tradeId())
                .orElseGet(() -> {
                    log.warn("No settlement found for tradeId [{}] on PositionUpdatedEvent. Creating late entry.", event.tradeId());
                    return new Settlement(UUID.randomUUID(), event.tradeId(), SettlementStatus.PENDING);
                });

        if (settlement.getStatus() == SettlementStatus.SETTLED
                || settlement.getStatus() == SettlementStatus.FAILED
                || settlement.getStatus() == SettlementStatus.READY_FOR_NETTING
                || settlement.getStatus() == SettlementStatus.NETTED) {
            log.warn("PositionUpdatedEvent received for already-finalized settlement [tradeId={}, status={}]. Skipping.",
                    event.tradeId(), settlement.getStatus());
            return;
        }

        // Populate account/instrument info on first confirmation received
        if (settlement.getInstrumentId() == null) {
            settlement.setInstrumentId(event.instrumentId());
        }

        if (event.quantityDelta() > 0) {
            settlement.setBuyerAccountId(event.accountId());
            settlement.setBuyerConfirmed(true);
            log.info("Buyer side confirmed for tradeId [{}], accountId [{}]", event.tradeId(), event.accountId());
        } else {
            settlement.setSellerAccountId(event.accountId());
            settlement.setSellerConfirmed(true);
            log.info("Seller side confirmed for tradeId [{}], accountId [{}]", event.tradeId(), event.accountId());
        }

        if (settlement.isBuyerConfirmed() && settlement.isSellerConfirmed()) {
            if (settlement.getPrice() != null) {
                finalizeSettlement(settlement);
            } else {
                log.info("Both sides confirmed for tradeId [{}], but waiting for TradeValidatedEvent to provide price.", settlement.getTradeId());
            }
        }
        
        settlementRepository.save(settlement);
    }

    private void finalizeSettlement(Settlement settlement) {
        settlement.setStatus(SettlementStatus.READY_FOR_NETTING);
        // We don't set settledAt yet, because it hasn't actually settled (just ready for netting)
        settlementRepository.save(settlement);

        log.info("Settlement ready for netting for tradeId [{}]. Buyer [{}] Seller [{}]",
                settlement.getTradeId(), settlement.getBuyerAccountId(), settlement.getSellerAccountId());

        // We no longer publish SettlementCompletedEvent here because we use Multilateral Netting now.
    }

    @Transactional(readOnly = true)
    public Optional<Settlement> findByTradeId(String tradeId) {
        return settlementRepository.findByTradeId(tradeId);
    }

    @Transactional(readOnly = true)
    public List<Settlement> findByStatus(SettlementStatus status) {
        return settlementRepository.findByStatus(status);
    }

    @Transactional(readOnly = true)
    public List<Settlement> findByAccountId(String accountId) {
        return settlementRepository.findByBuyerAccountIdOrSellerAccountId(accountId, accountId);
    }
}
