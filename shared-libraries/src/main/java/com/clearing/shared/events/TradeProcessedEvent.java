package com.clearing.shared.events;

import java.time.Instant;

/**
 * Event published to the {@code trade-processed} Kafka topic after both buyer
 * and seller positions have been successfully updated in the database.
 *
 * <p>Partition key: {@code tradeId}
 * <p>Producer: PositionService
 * <p>Consumer: ClearingService
 */
public record TradeProcessedEvent(

        /** The trade identifier. */
        String tradeId,

        /** UTC timestamp when position updates were committed. */
        Instant timestamp
) {
}
