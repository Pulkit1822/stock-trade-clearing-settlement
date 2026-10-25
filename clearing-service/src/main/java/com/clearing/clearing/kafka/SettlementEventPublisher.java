package com.clearing.clearing.kafka;

import com.clearing.shared.events.SettlementCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class SettlementEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(SettlementEventPublisher.class);
    private static final String TOPIC_SETTLEMENT_COMPLETED = "settlement-completed";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public SettlementEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the terminal settlement-completed event.
     * Partition key is tradeId for ordered delivery to NotificationService.
     */
    public CompletableFuture<SendResult<String, Object>> publishSettlementCompleted(SettlementCompletedEvent event) {
        String partitionKey = event.tradeId();
        
        log.info("Attempting to publish SettlementCompletedEvent [tradeId={}] to topic [{}]", 
                 partitionKey, TOPIC_SETTLEMENT_COMPLETED);
                 
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(TOPIC_SETTLEMENT_COMPLETED, partitionKey, event);
                
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully published SettlementCompletedEvent [tradeId={}] to partition [{}] with offset [{}]",
                         partitionKey, 
                         result.getRecordMetadata().partition(), 
                         result.getRecordMetadata().offset());
            } else {
                log.error("CRITICAL FAILURE: Failed to publish SettlementCompletedEvent [tradeId={}]. Reason: {}",
                          partitionKey, exception.getMessage(), exception);
            }
        });
        
        return future;
    }
}
