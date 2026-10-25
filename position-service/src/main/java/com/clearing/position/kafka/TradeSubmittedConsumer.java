package com.clearing.position.kafka;

import com.clearing.position.service.PositionValidationService;
import com.clearing.shared.events.TradeSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TradeSubmittedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeSubmittedConsumer.class);

    private final PositionValidationService positionValidationService;

    // Strict constructor injection for the service layer
    public TradeSubmittedConsumer(PositionValidationService positionValidationService) {
        this.positionValidationService = positionValidationService;
    }

    /**
     * Listens to the incoming trade-submitted topic explicitly.
     * Uses Manual Acknowledgment (enable-auto-commit is false in YML) to ensure
     * exactly-once processing architecture. If the database update fails mid-way,
     * the offset is NOT committed, forcing a retry.
     * 
     * @param event The deserialized record object directly mapped by Spring Kafka JSON Trust Packages
     * @param ack   The manual offset acknowledgment flag
     */
    @KafkaListener(topics = "trade-submitted", groupId = "position-service")
    public void consumeTrade(TradeSubmittedEvent event, Acknowledgment ack) {
        log.info("Consumer received TradeSubmittedEvent [tradeId={}] for Instrument [{}]", 
                 event.tradeId(), event.instrumentId());

        try {
            // Hand off strictly to @Transactional logic boundary
            positionValidationService.validateAndProcessTrade(event);
            
            // IF and ONLY IF the entire transaction succeeds, we instruct Kafka to shift the consumer offset forward.
            ack.acknowledge();
            log.info("Successfully processed and committed offset for Trade [{}]", event.tradeId());
            
        } catch (Exception e) {
            log.error("CRITICAL ALARM: Processing failed for TradeSubmittedEvent [tradeId={}]. Offset will not advance automatically.",
                      event.tradeId(), e);
            // By NOT acknowledging, Spring Kafka will follow its retry configuration.
            // Under Spring Boot semantics, throwing the exception here also forces DLQ (Dead Letter Queue) rerouting
            // if an ErrorHandler is configured. For now, it retries.
            throw e;
        }
    }
}
