package com.clearing.trade.kafka;

import com.clearing.shared.events.SettlementCompletedEvent;
import com.clearing.trade.service.TradeSettlementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Component;

/**
 * Kafka consumer for the {@code settlement-completed} topic.
 *
 * <p>This consumer closes the event loop for the trade pipeline. When ClearingService
 * confirms that both buyer and seller positions are updated, it publishes a
 * {@link SettlementCompletedEvent}. This consumer reacts by updating the original
 * {@code Trade} record status to {@code SETTLED} — satisfying the Week 6 success criterion.
 *
 * <p>Pattern matches {@link com.clearing.notification.kafka.SettlementCompletedConsumer}
 * in notification-service: manual ACK, exception rethrow for DLQ eligibility.
 *
 * <p>Consumer group {@code trade-service-settlement} is intentionally separate from
 * the notification-service group so both services consume the same events independently.
 */
@Component
public class SettlementCompletedConsumer {

    private static final Logger log = LoggerFactory.getLogger(SettlementCompletedConsumer.class);

    private static final String TOPIC_SETTLEMENT_COMPLETED = "settlement-completed";
    private static final String GROUP_ID = "trade-service-settlement";

    private final TradeSettlementService tradeSettlementService;

    public SettlementCompletedConsumer(TradeSettlementService tradeSettlementService) {
        this.tradeSettlementService = tradeSettlementService;
    }

    /**
     * Receives a settlement-completed event and delegates the Trade status update
     * to the service layer. Manual ACK ensures the Kafka offset only advances after
     * the database write succeeds.
     *
     * @param event The deserialized SettlementCompletedEvent from ClearingService.
     * @param ack   Manual acknowledgment handle.
     */
    @KafkaListener(topics = TOPIC_SETTLEMENT_COMPLETED, groupId = GROUP_ID)
    public void consume(SettlementCompletedEvent event, Acknowledgment ack) {
        log.info("Received SettlementCompletedEvent [tradeId={}] from ClearingService. Updating trade status.",
                event.tradeId());

        try {
            tradeSettlementService.markTradeAsSettled(event);
            ack.acknowledge();
            log.info("Trade [{}] status update committed. Kafka offset advanced.", event.tradeId());
        } catch (Exception e) {
            log.error("Failed to update Trade status for SettlementCompletedEvent [tradeId={}]. Offset will not advance.",
                    event.tradeId(), e);
            // Rethrow to allow Spring Kafka's DefaultErrorHandler to apply retry/DLQ routing.
            throw e;
        }
    }
}
