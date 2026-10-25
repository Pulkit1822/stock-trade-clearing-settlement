package com.clearing.margin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "margin_requirements")
public class MarginRequirement {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false, unique = true)
    private String accountId;

    @Column(name = "required_margin", nullable = false)
    private BigDecimal requiredMargin;

    @Column(name = "last_calculated_at")
    private Instant lastCalculatedAt;

    protected MarginRequirement() {}

    public MarginRequirement(UUID id, String accountId) {
        this.id = id;
        this.accountId = accountId;
        this.requiredMargin = BigDecimal.ZERO;
        this.lastCalculatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public BigDecimal getRequiredMargin() { return requiredMargin; }
    public void setRequiredMargin(BigDecimal requiredMargin) { this.requiredMargin = requiredMargin; }
    public Instant getLastCalculatedAt() { return lastCalculatedAt; }
    public void setLastCalculatedAt(Instant lastCalculatedAt) { this.lastCalculatedAt = lastCalculatedAt; }
}
