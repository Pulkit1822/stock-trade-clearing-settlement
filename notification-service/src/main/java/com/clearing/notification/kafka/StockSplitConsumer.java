package com.clearing.notification.kafka;

import com.clearing.notification.service.NotificationService;
import com.clearing.shared.events.StockSplitAppliedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

@Component
public class StockSplitConsumer {

    private static final Logger log = LoggerFactory.getLogger(StockSplitConsumer.class);

    private final NotificationService notificationService;

    public StockSplitConsumer(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @KafkaListener(topics = "stock-split-events", groupId = "notification-service-split")
    public void consume(StockSplitAppliedEvent event, Acknowledgment ack) {
        log.info("Received StockSplitAppliedEvent [corporateActionId={}, accountId={}, oldQuantity={}, newQuantity={}]",
                event.corporateActionId(), event.accountId(), event.oldQuantity(), event.newQuantity());

        try {
            notificationService.handleStockSplit(event);
            ack.acknowledge();
            log.info("Offset committed for StockSplitAppliedEvent [corporateActionId={}]", event.corporateActionId());
        } catch (Exception e) {
            log.error("Failed to process StockSplitAppliedEvent [corporateActionId={}]. Offset will not advance.",
                    event.corporateActionId(), e);
            throw e;
        }
    }
}
