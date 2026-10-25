package com.clearing.shared.events;

import java.time.Instant;

/**
 * Event published to the {@code notification-events} Kafka topic by NotificationService
 * after generating and dispatching a participant notification about a settlement.
 *
 * <p>Partition key: {@code accountId}
 * <p>Producer: NotificationService
 * <p>Consumer: none — terminal event, consumed by monitoring/log pipeline
 */
public record NotificationEvent(

        /** The account this notification was sent to. */
        String accountId,

        /** The settled trade this notification is about. */
        String tradeId,

        /** Human-readable notification message. */
        String message,

        /** UTC timestamp when the notification was dispatched. */
        Instant timestamp
) {
}
