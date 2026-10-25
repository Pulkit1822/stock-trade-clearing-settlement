package com.clearing.clearing.outbox;

import com.clearing.clearing.kafka.SettlementEventPublisher;
import com.clearing.shared.events.SettlementCompletedEvent;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.clearing.shared.outbox.OutboxEvent;

import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRepository outboxRepository;
    private final SettlementEventPublisher settlementEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxRelayScheduler(OutboxRepository outboxRepository,
                                SettlementEventPublisher settlementEventPublisher,
                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.settlementEventPublisher = settlementEventPublisher;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${outbox.poll.interval:2000}")
    @Transactional
    public void processOutbox() {
        List<OutboxEvent> events = outboxRepository.findAllByOrderByCreatedAtAsc(PageRequest.of(0, 50));
        if (events.isEmpty()) {
            return;
        }

        log.info("Outbox Poller: Found {} events to publish", events.size());

        for (OutboxEvent event : events) {
            try {
                if ("SettlementCompletedEvent".equals(event.getType())) {
                    SettlementCompletedEvent payload = objectMapper.readValue(event.getPayload(), SettlementCompletedEvent.class);
                    settlementEventPublisher.publishSettlementCompleted(payload).get();
                } else {
                    log.warn("Unknown event type in outbox: {}", event.getType());
                }
                
                outboxRepository.delete(event);
                
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize Outbox event {}: {}", event.getId(), e.getMessage());
            } catch (Exception e) {
                log.error("Failed to publish Outbox event {} to Kafka: {}", event.getId(), e.getMessage());
                throw new RuntimeException("Failed to publish Outbox event", e);
            }
        }
    }
}
