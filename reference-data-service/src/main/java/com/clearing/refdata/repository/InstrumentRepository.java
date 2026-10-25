package com.clearing.refdata.repository;

import com.clearing.refdata.model.Instrument;
import com.clearing.refdata.model.InstrumentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface InstrumentRepository extends JpaRepository<Instrument, UUID> {

    Optional<Instrument> findByInstrumentId(String instrumentId);

    List<Instrument> findByStatus(InstrumentStatus status);
}
