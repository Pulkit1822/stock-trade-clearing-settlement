package com.clearing.trade.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "trades")
public class Trade {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "buyer_account_id", nullable = false, length = 50)
    private String buyerAccountId;

    @Column(name = "seller_account_id", nullable = false, length = 50)
    private String sellerAccountId;

    @Column(name = "instrument_id", nullable = false, length = 50)
    private String instrumentId;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal quantity;

    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal price;

    @Column(name = "trade_date", nullable = false)
    private Instant tradeDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private TradeStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    // Protected no-args constructor for Hibernate
    protected Trade() {
    }

    public Trade(UUID id, String buyerAccountId, String sellerAccountId, String instrumentId, BigDecimal quantity, BigDecimal price, Instant tradeDate, TradeStatus status) {
        this.id = id;
        this.buyerAccountId = buyerAccountId;
        this.sellerAccountId = sellerAccountId;
        this.instrumentId = instrumentId;
        this.quantity = quantity;
        this.price = price;
        this.tradeDate = tradeDate;
        this.status = status;
    }

    // Getters and Setters
    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getBuyerAccountId() { return buyerAccountId; }
    public void setBuyerAccountId(String buyerAccountId) { this.buyerAccountId = buyerAccountId; }

    public String getSellerAccountId() { return sellerAccountId; }
    public void setSellerAccountId(String sellerAccountId) { this.sellerAccountId = sellerAccountId; }

    public String getInstrumentId() { return instrumentId; }
    public void setInstrumentId(String instrumentId) { this.instrumentId = instrumentId; }

    public BigDecimal getQuantity() { return quantity; }
    public void setQuantity(BigDecimal quantity) { this.quantity = quantity; }

    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }

    public Instant getTradeDate() { return tradeDate; }
    public void setTradeDate(Instant tradeDate) { this.tradeDate = tradeDate; }

    public TradeStatus getStatus() { return status; }
    public void setStatus(TradeStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
