package com.clearing.notification.kafka;

import com.clearing.notification.service.NotificationService;
import com.clearing.shared.events.MarginCallEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class MarginCallConsumer {

    private static final Logger log = LoggerFactory.getLogger(MarginCallConsumer.class);

    private final NotificationService notificationService;

    public MarginCallConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    /**
     * Consumes margin-call-issued events from MarginService.
     * Triggers participant notifications for the account.
     *
     * Manual ACK: offset only advances after the notification record is persisted.
     */
    @KafkaListener(topics = "margin-call-issued", groupId = "notification-service")
    public void consume(MarginCallEvent event, Acknowledgment ack) {
        log.info("Received MarginCallEvent [marginCallId={}, accountId={}, deficit={}]",
                event.marginCallId(), event.accountId(), event.deficitAmount());

        try {
            notificationService.handleMarginCall(event);
            ack.acknowledge();
            log.info("Offset committed for MarginCallEvent [marginCallId={}]", event.marginCallId());
        } catch (Exception e) {
            log.error("Failed to process MarginCallEvent [marginCallId={}]. Offset will not advance.",
                    event.marginCallId(), e);
            throw e;
        }
    }
}
