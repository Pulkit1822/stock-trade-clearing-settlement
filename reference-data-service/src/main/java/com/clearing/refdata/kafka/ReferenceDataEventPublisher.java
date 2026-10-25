package com.clearing.refdata.kafka;

import com.clearing.shared.events.ReferenceDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class ReferenceDataEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(ReferenceDataEventPublisher.class);
    private static final String TOPIC = "reference-data-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ReferenceDataEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a reference-data-events record when an instrument or participant changes.
     * Partition key is entityId for ordered delivery per entity.
     */
    public void publish(ReferenceDataEvent event) {
        log.info("Publishing ReferenceDataEvent [entityId={}, entityType={}, changeType={}]",
                event.entityId(), event.entityType(), event.changeType());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event.entityId(), event);

        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("ReferenceDataEvent published [entityId={}, partition={}]",
                        event.entityId(), result.getRecordMetadata().partition());
            } else {
                log.error("Failed to publish ReferenceDataEvent [entityId={}]",
                        event.entityId(), exception);
            }
        });
    }
}
