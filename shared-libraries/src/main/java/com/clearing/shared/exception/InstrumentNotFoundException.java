package com.clearing.shared.exception;

/**
 * Thrown when a trade references an instrument that does not exist or is not
 * in ACTIVE status in the ReferenceDataService.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity in the global exception handler.
 */
public class InstrumentNotFoundException extends RuntimeException {

    private final String instrumentId;

    public InstrumentNotFoundException(String instrumentId) {
        super("Instrument not found or not active: " + instrumentId);
        this.instrumentId = instrumentId;
    }

    public String getInstrumentId() {
        return instrumentId;
    }
}
