package com.clearing.refdata.service;

import com.clearing.refdata.kafka.ReferenceDataEventPublisher;
import com.clearing.refdata.model.Instrument;
import com.clearing.refdata.model.InstrumentStatus;
import com.clearing.refdata.repository.InstrumentRepository;
import com.clearing.shared.events.ReferenceDataEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InstrumentServiceTest {

    @Mock
    private InstrumentRepository instrumentRepository;

    @Mock
    private ReferenceDataEventPublisher eventPublisher;

    @InjectMocks
    private InstrumentService instrumentService;

    @Test
    @DisplayName("create persists instrument with ACTIVE status and publishes CREATED event")
    void create_persistsAndPublishesCreatedEvent() {
        when(instrumentRepository.save(any(Instrument.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Instrument result = instrumentService.create("AAPL", "Apple Inc.", "EQUITY", "USD");

        assertEquals("AAPL", result.getInstrumentId());
        assertEquals(InstrumentStatus.ACTIVE, result.getStatus());

        ArgumentCaptor<ReferenceDataEvent> captor = ArgumentCaptor.forClass(ReferenceDataEvent.class);
        verify(eventPublisher).publish(captor.capture());

        ReferenceDataEvent event = captor.getValue();
        assertEquals("AAPL", event.entityId());
        assertEquals("INSTRUMENT", event.entityType());
        assertEquals("CREATED", event.changeType());
    }

    @Test
    @DisplayName("update modifies instrument fields, evicts cache, and publishes UPDATED event")
    void update_updatesFieldsAndPublishesUpdatedEvent() {
        Instrument existing = new Instrument(UUID.randomUUID(), "AAPL", "Apple", "EQUITY", "USD", InstrumentStatus.ACTIVE);
        when(instrumentRepository.findByInstrumentId("AAPL")).thenReturn(Optional.of(existing));
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Instrument> result = instrumentService.update("AAPL", "Apple Inc.", "EQUITY", "USD");

        assertTrue(result.isPresent());
        assertEquals("Apple Inc.", result.get().getName());

        ArgumentCaptor<ReferenceDataEvent> captor = ArgumentCaptor.forClass(ReferenceDataEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("UPDATED", captor.getValue().changeType());
    }

    @Test
    @DisplayName("deactivate sets status to INACTIVE and publishes DEACTIVATED event")
    void deactivate_setsInactiveAndPublishesEvent() {
        Instrument existing = new Instrument(UUID.randomUUID(), "AAPL", "Apple Inc.", "EQUITY", "USD", InstrumentStatus.ACTIVE);
        when(instrumentRepository.findByInstrumentId("AAPL")).thenReturn(Optional.of(existing));
        when(instrumentRepository.save(any(Instrument.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Instrument> result = instrumentService.deactivate("AAPL");

        assertTrue(result.isPresent());
        assertEquals(InstrumentStatus.INACTIVE, result.get().getStatus());

        ArgumentCaptor<ReferenceDataEvent> captor = ArgumentCaptor.forClass(ReferenceDataEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("DEACTIVATED", captor.getValue().changeType());
    }

    @Test
    @DisplayName("update returns empty when instrument not found")
    void update_notFound_returnsEmpty() {
        when(instrumentRepository.findByInstrumentId("UNKNOWN")).thenReturn(Optional.empty());

        Optional<Instrument> result = instrumentService.update("UNKNOWN", "X", "EQUITY", "USD");

        assertTrue(result.isEmpty());
        verify(eventPublisher, never()).publish(any());
    }
}
