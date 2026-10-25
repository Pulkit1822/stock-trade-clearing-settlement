package com.clearing.clearing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "settlements")
public class Settlement {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "trade_id", nullable = false, unique = true, length = 36)
    private String tradeId;

    @Column(name = "buyer_account_id", length = 50)
    private String buyerAccountId;

    @Column(name = "seller_account_id", length = 50)
    private String sellerAccountId;

    @Column(name = "instrument_id", length = 50)
    private String instrumentId;

    @Column(name = "buyer_confirmed", nullable = false)
    private boolean buyerConfirmed = false;

    @Column(name = "seller_confirmed", nullable = false)
    private boolean sellerConfirmed = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private SettlementStatus status;

    @Column(name = "failure_reason", length = 500)
    private String failureReason;

    @Column(name = "settled_at")
    private Instant settledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Settlement() {
    }

    public Settlement(UUID id, String tradeId, SettlementStatus status) {
        this.id = id;
        this.tradeId = tradeId;
        this.status = status;
    }

    public UUID getId() { return id; }

    public String getTradeId() { return tradeId; }

    public String getBuyerAccountId() { return buyerAccountId; }
    public void setBuyerAccountId(String buyerAccountId) { this.buyerAccountId = buyerAccountId; }

    public String getSellerAccountId() { return sellerAccountId; }
    public void setSellerAccountId(String sellerAccountId) { this.sellerAccountId = sellerAccountId; }

    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }

    public boolean isBuyerConfirmed() { return buyerConfirmed; }
    public void setBuyerConfirmed(boolean buyerConfirmed) { this.buyerConfirmed = buyerConfirmed; }

    public boolean isSellerConfirmed() { return sellerConfirmed; }
    public void setSellerConfirmed(boolean sellerConfirmed) { this.sellerConfirmed = sellerConfirmed; }

    public SettlementStatus getStatus() { return status; }
    public void setStatus(SettlementStatus status) { this.status = status; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }

    public Instant getCreatedAt() { return createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
}
