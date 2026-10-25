package com.clearing.corporate.service;

import com.clearing.corporate.kafka.CorporateActionEventPublisher;
import com.clearing.corporate.model.CorporateAction;
import com.clearing.corporate.model.CorporateActionStatus;
import com.clearing.corporate.repository.CorporateActionRepository;
import com.clearing.shared.events.CorporateActionEvent;
import com.clearing.shared.events.CorporateActionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class CorporateActionService {

    private static final Logger log = LoggerFactory.getLogger(CorporateActionService.class);

    private final CorporateActionRepository corporateActionRepository;
    private final CorporateActionEventPublisher eventPublisher;

    public CorporateActionService(CorporateActionRepository corporateActionRepository,
                                   CorporateActionEventPublisher eventPublisher) {
        this.corporateActionRepository = corporateActionRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Submits a new corporate action.
     *
     * DIVIDEND: actionValue = cash amount per share (e.g. 0.25 = $0.25/share).
     * STOCK_SPLIT: actionValue = split ratio (e.g. 2.0 = 2-for-1 split).
     *
     * Flow:
     * 1. Persist with status PENDING.
     * 2. Publish CorporateActionEvent to Kafka (consumed by PositionService).
     * 3. Mark PUBLISHED.
     */
    @Transactional
    public CorporateAction submit(String instrumentId, CorporateActionType actionType, BigDecimal actionValue) {
        String corporateActionId = UUID.randomUUID().toString();

        CorporateAction action = new CorporateAction(
                UUID.randomUUID(), corporateActionId, instrumentId,
                actionType, actionValue, CorporateActionStatus.PENDING);

        corporateActionRepository.save(action);
        log.info("CorporateAction created [id={}, instrument={}, type={}, value={}]",
                corporateActionId, instrumentId, actionType, actionValue);

        CorporateActionEvent event = new CorporateActionEvent(
                corporateActionId, instrumentId, actionType, actionValue, Instant.now());
        eventPublisher.publish(event);

        action.setStatus(CorporateActionStatus.PUBLISHED);
        action.setPublishedAt(Instant.now());
        corporateActionRepository.save(action);

        log.info("CorporateAction PUBLISHED [id={}]", corporateActionId);
        return action;
    }

    public Optional<CorporateAction> findByCorporateActionId(String corporateActionId) {
        return corporateActionRepository.findByCorporateActionId(corporateActionId);
    }

    public List<CorporateAction> findByInstrumentId(String instrumentId) {
        return corporateActionRepository.findByInstrumentId(instrumentId);
    }

    public List<CorporateAction> findByStatus(CorporateActionStatus status) {
        return corporateActionRepository.findByStatus(status);
    }
}
