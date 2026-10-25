package com.clearing.margin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "collateral_accounts")
public class CollateralAccount {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true)
    private String accountId;

    @Column(nullable = false)
    private BigDecimal balance;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "updated_at")
    private Instant updatedAt;

    protected CollateralAccount() {}

    public CollateralAccount(UUID id, String accountId) {
        this.id = id;
        this.accountId = accountId;
        this.balance = BigDecimal.ZERO;
        this.currency = "USD";
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public BigDecimal getBalance() { return balance; }
    public void setBalance(BigDecimal balance) { this.balance = balance; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
