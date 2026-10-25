package com.clearing.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published by the Clearing Service when a Multilateral Netting cycle is completed
 * for a specific participant and instrument.
 */
public record NetSettlementCompletedEvent(
        String nettingGroupId,
        String businessDate,
        String accountId,
        String instrumentId,
        long netQuantity,
        BigDecimal netCash,
        Instant nettedAt
) {
}
