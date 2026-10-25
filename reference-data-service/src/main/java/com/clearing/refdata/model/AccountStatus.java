package com.clearing.refdata.model;

/**
 * Lifecycle status for a trading account.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — Account can submit and receive trades.</li>
 *   <li>{@link #SUSPENDED} — Account is temporarily frozen; no new trades allowed.</li>
 *   <li>{@link #CLOSED} — Account is permanently closed; no operations allowed.</li>
 * </ul>
 */
public enum AccountStatus {
    ACTIVE,
    SUSPENDED,
    CLOSED
}
