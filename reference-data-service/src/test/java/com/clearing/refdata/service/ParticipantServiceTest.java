package com.clearing.refdata.service;

import com.clearing.refdata.kafka.ReferenceDataEventPublisher;
import com.clearing.refdata.model.Participant;
import com.clearing.refdata.model.ParticipantStatus;
import com.clearing.refdata.repository.ParticipantRepository;
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
class ParticipantServiceTest {

    @Mock
    private ParticipantRepository participantRepository;

    @Mock
    private ReferenceDataEventPublisher eventPublisher;

    @InjectMocks
    private ParticipantService participantService;

    @Test
    @DisplayName("create persists participant with ACTIVE status and publishes CREATED event")
    void create_persistsAndPublishesCreatedEvent() {
        when(participantRepository.save(any(Participant.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        Participant result = participantService.create("ACC-001", "Acme Capital");

        assertEquals("ACC-001", result.getParticipantId());
        assertEquals(ParticipantStatus.ACTIVE, result.getStatus());

        ArgumentCaptor<ReferenceDataEvent> captor = ArgumentCaptor.forClass(ReferenceDataEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("PARTICIPANT", captor.getValue().entityType());
        assertEquals("CREATED", captor.getValue().changeType());
    }

    @Test
    @DisplayName("suspend sets status to SUSPENDED and publishes DEACTIVATED event")
    void suspend_setsSuspendedAndPublishesEvent() {
        Participant existing = new Participant(UUID.randomUUID(), "ACC-001", "Acme Capital", ParticipantStatus.ACTIVE);
        when(participantRepository.findByParticipantId("ACC-001")).thenReturn(Optional.of(existing));
        when(participantRepository.save(any(Participant.class))).thenAnswer(inv -> inv.getArgument(0));

        Optional<Participant> result = participantService.suspend("ACC-001");

        assertTrue(result.isPresent());
        assertEquals(ParticipantStatus.SUSPENDED, result.get().getStatus());

        ArgumentCaptor<ReferenceDataEvent> captor = ArgumentCaptor.forClass(ReferenceDataEvent.class);
        verify(eventPublisher).publish(captor.capture());
        assertEquals("DEACTIVATED", captor.getValue().changeType());
    }

    @Test
    @DisplayName("suspend returns empty when participant not found")
    void suspend_notFound_returnsEmpty() {
        when(participantRepository.findByParticipantId("UNKNOWN")).thenReturn(Optional.empty());

        Optional<Participant> result = participantService.suspend("UNKNOWN");

        assertTrue(result.isEmpty());
        verify(eventPublisher, never()).publish(any());
    }
}
