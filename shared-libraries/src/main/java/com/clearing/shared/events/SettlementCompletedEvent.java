package com.clearing.shared.events;

import java.time.Instant;

/**
 * Event published to the {@code settlement-completed} Kafka topic after
 * ClearingService marks a trade as SETTLED in the database.
 *
 * <p>Partition key: {@code tradeId}
 * <p>Producer: ClearingService
 * <p>Consumer: NotificationService
 *
 * <p>This is the terminal event in the clearing pipeline. Receipt of this
 * event triggers participant notification.
 */
public record SettlementCompletedEvent(

        /** The settled trade identifier. */
        String tradeId,

        /** Account of the buyer whose position was credited. */
        String buyerAccount,

        /** Account of the seller whose position was debited. */
        String sellerAccount,

        /** UTC timestamp when settlement was finalized. */
        Instant timestamp
) {
}
