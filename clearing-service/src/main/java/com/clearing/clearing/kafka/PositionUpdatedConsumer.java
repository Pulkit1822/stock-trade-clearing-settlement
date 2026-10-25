package com.clearing.clearing.kafka;

import com.clearing.clearing.service.ClearingService;
import com.clearing.shared.events.PositionUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PositionUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PositionUpdatedConsumer.class);

    private final ClearingService clearingService;

    public PositionUpdatedConsumer(ClearingService clearingService) {
        this.clearingService = clearingService;
    }

    /**
     * Consumes position-updated events from PositionService.
     * One event is emitted per account side (buyer and seller).
     * quantityDelta > 0 = buyer side confirmed.
     * quantityDelta < 0 = seller side confirmed.
     *
     * When both sides are confirmed, ClearingService marks the settlement SETTLED
     * and publishes settlement-completed.
     *
     * Manual ACK: offset only advances after the DB write succeeds.
     */
    @KafkaListener(topics = "position-updated", groupId = "clearing-service")
    public void consume(PositionUpdatedEvent event, Acknowledgment ack) {
        log.info("Received PositionUpdatedEvent [tradeId={}, accountId={}, delta={}]",
                event.tradeId(), event.accountId(), event.quantityDelta());

        try {
            clearingService.handlePositionUpdated(event);
            ack.acknowledge();
            log.info("Offset committed for PositionUpdatedEvent [tradeId={}, accountId={}]",
                    event.tradeId(), event.accountId());
        } catch (Exception e) {
            log.error("Failed to process PositionUpdatedEvent [tradeId={}, accountId={}]. Offset will not advance.",
                    event.tradeId(), event.accountId(), e);
            throw e;
        }
    }
}
