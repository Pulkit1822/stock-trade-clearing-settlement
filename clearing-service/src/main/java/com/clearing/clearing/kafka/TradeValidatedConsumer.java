package com.clearing.clearing.kafka;

import com.clearing.clearing.service.ClearingService;
import com.clearing.shared.events.TradeValidatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class TradeValidatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(TradeValidatedConsumer.class);

    private final ClearingService clearingService;

    public TradeValidatedConsumer(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    /**
     * Consumes trade-validated events from PositionService.
     * VALIDATED trades create a PENDING settlement record.
     * REJECTED trades create a FAILED settlement record and stop processing.
     *
     * Manual ACK: offset only advances after the DB write succeeds.
     */
    @KafkaListener(topics = "trade-validated", groupId = "clearing-service")
    public void consume(TradeValidatedEvent event, Acknowledgment ack) {
        log.info("Received TradeValidatedEvent [tradeId={}, status={}]", event.tradeId(), event.status());

        try {
            clearingService.handleTradeValidated(event);
            ack.acknowledge();
            log.info("Offset committed for TradeValidatedEvent [tradeId={}]", event.tradeId());
        } catch (Exception e) {
            log.error("Failed to process TradeValidatedEvent [tradeId={}]. Offset will not advance.", event.tradeId(), e);
            throw e;
        }
    }
}
