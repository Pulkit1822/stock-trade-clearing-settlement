package com.clearing.position.kafka;

import com.clearing.position.service.PositionCorporateActionService;
import com.clearing.shared.events.CorporateActionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class CorporateActionConsumer {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionConsumer.class);

    private final PositionCorporateActionService corporateActionService;

    public CorporateActionConsumer(PositionCorporateActionService corporateActionService) {
        this.corporateActionService = corporateActionService;
    }

    @KafkaListener(
            topics = "corporate-action-events",
            groupId = "position-service-corporate-action",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consume(CorporateActionEvent event) {
        log.info("Received CorporateActionEvent [{}] for Instrument [{}] Type [{}]",
                event.corporateActionId(), event.instrumentId(), event.actionType());

        try {
            switch (event.actionType()) {
                case STOCK_SPLIT -> corporateActionService.applyStockSplit(event);
                case DIVIDEND -> corporateActionService.applyDividend(event);
                default -> log.warn("Unsupported CorporateActionType [{}]", event.actionType());
            }
        } catch (Exception e) {
            log.error("Failed to process CorporateActionEvent [{}]. Error: {}", event.corporateActionId(), e.getMessage(), e);
            throw e; // Relying on Kafka listener retry/DLQ mechanism
        }
    }
}
