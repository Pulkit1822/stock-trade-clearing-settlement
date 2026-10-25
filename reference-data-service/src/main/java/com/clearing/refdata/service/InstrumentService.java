package com.clearing.refdata.service;

import com.clearing.refdata.kafka.ReferenceDataEventPublisher;
import com.clearing.refdata.model.Instrument;
import com.clearing.refdata.model.InstrumentStatus;
import com.clearing.refdata.repository.InstrumentRepository;
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
public class InstrumentService {

    private static final Logger log = LoggerFactory.getLogger(InstrumentService.class);

    private final InstrumentRepository instrumentRepository;
    private final ReferenceDataEventPublisher eventPublisher;

    public InstrumentService(InstrumentRepository instrumentRepository,
                              ReferenceDataEventPublisher eventPublisher) {
        this.instrumentRepository = instrumentRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Cached instrument lookup by instrumentId (e.g. "AAPL").
     * Cache name: "instruments", key: instrumentId.
     * TTL: 10 minutes (configured in application.yml).
     */
    @Cacheable(value = "instruments", key = "#instrumentId")
    public Optional<Instrument> findByInstrumentId(String instrumentId) {
        log.info("Cache miss — loading instrument [{}] from database", instrumentId);
        return instrumentRepository.findByInstrumentId(instrumentId);
    }

    public List<Instrument> findAll() {
        return instrumentRepository.findAll();
    }

    public List<Instrument> findByStatus(InstrumentStatus status) {
        return instrumentRepository.findByStatus(status);
    }

    @Transactional
    public Instrument create(String instrumentId, String name, String assetClass, String currency) {
        Instrument instrument = new Instrument(
                UUID.randomUUID(), instrumentId, name, assetClass, currency, InstrumentStatus.ACTIVE);
        Instrument saved = instrumentRepository.save(instrument);
        log.info("Instrument created [instrumentId={}]", instrumentId);

        eventPublisher.publish(new ReferenceDataEvent(
                instrumentId, "INSTRUMENT", "CREATED", Instant.now()));
        return saved;
    }

    /**
     * Evicts cached entry on update so next read fetches fresh data from DB.
     */
    @Transactional
    @CacheEvict(value = "instruments", key = "#instrumentId")
    public Optional<Instrument> update(String instrumentId, String name, String assetClass, String currency) {
        return instrumentRepository.findByInstrumentId(instrumentId).map(instrument -> {
            instrument.setName(name);
            instrument.setAssetClass(assetClass);
            instrument.setCurrency(currency);
            Instrument saved = instrumentRepository.save(instrument);
            log.info("Instrument updated [instrumentId={}]", instrumentId);

            eventPublisher.publish(new ReferenceDataEvent(
                    instrumentId, "INSTRUMENT", "UPDATED", Instant.now()));
            return saved;
        });
    }

    @Transactional
    @CacheEvict(value = "instruments", key = "#instrumentId")
    public Optional<Instrument> deactivate(String instrumentId) {
        return instrumentRepository.findByInstrumentId(instrumentId).map(instrument -> {
            instrument.setStatus(InstrumentStatus.INACTIVE);
            Instrument saved = instrumentRepository.save(instrument);
            log.info("Instrument deactivated [instrumentId={}]", instrumentId);

            eventPublisher.publish(new ReferenceDataEvent(
                    instrumentId, "INSTRUMENT", "DEACTIVATED", Instant.now()));
            return saved;
        });
    }
}
