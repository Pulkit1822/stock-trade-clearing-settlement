package com.clearing.shared.events;

import java.time.Instant;

/**
 * Event published to the {@code trade-validated} Kafka topic after the
 * PositionService validates whether the seller has sufficient position.
 *
 * <p>Partition key: {@code tradeId}
 * <p>Producer: PositionService
 * <p>Consumers: PositionService (for position updates), ClearingService
 *
 * <p>If status is REJECTED, the trade is routed to {@code trade-dead-letter}.
 * If status is VALIDATED, PositionService proceeds to update buyer and seller positions.
 */
public record TradeValidatedEvent(

        /** The trade identifier. Matches the tradeId from TradeSubmittedEvent. */
        String tradeId,

        /**
         * Result of the validation check.
         * VALIDATED = seller has sufficient shares, trade can proceed.
         * REJECTED = seller position insufficient, or other business rule failed.
         */
        TradeValidationStatus status,

        /**
         * Optional human-readable reason for rejection.
         * Null when status is VALIDATED.
         */
        String rejectionReason,

        /** UTC timestamp when validation completed. */
        Instant timestamp
) {
}
