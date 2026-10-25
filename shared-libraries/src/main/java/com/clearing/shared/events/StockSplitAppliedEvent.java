package com.clearing.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to the {@code stock-split-events} Kafka topic by the PositionService
 * when a stock split corporate action is processed.
 *
 * <p>Partition key: {@code accountId}
 * <p>Producer: PositionService
 * <p>Consumer: NotificationService (to notify participant)
 */
public record StockSplitAppliedEvent(

        /** The unique identifier of the corporate action that triggered this. */
        String corporateActionId,

        /** The account holding the split stock. */
        String accountId,

        /** The instrument that split. */
        String instrumentId,

        /** The old quantity before split. */
        BigDecimal oldQuantity,

        /** The new quantity after split. */
        BigDecimal newQuantity,

        /** UTC timestamp when the split was applied. */
        Instant timestamp
) {
}
