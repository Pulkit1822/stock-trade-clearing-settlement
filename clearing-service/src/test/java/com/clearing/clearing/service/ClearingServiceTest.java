package com.clearing.clearing.service;

import com.clearing.clearing.kafka.SettlementEventPublisher;
import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import com.clearing.clearing.repository.SettlementRepository;
import com.clearing.shared.events.PositionUpdatedEvent;
import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.shared.events.TradeValidatedEvent;
import com.clearing.shared.events.TradeValidationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ClearingServiceTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementEventPublisher settlementEventPublisher;

    @InjectMocks
    private ClearingService clearingService;

    // ─── handleTradeValidated ────────────────────────────────────────────────

    @Test
    @DisplayName("VALIDATED trade creates a PENDING settlement record")
    void handleTradeValidated_validated_createsPendingSettlement() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        TradeValidatedEvent event = new TradeValidatedEvent(
                tradeId, TradeValidationStatus.VALIDATED, null, Instant.now());

        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.empty());

        // Act
        clearingService.handleTradeValidated(event);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, times(1)).save(captor.capture());

        Settlement saved = captor.getValue();
        assertEquals(tradeId, saved.getTradeId());
        assertEquals(SettlementStatus.PENDING, saved.getStatus());
        assertNull(saved.getFailureReason());
    }

    @Test
    @DisplayName("REJECTED trade creates a FAILED settlement with failure reason")
    void handleTradeValidated_rejected_createsFailedSettlement() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        String reason = "Insufficient position in Seller account";
        TradeValidatedEvent event = new TradeValidatedEvent(
                tradeId, TradeValidationStatus.REJECTED, reason, Instant.now());

        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.empty());

        // Act
        clearingService.handleTradeValidated(event);

        // Assert
        ArgumentCaptor<Settlement> captor = ArgumentCaptor.forClass(Settlement.class);
        verify(settlementRepository, times(1)).save(captor.capture());

        Settlement saved = captor.getValue();
        assertEquals(tradeId, saved.getTradeId());
        assertEquals(SettlementStatus.FAILED, saved.getStatus());
        assertEquals(reason, saved.getFailureReason());
    }

    @Test
    @DisplayName("Duplicate tradeId is skipped — idempotency enforced")
    void handleTradeValidated_duplicateTradeId_skipsProcessing() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        TradeValidatedEvent event = new TradeValidatedEvent(
                tradeId, TradeValidationStatus.VALIDATED, null, Instant.now());

        Settlement existing = new Settlement(UUID.randomUUID(), tradeId, SettlementStatus.PENDING);
        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.of(existing));

        // Act
        clearingService.handleTradeValidated(event);

        // Assert — no new settlement created
        verify(settlementRepository, never()).save(any(Settlement.class));
    }

    // ─── handlePositionUpdated ───────────────────────────────────────────────

    @Test
    @DisplayName("Buyer-side position update sets buyerConfirmed and buyerAccountId")
    void handlePositionUpdated_buyerSide_setsBuyerConfirmed() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        Settlement settlement = new Settlement(UUID.randomUUID(), tradeId, SettlementStatus.PENDING);

        PositionUpdatedEvent event = new PositionUpdatedEvent(
                "BUYER-001", "AAPL", 100, 200, tradeId, Instant.now());

        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.of(settlement));

        // Act
        clearingService.handlePositionUpdated(event);

        // Assert
        verify(settlementRepository, times(1)).save(settlement);
        assertTrue(settlement.isBuyerConfirmed());
        assertEquals("BUYER-001", settlement.getBuyerAccountId());
        assertEquals("AAPL", settlement.getInstrumentId());
        assertFalse(settlement.isSellerConfirmed());
        assertEquals(SettlementStatus.PENDING, settlement.getStatus());
    }

    @Test
    @DisplayName("Seller-side position update sets sellerConfirmed and sellerAccountId")
    void handlePositionUpdated_sellerSide_setsSellerConfirmed() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        Settlement settlement = new Settlement(UUID.randomUUID(), tradeId, SettlementStatus.PENDING);

        PositionUpdatedEvent event = new PositionUpdatedEvent(
                "SELLER-001", "AAPL", -100, 400, tradeId, Instant.now());

        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.of(settlement));

        // Act
        clearingService.handlePositionUpdated(event);

        // Assert
        verify(settlementRepository, times(1)).save(settlement);
        assertTrue(settlement.isSellerConfirmed());
        assertEquals("SELLER-001", settlement.getSellerAccountId());
        assertFalse(settlement.isBuyerConfirmed());
        assertEquals(SettlementStatus.PENDING, settlement.getStatus());
    }

    @Test
    @DisplayName("Both sides confirmed — settlement marked SETTLED and event published")
    void handlePositionUpdated_bothSidesConfirmed_finalizesSettlement() {
        // Arrange — buyer already confirmed, seller event arrives
        String tradeId = UUID.randomUUID().toString();
        Settlement settlement = new Settlement(UUID.randomUUID(), tradeId, SettlementStatus.PENDING);
        settlement.setBuyerAccountId("BUYER-001");
        settlement.setBuyerConfirmed(true);
        settlement.setInstrumentId("AAPL");

        PositionUpdatedEvent sellerEvent = new PositionUpdatedEvent(
                "SELLER-001", "AAPL", -100, 400, tradeId, Instant.now());

        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.of(settlement));

        // Act
        clearingService.handlePositionUpdated(sellerEvent);

        // Assert — settlement finalized
        assertEquals(SettlementStatus.SETTLED, settlement.getStatus());
        assertNotNull(settlement.getSettledAt());
        assertEquals("SELLER-001", settlement.getSellerAccountId());

        // Assert — settlement-completed event published
        ArgumentCaptor<SettlementCompletedEvent> captor = ArgumentCaptor.forClass(SettlementCompletedEvent.class);
        verify(settlementEventPublisher, times(1)).publishSettlementCompleted(captor.capture());

        SettlementCompletedEvent published = captor.getValue();
        assertEquals(tradeId, published.tradeId());
        assertEquals("BUYER-001", published.buyerAccount());
        assertEquals("SELLER-001", published.sellerAccount());
    }

    @Test
    @DisplayName("Position update on already-settled trade is ignored")
    void handlePositionUpdated_alreadySettled_skipsProcessing() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        Settlement settled = new Settlement(UUID.randomUUID(), tradeId, SettlementStatus.SETTLED);
        settled.setBuyerConfirmed(true);
        settled.setSellerConfirmed(true);

        PositionUpdatedEvent event = new PositionUpdatedEvent(
                "BUYER-001", "AAPL", 100, 200, tradeId, Instant.now());

        when(settlementRepository.findByTradeId(tradeId)).thenReturn(Optional.of(settled));

        // Act
        clearingService.handlePositionUpdated(event);

        // Assert — nothing updated, no event published
        verify(settlementRepository, never()).save(any(Settlement.class));
        verify(settlementEventPublisher, never()).publishSettlementCompleted(any());
    }
}
