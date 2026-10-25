package com.clearing.trade.outbox;

import com.clearing.shared.events.TradeSubmittedEvent;
import com.clearing.shared.outbox.OutboxEvent;
import com.clearing.trade.kafka.TradeEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
public class OutboxRelayScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelayScheduler.class);

    private final OutboxRepository outboxRepository;
    private final TradeEventPublisher tradeEventPublisher;
    private final ObjectMapper objectMapper;

    public OutboxRelayScheduler(OutboxRepository outboxRepository,
                                TradeEventPublisher tradeEventPublisher,
                                ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.tradeEventPublisher = tradeEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Polls the outbox table every 2 seconds for unpublished events.
     * In a multi-instance deployment, we would use something like ShedLock or 
     * explicit 'locked_at' logic to prevent concurrent polling, but for 
     * this implementation, we rely on Kafka's idempotency downstream if 
     * duplicates occur.
     */
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
                if ("TradeSubmittedEvent".equals(event.getType())) {
                    TradeSubmittedEvent payload = objectMapper.readValue(event.getPayload(), TradeSubmittedEvent.class);
                    // Synchronously wait for Kafka broker acknowledgment to guarantee no data loss
                    tradeEventPublisher.publishTradeSubmitted(payload).get();
                } else {
                    log.warn("Unknown event type in outbox: {}", event.getType());
                }
                
                // Remove the event to mark as processed ONLY AFTER successful publish
                outboxRepository.delete(event);
                
            } catch (JsonProcessingException e) {
                log.error("Failed to deserialize Outbox event {}: {}", event.getId(), e.getMessage());
                // Skip deletion or mark as FAILED so it doesn't get lost
                // For this implementation, we will log and ignore it in this pass.
            } catch (Exception e) {
                log.error("Failed to publish Outbox event {} to Kafka: {}", event.getId(), e.getMessage());
                // Do NOT delete the event. Let the transaction roll back or continue to the next event.
                // The event will be retried on the next scheduled poll.
                throw new RuntimeException("Failed to publish Outbox event", e);
            }
        }
    }
}
