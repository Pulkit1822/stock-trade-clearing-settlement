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
    public void publishSettlementCompleted(SettlementCompletedEvent event) {
        String partitionKey = event.tradeId();

        log.info("Publishing SettlementCompletedEvent [tradeId={}] to topic [{}]",
                partitionKey, TOPIC_SETTLEMENT_COMPLETED);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_SETTLEMENT_COMPLETED, partitionKey, event);

        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("Successfully published SettlementCompletedEvent [tradeId={}] to partition [{}]",
                        partitionKey, result.getRecordMetadata().partition());
            } else {
                log.error("CRITICAL ALARM: Failed to publish SettlementCompletedEvent [tradeId={}]. NotificationService will not be triggered.",
                        partitionKey, exception);
            }
        });
    }
}
