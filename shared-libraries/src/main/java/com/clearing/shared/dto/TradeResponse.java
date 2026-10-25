package com.clearing.shared.dto;

/**
 * REST response returned by TradeService after a trade is accepted and persisted.
 *
 * <p>The tradeId is the system-generated UUID that the client can use to
 * poll trade status via GET /trades/{tradeId}.
 */
public record TradeResponse(

        /** System-generated unique trade identifier. */
        String tradeId,

        /**
         * Current status of the trade in the clearing pipeline.
         * Initial value on submission: "SUBMITTED"
         * Subsequent values: "VALIDATED", "PROCESSED", "SETTLED", "REJECTED"
         */
        String status
) {
}
