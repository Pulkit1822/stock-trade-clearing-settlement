package com.clearing.position.kafka;

import com.clearing.shared.events.StockSplitAppliedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class StockSplitPublisher {

    private static final Logger log = LoggerFactory.getLogger(StockSplitPublisher.class);
    private static final String TOPIC = "stock-split-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public StockSplitPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(StockSplitAppliedEvent event) {
        log.info("Publishing StockSplitAppliedEvent for account {} instrument {}", event.accountId(), event.instrumentId());
        kafkaTemplate.send(TOPIC, event.accountId(), event);
    }
}
