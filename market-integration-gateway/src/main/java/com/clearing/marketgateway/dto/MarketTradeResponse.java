package com.clearing.marketgateway.dto;

import java.time.Instant;

/**
 * Response returned to the external broker after a trade is accepted
 * by the clearing platform.
 *
 * <p>Contains the internal clearing tradeId alongside the broker's
 * own reference so the broker can correlate their submission.
 */
public record MarketTradeResponse(

        /** Internal clearing platform trade ID (UUID). */
        String clearingTradeId,

        /** Broker's original reference ID echoed back for correlation. */
        String brokerTradeId,

        /** Ticker submitted. */
        String ticker,

        /** Acknowledgement status. Always "ACCEPTED" on HTTP 201. */
        String status,

        /** UTC timestamp when the clearing platform accepted the trade. */
        Instant acceptedAt
) {
}
