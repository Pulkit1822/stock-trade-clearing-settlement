package com.clearing.shared.events;

/**
 * Represents the outcome of trade validation in the clearing pipeline.
 *
 * <p>VALIDATED means all checks passed and the trade can proceed to position updates.
 * REJECTED means a business rule was violated (e.g. oversell, unknown instrument)
 * and the trade is routed to the dead-letter queue.
 */
public enum TradeValidationStatus {

    VALIDATED,
    REJECTED
}
