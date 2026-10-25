package com.clearing.position.service;

import com.clearing.position.kafka.TradeValidationPublisher;
import com.clearing.position.model.Position;
import com.clearing.position.repository.PositionRepository;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.shared.events.TradeValidatedEvent;
import com.clearing.shared.events.TradeValidationStatus;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PositionValidationServiceTest {

    @Mock
    private PositionRepository positionRepository;

    @Mock
    private TradeValidationPublisher validationPublisher;

    @InjectMocks
    private PositionValidationService positionValidationService;

    @Test
    void shouldValidateAndProcessWhenSellerHasSufficientShares() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(tradeId, "BUYER1", "SELLER1", "AAPL", 100, new BigDecimal("150.00"), Instant.now());

        Position sellerPos = new Position(UUID.randomUUID(), "SELLER1", "AAPL", new BigDecimal("500"));
        Position buyerPos = new Position(UUID.randomUUID(), "BUYER1", "AAPL", new BigDecimal("0"));

        when(positionRepository.findByAccountIdAndInstrumentId("SELLER1", "AAPL")).thenReturn(Optional.of(sellerPos));
        when(positionRepository.findByAccountIdAndInstrumentId("BUYER1", "AAPL")).thenReturn(Optional.of(buyerPos));

        // Act
        positionValidationService.validateAndProcessTrade(event);

        // Assert DB Updates
        verify(positionRepository, times(2)).save(any(Position.class));
        assertEquals(new BigDecimal("400"), sellerPos.getQuantity()); // 500 - 100
        assertEquals(new BigDecimal("100"), buyerPos.getQuantity());  // 0 + 100

        // Assert Validation Publisher
        ArgumentCaptor<TradeValidatedEvent> captor = ArgumentCaptor.forClass(TradeValidatedEvent.class);
        verify(validationPublisher, times(1)).publishValidationResult(captor.capture());
        
        TradeValidatedEvent emittedEvent = captor.getValue();
        assertEquals(tradeId, emittedEvent.tradeId());
        assertEquals(TradeValidationStatus.VALIDATED, emittedEvent.status());
    }

    @Test
    void shouldRejectWhenSellerHasInsufficientShares() {
        // Arrange
        String tradeId = UUID.randomUUID().toString();
        TradeSubmittedEvent event = new TradeSubmittedEvent(tradeId, "BUYER1", "SELLER1", "AAPL", 100, new BigDecimal("150.00"), Instant.now());

        // Seller only has 50 shares
        Position sellerPos = new Position(UUID.randomUUID(), "SELLER1", "AAPL", new BigDecimal("50"));
        when(positionRepository.findByAccountIdAndInstrumentId("SELLER1", "AAPL")).thenReturn(Optional.of(sellerPos));

        // Act
        positionValidationService.validateAndProcessTrade(event);

        // Assert Security Rules enforcing immediate rejection
        verify(positionRepository, never()).save(any(Position.class));

        ArgumentCaptor<TradeValidatedEvent> captor = ArgumentCaptor.forClass(TradeValidatedEvent.class);
        verify(validationPublisher, times(1)).publishValidationResult(captor.capture());
        
        TradeValidatedEvent emittedEvent = captor.getValue();
        assertEquals(TradeValidationStatus.REJECTED, emittedEvent.status());
    }
}
