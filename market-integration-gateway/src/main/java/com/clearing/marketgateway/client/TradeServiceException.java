package com.clearing.marketgateway.client;

/**
 * Thrown when TradeService returns an error or is unreachable.
 * Maps to HTTP 502 Bad Gateway at the controller layer.
 */
public class TradeServiceException extends RuntimeException {

    public TradeServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
