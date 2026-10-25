package com.clearing.trade.kafka;

import com.clearing.shared.events.TradeSubmittedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class TradeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(TradeEventPublisher.class);
    
    // Extracted as constant to avoid magic strings
    private static final String TOPIC_TRADE_SUBMITTED = "trade-submitted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Constructor injection explicitly
    public TradeEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a TradeSubmittedEvent to the Kafka broker.
     * Uses the generated tradeId as the partition key (as defined in the shared library contract)
     * to ensure load is evenly distributed across the 3 partitions initially configured.
     * 
     * @param event The exact TradeSubmittedEvent record mapping representing the accepted trade.
     */
    public void publishTradeSubmitted(TradeSubmittedEvent event) {
        String partitionKey = event.tradeId();
        
        log.info("Attempting to publish TradeSubmittedEvent [tradeId={}] to topic [{}]", 
                 partitionKey, TOPIC_TRADE_SUBMITTED);
                 
        // Asynchronous publish using Spring's CompletableFuture wrapper
        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(TOPIC_TRADE_SUBMITTED, partitionKey, event);
                
        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully published TradeSubmittedEvent [tradeId={}] to partition [{}] with offset [{}]",
                         partitionKey, 
                         result.getRecordMetadata().partition(), 
                         result.getRecordMetadata().offset());
            } else {
                log.error("CRITICAL FAILURE: Failed to publish TradeSubmittedEvent [tradeId={}]. Reason: {}",
                          partitionKey, exception.getMessage(), exception);
                // Note for production: If Kafka is down despite internal retries (idempotency=true), 
                // we should fallback to an Outbox pattern (saving event locally to DB) or halt the transaction.
            }
        });
    }
}
