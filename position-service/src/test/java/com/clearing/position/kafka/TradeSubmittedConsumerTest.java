package com.clearing.position.kafka;

import com.clearing.position.service.PositionValidationService;
import com.clearing.shared.events.TradeSubmittedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.support.Acknowledgment;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TradeSubmittedConsumerTest {

    @Mock
    private PositionValidationService positionValidationService;

    @Mock
    private Acknowledgment acknowledgment;

    @InjectMocks
    private TradeSubmittedConsumer tradeSubmittedConsumer;

    @Test
    void shouldAcknowledgeWhenServiceProcessesSuccessfully() {
        // Arrange
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                UUID.randomUUID().toString(), "B1", "S1", "AAPL", 10, new BigDecimal("10.0"), Instant.now()
        );

        // Act
        tradeSubmittedConsumer.consumeTrade(event, acknowledgment);

        // Assert Exactly-Once pattern applies
        verify(positionValidationService, times(1)).validateAndProcessTrade(event);
        verify(acknowledgment, times(1)).acknowledge();
    }

    @Test
    void shouldNotAcknowledgeWhenServiceThrowsException() {
        // Arrange
        TradeSubmittedEvent event = new TradeSubmittedEvent(
                UUID.randomUUID().toString(), "B1", "S1", "AAPL", 10, new BigDecimal("10.0"), Instant.now()
        );
        doThrow(new RuntimeException("Database down")).when(positionValidationService).validateAndProcessTrade(event);

        // Act & Assert
        assertThrows(RuntimeException.class, () -> tradeSubmittedConsumer.consumeTrade(event, acknowledgment));

        // Offset MUST NOT be moved forward, leaving it for Spring Kafka DLQ mechanisms
        verify(acknowledgment, never()).acknowledge();
    }
}
