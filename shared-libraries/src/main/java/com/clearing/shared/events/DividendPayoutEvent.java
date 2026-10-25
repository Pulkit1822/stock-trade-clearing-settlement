package com.clearing.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to the {@code dividend-payout-events} Kafka topic by the PositionService
 * when a cash dividend corporate action is processed.
 *
 * <p>Partition key: {@code accountId}
 * <p>Producer: PositionService
 * <p>Consumer: ReferenceDataService (to credit account balance), NotificationService (to notify participant)
 */
public record DividendPayoutEvent(

        /** The unique identifier of the corporate action that triggered this. */
        String corporateActionId,

        /** The account receiving the dividend. */
        String accountId,

        /** The instrument that paid the dividend. */
        String instrumentId,

        /** Total cash amount disbursed to this account. */
        BigDecimal amount,

        /** UTC timestamp when the payout calculation completed. */
        Instant timestamp
) {
}
