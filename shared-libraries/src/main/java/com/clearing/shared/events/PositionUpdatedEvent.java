package com.clearing.shared.events;

import java.time.Instant;

/**
 * Event published to the {@code position-updated} Kafka topic after PositionService
 * atomically updates both the buyer and seller positions for a trade.
 *
 * <p>Partition key: {@code accountId} — ensures ordered processing per account.
 * <p>Producer: PositionService
 * <p>Consumer: ClearingService
 *
 * <p>One event is emitted per account side (buyer and seller) affected by the trade.
 */
public record PositionUpdatedEvent(

        /** Account whose position was updated. */
        String accountId,

        /** Instrument for which the position changed. */
        String instrumentId,

        /**
         * Net quantity change applied.
         * Positive = shares added (buyer side).
         * Negative = shares deducted (seller side).
         */
        Integer quantityDelta,

        /** Remaining quantity held by this account after the update. */
        Integer newQuantity,

        /** The trade that triggered this position update. */
        String tradeId,

        /** UTC timestamp when the position was committed to the database. */
        Instant timestamp
) {
}
