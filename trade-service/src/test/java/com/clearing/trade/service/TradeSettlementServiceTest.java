package com.clearing.trade.service;

import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for TradeSettlementService.
 *
 * Tests cover:
 * - Happy path: Trade transitions SUBMITTED → SETTLED
 * - Idempotency: Already-SETTLED trade is not touched again
 * - Unknown tradeId: Logs warning and skips DB write
 * - Malformed UUID: Logs error and skips DB write
 */
@ExtendWith(MockitoExtension.class)
class TradeSettlementServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @InjectMocks
    private TradeSettlementService tradeSettlementService;

    // ─────────────────────────────────────────────────────────────────────────
    // Happy path
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateTradeStatusToSettledOnValidEvent() {
        UUID tradeId = UUID.randomUUID();
        Trade submittedTrade = buildTrade(tradeId, TradeStatus.SUBMITTED);
        SettlementCompletedEvent event = buildEvent(tradeId.toString());

        when(tradeRepository.findById(tradeId)).thenReturn(Optional.of(submittedTrade));
        when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> invocation.getArgument(0));

        tradeSettlementService.markTradeAsSettled(event);

        ArgumentCaptor<Trade> savedTradeCaptor = ArgumentCaptor.forClass(Trade.class);
        verify(tradeRepository, times(1)).save(savedTradeCaptor.capture());

        Trade savedTrade = savedTradeCaptor.getValue();
        assertEquals(TradeStatus.SETTLED, savedTrade.getStatus(),
                "Trade status must be SETTLED after settlement event is processed");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Idempotency
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldSkipUpdateWhenTradeAlreadySettled() {
        UUID tradeId = UUID.randomUUID();
        Trade alreadySettled = buildTrade(tradeId, TradeStatus.SETTLED);
        SettlementCompletedEvent event = buildEvent(tradeId.toString());

        when(tradeRepository.findById(tradeId)).thenReturn(Optional.of(alreadySettled));

        tradeSettlementService.markTradeAsSettled(event);

        // Repository.save must NOT be called — idempotent no-op
        verify(tradeRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unknown trade
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldSkipUpdateWhenTradeNotFoundInDatabase() {
        UUID tradeId = UUID.randomUUID();
        SettlementCompletedEvent event = buildEvent(tradeId.toString());

        when(tradeRepository.findById(tradeId)).thenReturn(Optional.empty());

        // Should complete without exception — tolerant of unknown IDs
        tradeSettlementService.markTradeAsSettled(event);

        verify(tradeRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Malformed UUID in event
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldSkipProcessingWhenTradeIdIsNotValidUuid() {
        SettlementCompletedEvent event = buildEvent("NOT-A-VALID-UUID");

        // Should complete without exception — defensive UUID parsing
        tradeSettlementService.markTradeAsSettled(event);

        // No DB interaction whatsoever
        verify(tradeRepository, never()).findById(any());
        verify(tradeRepository, never()).save(any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────────────────

    private Trade buildTrade(UUID tradeId, TradeStatus status) {
        return new Trade(
                tradeId,
                "BUYER-001",
                "SELLER-001",
                "AAPL",
                new BigDecimal("100"),
                new BigDecimal("150.00"),
                Instant.now(),
                status
        );
    }

    private SettlementCompletedEvent buildEvent(String tradeId) {
        return new SettlementCompletedEvent(tradeId, "BUYER-001", "SELLER-001", Instant.now());
    }
}
