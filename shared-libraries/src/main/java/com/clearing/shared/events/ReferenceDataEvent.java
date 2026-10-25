package com.clearing.shared.events;

import java.time.Instant;

/**
 * Event published to the {@code reference-data-events} Kafka topic when an
 * instrument or participant record is created, updated, or deactivated in
 * ReferenceDataService.
 *
 * <p>Partition key: {@code entityId}
 * <p>Producer: ReferenceDataService
 * <p>Consumer: TradeService (to invalidate instrument cache)
 */
public record ReferenceDataEvent(

        /** ID of the entity that changed (instrument_id or participant_id). */
        String entityId,

        /**
         * Type of entity that changed.
         * Expected values: "INSTRUMENT", "PARTICIPANT"
         */
        String entityType,

        /**
         * Type of change applied.
         * Expected values: "CREATED", "UPDATED", "DEACTIVATED"
         */
        String changeType,

        /** UTC timestamp when the change was committed. */
        Instant timestamp
) {
}
