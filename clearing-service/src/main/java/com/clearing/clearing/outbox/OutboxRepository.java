package com.clearing.clearing.outbox;

import com.clearing.shared.outbox.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {
    
    /**
     * Find oldest unpublished events.
     */
    List<OutboxEvent> findAllByOrderByCreatedAtAsc(Pageable pageable);
}
