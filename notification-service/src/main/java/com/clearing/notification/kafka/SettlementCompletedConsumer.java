package com.clearing.notification.kafka;

import com.clearing.notification.service.NotificationService;
import com.clearing.shared.events.SettlementCompletedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class SettlementCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SettlementCompletedConsumer.class);

    private final NotificationService notificationService;

    public SettlementCompletedConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Consumes settlement-completed events from ClearingService.
     * Triggers participant notifications for both buyer and seller accounts.
     *
     * Manual ACK: offset only advances after both notification records are persisted.
     */
    @KafkaListener(topics = "settlement-completed", groupId = "notification-service")
    public void consume(SettlementCompletedEvent event, Acknowledgment ack) {
        log.info("Received SettlementCompletedEvent [tradeId={}, buyer={}, seller={}]",
                event.tradeId(), event.buyerAccount(), event.sellerAccount());

        try {
            notificationService.handleSettlementCompleted(event);
            ack.acknowledge();
            log.info("Offset committed for SettlementCompletedEvent [tradeId={}]", event.tradeId());
        } catch (Exception e) {
            log.error("Failed to process SettlementCompletedEvent [tradeId={}]. Offset will not advance.",
                    event.tradeId(), e);
            throw e;
        }
    }
}
