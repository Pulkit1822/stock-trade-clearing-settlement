package com.clearing.corporate.kafka;

import com.clearing.shared.events.CorporateActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Component
public class CorporateActionEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionEventPublisher.class);
    private static final String TOPIC = "corporate-action-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CorporateActionEventPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publishes a corporate-action-events record.
     * Partition key is instrumentId for ordered delivery per instrument.
     * Consumer: PositionService (applies position adjustments).
     */
    public void publish(CorporateActionEvent event) {
        log.info("Publishing CorporateActionEvent [corporateActionId={}, instrumentId={}, type={}]",
                event.corporateActionId(), event.instrumentId(), event.actionType());

        CompletableFuture<SendResult<String, Object>> future =
                kafkaTemplate.send(TOPIC, event.instrumentId(), event);

        future.whenComplete((result, exception) -> {
            if (exception == null) {
                log.info("CorporateActionEvent published [corporateActionId={}, partition={}]",
                        event.corporateActionId(), result.getRecordMetadata().partition());
            } else {
                log.error("Failed to publish CorporateActionEvent [corporateActionId={}]",
                        event.corporateActionId(), exception);
            }
        });
    }
}
