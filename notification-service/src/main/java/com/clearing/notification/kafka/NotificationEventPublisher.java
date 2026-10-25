package com.clearing.notification.kafka;

import com.clearing.shared.events.NotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class NotificationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventPublisher.class);
    private static final String TOPIC_NOTIFICATION_EVENTS = "notification-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public NotificationEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a notification-events record to the monitoring/log pipeline.
     * Partition key is accountId for ordered delivery per participant.
     * This is a terminal event — no downstream service consumes it.
     */
    public void publish(NotificationEvent event) {
        String partitionKey = event.accountId();

        log.info("Publishing NotificationEvent [tradeId={}, accountId={}] to topic [{}]",
                event.tradeId(), partitionKey, TOPIC_NOTIFICATION_EVENTS);

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC_NOTIFICATION_EVENTS, partitionKey, event);

        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("NotificationEvent published [tradeId={}, accountId={}, partition={}]",
                        event.tradeId(), partitionKey, result.getRecordMetadata().partition());
            } else {
                log.error("Failed to publish NotificationEvent [tradeId={}, accountId={}]",
                        event.tradeId(), partitionKey, exception);
            }
        });
    }
}
