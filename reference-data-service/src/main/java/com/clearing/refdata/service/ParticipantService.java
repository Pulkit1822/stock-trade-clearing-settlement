package com.clearing.refdata.service;

import com.clearing.refdata.kafka.ReferenceDataEventPublisher;
import com.clearing.refdata.model.Participant;
import com.clearing.refdata.model.ParticipantStatus;
import com.clearing.refdata.repository.ParticipantRepository;
import com.clearing.shared.events.ReferenceDataEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ParticipantService {

    private static final Logger log = LoggerFactory.getLogger(ParticipantService.class);

    private final ParticipantRepository participantRepository;
    private final ReferenceDataEventPublisher eventPublisher;

    public ParticipantService(ParticipantRepository participantRepository,
                               ReferenceDataEventPublisher eventPublisher) {
        this.participantRepository = participantRepository;
        this.eventPublisher = eventPublisher;
    }

    @Cacheable(value = "participants", key = "#participantId")
    public Optional<Participant> findByParticipantId(String participantId) {
        log.info("Cache miss — loading participant [{}] from database", participantId);
        return participantRepository.findByParticipantId(participantId);
    }

    public List<Participant> findAll() {
        return participantRepository.findAll();
    }

    public List<Participant> findByStatus(ParticipantStatus status) {
        return participantRepository.findByStatus(status);
    }

    @Transactional
    public Participant create(String participantId, String name) {
        Participant participant = new Participant(
                UUID.randomUUID(), participantId, name, ParticipantStatus.ACTIVE);
        Participant saved = participantRepository.save(participant);
        log.info("Participant created [participantId={}]", participantId);

        eventPublisher.publish(new ReferenceDataEvent(
                participantId, "PARTICIPANT", "CREATED", Instant.now()));
        return saved;
    }

    @Transactional
    @CacheEvict(value = "participants", key = "#participantId")
    public Optional<Participant> update(String participantId, String name) {
        return participantRepository.findByParticipantId(participantId).map(participant -> {
            participant.setName(name);
            Participant saved = participantRepository.save(participant);
            log.info("Participant updated [participantId={}]", participantId);

            eventPublisher.publish(new ReferenceDataEvent(
                    participantId, "PARTICIPANT", "UPDATED", Instant.now()));
            return saved;
        });
    }

    @Transactional
    @CacheEvict(value = "participants", key = "#participantId")
    public Optional<Participant> suspend(String participantId) {
        return participantRepository.findByParticipantId(participantId).map(participant -> {
            participant.setStatus(ParticipantStatus.SUSPENDED);
            Participant saved = participantRepository.save(participant);
            log.info("Participant suspended [participantId={}]", participantId);

            eventPublisher.publish(new ReferenceDataEvent(
                    participantId, "PARTICIPANT", "DEACTIVATED", Instant.now()));
            return saved;
        });
    }
}
