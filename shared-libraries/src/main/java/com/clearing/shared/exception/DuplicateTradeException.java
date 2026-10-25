package com.clearing.shared.exception;

/**
 * Thrown when a trade submission is rejected because a trade with the same
 * tradeId already exists in the database.
 *
 * <p>Maps to HTTP 409 Conflict in the global exception handler.
 * This is the primary idempotency guard at the service boundary.
 */
public class DuplicateTradeException extends RuntimeException {

    private final String tradeId;

    public DuplicateTradeException(String tradeId) {
        super("Trade already exists with id: " + tradeId);
        this.tradeId = tradeId;
    }

    public String getTradeId() {
        return tradeId;
    }
}
