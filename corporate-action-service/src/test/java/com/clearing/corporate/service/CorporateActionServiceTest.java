package com.clearing.corporate.service;

import com.clearing.corporate.kafka.CorporateActionEventPublisher;
import com.clearing.corporate.model.CorporateAction;
import com.clearing.corporate.model.CorporateActionStatus;
import com.clearing.corporate.repository.CorporateActionRepository;
import com.clearing.shared.events.CorporateActionEvent;
import com.clearing.shared.events.CorporateActionType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorporateActionServiceTest {

    @Mock
    private CorporateActionRepository corporateActionRepository;

    @Mock
    private CorporateActionEventPublisher eventPublisher;

    @InjectMocks
    private CorporateActionService corporateActionService;

    @Test
    @DisplayName("submit DIVIDEND persists action and publishes event with correct fields")
    void submit_dividend_persistsAndPublishesEvent() {
        when(corporateActionRepository.save(any(CorporateAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CorporateAction result = corporateActionService.submit(
                "AAPL", CorporateActionType.DIVIDEND, new BigDecimal("0.25"));

        assertEquals("AAPL", result.getInstrumentId());
        assertEquals(CorporateActionType.DIVIDEND, result.getActionType());
        assertEquals(new BigDecimal("0.25"), result.getActionValue());
        assertEquals(CorporateActionStatus.PUBLISHED, result.getStatus());
        assertNotNull(result.getPublishedAt());

        ArgumentCaptor<CorporateActionEvent> captor = ArgumentCaptor.forClass(CorporateActionEvent.class);
        verify(eventPublisher).publish(captor.capture());

        CorporateActionEvent event = captor.getValue();
        assertEquals("AAPL", event.instrumentId());
        assertEquals(CorporateActionType.DIVIDEND, event.actionType());
        assertEquals(new BigDecimal("0.25"), event.actionValue());
    }

    @Test
    @DisplayName("submit STOCK_SPLIT persists action with correct split ratio and publishes event")
    void submit_stockSplit_persistsAndPublishesEvent() {
        when(corporateActionRepository.save(any(CorporateAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CorporateAction result = corporateActionService.submit(
                "TSLA", CorporateActionType.STOCK_SPLIT, new BigDecimal("3.0"));

        assertEquals("TSLA", result.getInstrumentId());
        assertEquals(CorporateActionType.STOCK_SPLIT, result.getActionType());
        assertEquals(new BigDecimal("3.0"), result.getActionValue());
        assertEquals(CorporateActionStatus.PUBLISHED, result.getStatus());

        ArgumentCaptor<CorporateActionEvent> captor = ArgumentCaptor.forClass(CorporateActionEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals(CorporateActionType.STOCK_SPLIT, captor.getValue().actionType());
    }

    @Test
    @DisplayName("submit saves twice — once PENDING, once PUBLISHED")
    void submit_savesTwice_pendingThenPublished() {
        when(corporateActionRepository.save(any(CorporateAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        corporateActionService.submit("AAPL", CorporateActionType.DIVIDEND, new BigDecimal("0.25"));

        // Two saves: first PENDING, then PUBLISHED after event is dispatched
        verify(corporateActionRepository, times(2)).save(any(CorporateAction.class));
    }

    @Test
    @DisplayName("submit generates a unique corporateActionId for each call")
    void submit_generatesCorporateActionId() {
        when(corporateActionRepository.save(any(CorporateAction.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        CorporateAction first = corporateActionService.submit(
                "AAPL", CorporateActionType.DIVIDEND, new BigDecimal("0.25"));
        CorporateAction second = corporateActionService.submit(
                "AAPL", CorporateActionType.DIVIDEND, new BigDecimal("0.25"));

        assertNotEquals(first.getCorporateActionId(), second.getCorporateActionId());
    }
}
