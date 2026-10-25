package com.clearing.shared.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Inbound REST request payload for submitting a new trade to the platform.
 *
 * <p>Validated at the controller layer in TradeService.
 * All fields are mandatory — partial submissions are rejected with HTTP 400.
 */
public record TradeRequest(

        @NotBlank(message = "instrumentId is required")
        String instrumentId,

        @NotBlank(message = "buyerAccountId is required")
        String buyerAccountId,

        @NotBlank(message = "sellerAccountId is required")
        String sellerAccountId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than 0")
        BigDecimal price
) {
}
