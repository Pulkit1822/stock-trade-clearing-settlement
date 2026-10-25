package com.clearing.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to the {@code corporate-action-events} Kafka topic when a
 * corporate action (dividend or stock split) affects an instrument.
 *
 * <p>Partition key: {@code instrumentId}
 * <p>Producer: CorporateActionService
 * <p>Consumer: PositionService (to apply position adjustments)
 *
 * <p>Note: CorporateActionService is a skeleton implementation in Phase 1.
 * This event contract is defined now to allow consumers to wire up listeners.
 */
public record CorporateActionEvent(

        /** Unique ID for this corporate action event. */
        String corporateActionId,

        /** The instrument affected by this corporate action. */
        String instrumentId,

        /** Type of corporate action. */
        CorporateActionType actionType,

        /**
         * Payload value for the action.
         * For DIVIDEND: dividend amount per share.
         * For STOCK_SPLIT: split ratio (e.g. 2.0 for a 2-for-1 split).
         */
        BigDecimal actionValue,

        /** UTC timestamp when the corporate action was published. */
        Instant timestamp
) {
}
