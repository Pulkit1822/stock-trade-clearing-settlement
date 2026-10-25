package com.clearing.position.kafka;

import com.clearing.shared.events.DividendPayoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class DividendPayoutPublisher {

    private static final Logger log = LoggerFactory.getLogger(DividendPayoutPublisher.class);
    private static final String TOPIC = "dividend-payout-events";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public DividendPayoutPublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPayout(DividendPayoutEvent event) {
        log.debug("Publishing Dividend Payout [{}] for Account [{}]", event.amount(), event.accountId());
        // Using accountId as the routing key
        kafkaTemplate.send(TOPIC, event.accountId(), event);
    }
}
