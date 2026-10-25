package com.clearing.notification.kafka;

import com.clearing.notification.service.NotificationService;
import com.clearing.shared.events.DividendPayoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class DividendPayoutConsumer {

    private static final Logger log = LoggerFactory.getLogger(DividendPayoutConsumer.class);

    private final NotificationService notificationService;

    public DividendPayoutConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "dividend-payout-events", groupId = "notification-service-dividend")
    public void consume(DividendPayoutEvent event, Acknowledgment ack) {
        log.info("Received DividendPayoutEvent [corporateActionId={}, accountId={}, amount={}]",
                event.corporateActionId(), event.accountId(), event.amount());

        try {
            notificationService.handleDividendPayout(event);
            ack.acknowledge();
            log.info("Offset committed for DividendPayoutEvent [corporateActionId={}]", event.corporateActionId());
        } catch (Exception e) {
            log.error("Failed to process DividendPayoutEvent [corporateActionId={}]. Offset will not advance.",
                    event.corporateActionId(), e);
            throw e;
        }
    }
}
