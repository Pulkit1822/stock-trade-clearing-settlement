package com.clearing.margin.outbox;

import com.clearing.shared.outbox.OutboxEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelayScheduler(OutboxRepository outboxRepository, KafkaTemplate<String, String> kafkaTemplate) {
        this.outboxRepository = outboxRepository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${outbox.poll.interval:1000}")
    @Transactional
    public void relayEvents() {
        List<OutboxEvent> pendingEvents = outboxRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, 50));

        if (!pendingEvents.isEmpty()) {
            log.debug("Found {} pending outbox events to relay.", pendingEvents.size());
        }

        for (OutboxEvent event : pendingEvents) {
            String topic = determineTopic(event.getType());
            
            try {
                kafkaTemplate.send(topic, event.getAggregateId(), event.getPayload()).get();
                outboxRepository.delete(event);
                
                log.info("Relayed outbox event [{}] of type [{}] to topic [{}]", 
                         event.getId(), event.getType(), topic);
            } catch (Exception e) {
                log.error("Failed to relay outbox event [{}] of type [{}]. Will retry next polling cycle.", 
                          event.getId(), event.getType(), e);
                break;
            }
        }
    }

    private String determineTopic(String eventType) {
        return switch (eventType) {
            case "MarginCallEvent" -> "margin-call-issued";
            default -> throw new IllegalArgumentException("Unknown event type for routing: " + eventType);
        };
    }
}
