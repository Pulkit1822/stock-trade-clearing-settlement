package com.clearing.shared.exception;

/**
 * Thrown when a trade references a buyer or seller account that does not exist
 * or is not in ACTIVE status.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity in the global exception handler.
 */
public class AccountNotFoundException extends RuntimeException {

    private final String accountId;

    public AccountNotFoundException(String accountId) {
        super("Account not found or not active: " + accountId);
        this.accountId = accountId;
    }

    public String getAccountId() {
        return accountId;
    }
}
