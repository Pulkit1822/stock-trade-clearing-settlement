package com.clearing.shared.events;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Event published to the {@code margin-call-issued} topic when an account's
 * required margin exceeds their posted collateral.
 */
public record MarginCallEvent(
        String marginCallId,
        String accountId,
        BigDecimal requiredMargin,
        BigDecimal availableCollateral,
        BigDecimal deficitAmount,
        Instant issuedAt
) {
}
