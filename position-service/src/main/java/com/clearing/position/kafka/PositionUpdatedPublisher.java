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
    public CompletableFuture<SendResult<String, Object>> publishPositionUpdated(PositionUpdatedEvent event) {
        // Use composite partition key to guarantee ordering per account-instrument pair
        String partitionKey = event.accountId() + "-" + event.instrumentId();
        
        log.info("Attempting to publish PositionUpdatedEvent [accountId={}, instrumentId={}] to topic [{}]", 
                 event.accountId(), event.instrumentId(), TOPIC_POSITION_UPDATED);
                 
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(TOPIC_POSITION_UPDATED, partitionKey, event);
                
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully published PositionUpdatedEvent [accountId={}] to partition [{}] offset [{}]",
                         event.accountId(), 
                         result.getRecordMetadata().partition(), 
                         result.getRecordMetadata().offset());
            } else {
                log.error("CRITICAL FAILURE: Failed to publish PositionUpdatedEvent [accountId={}]. Reason: {}",
                          event.accountId(), exception.getMessage(), exception);
            }
        });
        
        return future;
    }
}
