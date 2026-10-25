package com.clearing.shared.exception;

/**
 * Thrown during position validation when the seller does not hold enough shares
 * to fulfil the trade quantity requested.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity in the global exception handler.
 * At the Kafka consumer level, this causes the trade to be routed to
 * {@code trade-dead-letter} with a REJECTED validation status.
 */
public class InsufficientPositionException extends RuntimeException {

    private final String accountId;
    private final String instrumentId;
    private final int requestedQuantity;
    private final int availableQuantity;

    public InsufficientPositionException(
            String accountId,
            String instrumentId,
            int requestedQuantity,
            int availableQuantity) {

        super(String.format(
                "Insufficient position for account %s on instrument %s: "
                        + "requested %d but only %d available",
                accountId, instrumentId, requestedQuantity, availableQuantity));

        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.requestedQuantity = requestedQuantity;
        this.availableQuantity = availableQuantity;
    }

    public String getAccountId() {
        return accountId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public int getRequestedQuantity() {
        return requestedQuantity;
    }

    public int getAvailableQuantity() {
        return availableQuantity;
    }
}
