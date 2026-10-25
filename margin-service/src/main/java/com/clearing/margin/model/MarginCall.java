package com.clearing.margin.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "margin_calls")
public class MarginCall {

    @Id
    private UUID id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(name = "deficit_amount", nullable = false)
    private BigDecimal deficitAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MarginCallStatus status;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected MarginCall() {}

    public MarginCall(UUID id, String accountId, BigDecimal deficitAmount) {
        this.id = id;
        this.accountId = accountId;
        this.deficitAmount = deficitAmount;
        this.status = MarginCallStatus.OPEN;
        this.issuedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getAccountId() { return accountId; }
    public BigDecimal getDeficitAmount() { return deficitAmount; }
    public MarginCallStatus getStatus() { return status; }
    public void setStatus(MarginCallStatus status) { this.status = status; }
    public Instant getIssuedAt() { return issuedAt; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
}
