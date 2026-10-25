package com.clearing.clearing.service;

import com.clearing.clearing.kafka.SettlementEventPublisher;
import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import com.clearing.clearing.repository.SettlementRepository;
import com.clearing.shared.events.PositionUpdatedEvent;
import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.shared.events.TradeValidatedEvent;
import com.clearing.shared.events.TradeValidationStatus;
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
    private final SettlementEventPublisher settlementEventPublisher;

    public ClearingService(SettlementRepository settlementRepository,
                           SettlementEventPublisher settlementEventPublisher) {
        this.settlementRepository = settlementRepository;
        this.settlementEventPublisher = settlementEventPublisher;
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
        if (settlementRepository.findByTradeId(event.tradeId()).isPresent()) {
            log.warn("Duplicate TradeValidatedEvent received for tradeId [{}]. Skipping.", event.tradeId());
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
        settlementRepository.save(pending);
        log.info("Settlement created with status PENDING for tradeId [{}]", event.tradeId());
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
        Settlement settlement = settlementRepository.findByTradeId(event.tradeId())
                .orElseGet(() -> {
                    log.warn("No settlement found for tradeId [{}] on PositionUpdatedEvent. Creating late entry.", event.tradeId());
                    return new Settlement(UUID.randomUUID(), event.tradeId(), SettlementStatus.PENDING);
                });

        if (settlement.getStatus() == SettlementStatus.SETTLED
                || settlement.getStatus() == SettlementStatus.FAILED) {
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

        settlementRepository.save(settlement);

        if (settlement.isBuyerConfirmed() && settlement.isSellerConfirmed()) {
            finalizeSettlement(settlement);
        }
    }

    private void finalizeSettlement(Settlement settlement) {
        settlement.setStatus(SettlementStatus.SETTLED);
        settlement.setSettledAt(Instant.now());
        settlementRepository.save(settlement);

        log.info("Settlement FINALIZED for tradeId [{}]. Buyer [{}] Seller [{}]",
                settlement.getTradeId(), settlement.getBuyerAccountId(), settlement.getSellerAccountId());

        SettlementCompletedEvent completedEvent = new SettlementCompletedEvent(
                settlement.getTradeId(),
                settlement.getBuyerAccountId(),
                settlement.getSellerAccountId(),
                settlement.getSettledAt()
        );
        settlementEventPublisher.publishSettlementCompleted(completedEvent);
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
