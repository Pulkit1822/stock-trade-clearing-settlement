package com.clearing.trade.service;

import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles the terminal update to a Trade record when settlement is finalized.
 *
 * <p>This service is the only component that transitions a Trade from SUBMITTED → SETTLED.
 * It is driven by the {@code settlement-completed} Kafka topic published by ClearingService.
 *
 * <p>Design decisions:
 * <ul>
 *   <li>Idempotent: if the trade is already SETTLED, the event is a no-op. Safe for Kafka redelivery.</li>
 *   <li>Tolerant of unknown tradeIds: logs a warning and returns rather than crashing the consumer.</li>
 *   <li>@Transactional ensures the status update is atomic with the DB commit.</li>
 * </ul>
 */
@Service
public class TradeSettlementService {

    private static final Logger log = LoggerFactory.getLogger(TradeSettlementService.class);

    private final TradeRepository tradeRepository;

    public TradeSettlementService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    /**
     * Marks the Trade record as SETTLED in response to a {@link SettlementCompletedEvent}.
     *
     * <p>Idempotency contract: if the trade is already in status SETTLED, this method
     * logs a warning and returns immediately without touching the database.
     *
     * @param event The settlement-completed event from ClearingService.
     */
    @Transactional
    public void markTradeAsSettled(SettlementCompletedEvent event) {
        UUID tradeUuid;
        try {
            tradeUuid = UUID.fromString(event.tradeId());
        } catch (IllegalArgumentException e) {
            log.error("SettlementCompletedEvent contains invalid tradeId format [tradeId={}]. Skipping.",
                    event.tradeId(), e);
            return;
        }

        Optional<Trade> tradeOptional = tradeRepository.findById(tradeUuid);

        if (tradeOptional.isEmpty()) {
            log.warn("Received SettlementCompletedEvent for unknown tradeId [{}]. Trade not found in DB. Skipping.",
                    event.tradeId());
            return;
        }

        Trade trade = tradeOptional.get();

        if (trade.getStatus() == TradeStatus.SETTLED) {
            log.warn("Duplicate SettlementCompletedEvent for tradeId [{}]. Trade already SETTLED. Skipping (idempotent).",
                    event.tradeId());
            return;
        }

        trade.setStatus(TradeStatus.SETTLED);
        tradeRepository.save(trade);

        log.info("Trade [{}] status updated to SETTLED. Buyer [{}] Seller [{}] settled at [{}].",
                event.tradeId(), event.buyerAccount(), event.sellerAccount(), event.timestamp());
    }
}
