package com.clearing.margin.kafka;

import com.clearing.margin.service.MarginCalculationService;
import com.clearing.shared.events.PositionUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class PositionUpdatedConsumer {

    private static final Logger log = LoggerFactory.getLogger(PositionUpdatedConsumer.class);

    private static final String TOPIC = "position-updated";
    private static final String GROUP_ID = "margin-service";

    private final MarginCalculationService marginCalculationService;

    public PositionUpdatedConsumer(MarginCalculationService marginCalculationService) {
        this.marginCalculationService = marginCalculationService;
    }

    @KafkaListener(topics = TOPIC, groupId = GROUP_ID)
    public void consume(PositionUpdatedEvent event, Acknowledgment ack) {
        log.info("Received PositionUpdatedEvent for account [{}] regarding instrument [{}]", 
                 event.accountId(), event.instrumentId());

        try {
            marginCalculationService.evaluateMarginRequirement(
                    event.accountId(),
                    event.instrumentId(),
                    event.newQuantity()
            );
            ack.acknowledge();
        } catch (Exception e) {
            log.error("Failed to process PositionUpdatedEvent for account [{}]", event.accountId(), e);
            throw e; // Rely on Spring Kafka to retry or DLQ
        }
    }
}
