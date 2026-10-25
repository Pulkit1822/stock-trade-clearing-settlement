package com.clearing.trade.service;

import com.clearing.shared.dto.TradeRequest;
import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.trade.kafka.TradeEventPublisher;
import com.clearing.trade.model.Trade;
import com.clearing.trade.model.TradeStatus;
import com.clearing.trade.repository.TradeRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class TradeProcessingServiceTest {

    @Mock
    private TradeRepository tradeRepository;

    @Mock
    private TradeEventPublisher tradeEventPublisher;

    @InjectMocks
    private TradeProcessingService tradeProcessingService;

    @Test
    void shouldPersistTradeAndPublishEventStrictly() {
        // Arrange
        TradeRequest request = new TradeRequest("MSFT", "B-777", "S-888", 50, new BigDecimal("350.10"));

        Mockito.when(tradeRepository.save(any(Trade.class))).thenAnswer(invocation -> {
            Trade tradeToSave = invocation.getArgument(0);
            tradeToSave.setId(java.util.UUID.randomUUID()); 
            return tradeToSave;
        });

        // Act
        Trade result = tradeProcessingService.processTradeRequest(request);

        // Assert DB Interaction
        verify(tradeRepository, times(1)).save(any(Trade.class));
        assertNotNull(result.getId(), "Trade ID must be generated securely");
        assertEquals("MSFT", result.getInstrumentId());
        assertEquals(TradeStatus.SUBMITTED, result.getStatus());

        // Assert Event Construction
        verify(tradeEventPublisher, times(1)).publishTradeSubmitted(any(TradeSubmittedEvent.class));
    }
}
