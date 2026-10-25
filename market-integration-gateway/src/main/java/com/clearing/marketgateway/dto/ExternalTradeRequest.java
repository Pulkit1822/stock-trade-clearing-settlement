package com.clearing.marketgateway.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Inbound trade payload from external brokers or market venues.
 *
 * <p>This is the external-facing contract. Field names follow broker convention
 * (ticker, pricePerShare, executionTimestamp) rather than internal clearing
 * platform naming (instrumentId, price, tradeDate).
 *
 * <p>Translated to {@link com.clearing.shared.dto.TradeRequest} by
 * {@link com.clearing.marketgateway.translator.TradeTranslator} before forwarding
 * to TradeService.
 */
public record ExternalTradeRequest(

        /** Broker's own reference ID for this trade execution. */
        @NotBlank(message = "brokerTradeId is required")
        String brokerTradeId,

        /** Instrument ticker symbol (e.g. "AAPL", "TSLA"). */
        @NotBlank(message = "ticker is required")
        String ticker,

        /** Account ID of the buy-side participant. */
        @NotBlank(message = "buyerAccount is required")
        String buyerAccount,

        /** Account ID of the sell-side participant. */
        @NotBlank(message = "sellerAccount is required")
        String sellerAccount,

        /** Number of shares in this execution. Must be at least 1. */
        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        /** Price per share at execution. Must be greater than 0. */
        @NotNull(message = "pricePerShare is required")
        @DecimalMin(value = "0.01", message = "pricePerShare must be greater than 0")
        BigDecimal pricePerShare,

        /** ISO 4217 currency code (e.g. "USD"). Validated but not forwarded in v1. */
        @NotBlank(message = "tradeCurrency is required")
        String tradeCurrency,

        /** UTC timestamp when the trade was matched on the exchange. */
        @NotNull(message = "executionTimestamp is required")
        Instant executionTimestamp
) {
}
