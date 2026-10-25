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
    public void publishValidationResult(TradeValidatedEvent event) {
        String partitionKey = event.tradeId();
        
        log.info("Publishing TradeValidatedEvent [tradeId={}, status={}] to topic [{}]", 
                 partitionKey, event.status(), TOPIC_TRADE_VALIDATED);
                 
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(TOPIC_TRADE_VALIDATED, partitionKey, event);
                
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully published Validation Status [{}] for tradeId [{}] to partition [{}]",
                         event.status(), partitionKey, result.getRecordMetadata().partition());
            } else {
                log.error("CRITICAL ALARM: Failed to publish TradeValidatedEvent for tradeId [{}]. System state may be desynced.",
                          partitionKey, exception);
            }
        });
    }
}
