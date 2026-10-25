package com.clearing.clearing.repository;

import com.clearing.clearing.model.Settlement;
import com.clearing.clearing.model.SettlementStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface SettlementRepository extends JpaRepository<Settlement, UUID> {

    /**
     * Look up a settlement by its trade identifier (Read-Only, no lock).
     */
    Optional<Settlement> findByTradeId(String tradeId);

    /**
     * Look up a settlement by its trade identifier with a pessimistic lock.
     *
     * PESSIMISTIC_WRITE lock (SELECT ... FOR UPDATE) prevents concurrent
     * position-updated events for the same trade from racing to update
     * buyer/seller confirmation flags simultaneously.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("SELECT s FROM Settlement s WHERE s.tradeId = :tradeId")
    Optional<Settlement> findLockedByTradeId(@org.springframework.data.repository.query.Param("tradeId") String tradeId);

    /**
     * Find all settlements in a given status — used to query PENDING
     * settlements for monitoring and reconciliation.
     */
    List<Settlement> findByStatus(SettlementStatus status);

    /**
     * Find all settlements involving a participant account on either side.
     */
    List<Settlement> findByBuyerAccountIdOrSellerAccountId(String buyerAccountId, String sellerAccountId);
}
