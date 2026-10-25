package com.clearing.position.kafka;

import com.clearing.shared.events.TradeValidatedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TradeValidationPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeValidationPublisher.class);
    private static final String TOPIC_TRADE_VALIDATED = "trade-validated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public TradeValidationPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes the TradeValidation outcome back to the network.
     * 
     * @param event The immutable TradeValidatedEvent containing REJECTED or VALIDATED status.
     */
    public CompletableFuture<SendResult<String, Object>> publishValidationResult(TradeValidatedEvent event) {
        String partitionKey = event.tradeId();
        
        log.info("Attempting to publish TradeValidatedEvent [tradeId={}, status={}] to topic [{}]", 
                 partitionKey, event.status(), TOPIC_TRADE_VALIDATED);
                 
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(TOPIC_TRADE_VALIDATED, partitionKey, event);
                
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully published TradeValidatedEvent [tradeId={}] to partition [{}] with offset [{}]",
                         partitionKey, 
                         result.getRecordMetadata().partition(), 
                         result.getRecordMetadata().offset());
            } else {
                log.error("CRITICAL FAILURE: Failed to publish TradeValidatedEvent [tradeId={}]. Reason: {}",
                          partitionKey, exception.getMessage(), exception);
            }
        });
        
        return future;
    }
}
