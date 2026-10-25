package com.clearing.position.kafka;

import com.clearing.shared.events.PositionUpdatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class PositionUpdatedPublisher {

    private static final Logger log = LoggerFactory.getLogger(PositionUpdatedPublisher.class);
    private static final String TOPIC_POSITION_UPDATED = "position-updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PositionUpdatedPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a PositionUpdatedEvent to the position-updated topic.
     * Partition key is accountId to ensure ordered processing per account.
     *
     * @param event The position update for one side of the trade (buyer or seller).
     */
    public void publishPositionUpdated(PositionUpdatedEvent event) {
        String partitionKey = event.accountId();

        log.info("Publishing PositionUpdatedEvent [tradeId={}, accountId={}, delta={}] to topic [{}]",
                event.tradeId(), event.accountId(), event.quantityDelta(), TOPIC_POSITION_UPDATED);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_POSITION_UPDATED, partitionKey, event);

        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Published PositionUpdatedEvent for tradeId [{}] accountId [{}] to partition [{}]",
                        event.tradeId(), event.accountId(), result.getRecordMetadata().partition());
            } else {
                log.error("CRITICAL ALARM: Failed to publish PositionUpdatedEvent for tradeId [{}] accountId [{}].",
                        event.tradeId(), event.accountId(), exception);
            }
        });
    }
}
