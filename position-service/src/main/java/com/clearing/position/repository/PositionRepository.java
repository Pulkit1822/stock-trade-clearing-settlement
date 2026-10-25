package com.clearing.position.repository;

import com.clearing.position.model.Position;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PositionRepository extends JpaRepository<Position, UUID> {

    /**
     * Looks up an account's position for a specific instrument.
     * 
     * CRITICAL CLEARING ENGINEERING:
     * We use a strong PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE in PostgreSQL).
     * High-frequency trades happening on the same account/stock at the same millisecond 
     * will be strictly serialized by the database, guaranteeing atomic quantity updates
     * without lost updates or race conditions.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<Position> findByAccountIdAndInstrumentId(String accountId, String instrumentId);

    /**
     * Looks up all positions for a given instrument (used for bulk Corporate Action processing).
     */
    org.springframework.data.domain.Page<Position> findByInstrumentId(String instrumentId, org.springframework.data.domain.Pageable pageable);
}
