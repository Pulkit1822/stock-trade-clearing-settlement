package com.clearing.clearing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "net_settlement_obligations")
public class NetSettlementObligation {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "netting_group_id", nullable = false)
    private NettingGroup nettingGroup;

    @Column(name = "account_id", nullable = false, length = 50)
    private String accountId;

    @Column(name = "instrument_id", nullable = false, length = 50)
    private String instrumentId;

    @Column(name = "net_quantity", nullable = false)
    private long netQuantity; // Positive = to receive shares, Negative = to deliver shares

    @Column(name = "net_cash", nullable = false, precision = 18, scale = 4)
    private BigDecimal netCash; // Positive = to receive cash, Negative = to pay cash

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NetSettlementObligation() {
    }

    public NetSettlementObligation(UUID id, NettingGroup nettingGroup, String accountId, String instrumentId, long netQuantity, BigDecimal netCash) {
        this.id = id;
        this.nettingGroup = nettingGroup;
        this.accountId = accountId;
        this.instrumentId = instrumentId;
        this.netQuantity = netQuantity;
        this.netCash = netCash;
    }

    public UUID getId() { return id; }
    public NettingGroup getNettingGroup() { return nettingGroup; }
    public String getAccountId() { return accountId; }
    public String getInstrumentId() { return instrumentId; }
    public long getNetQuantity() { return netQuantity; }
    public BigDecimal getNetCash() { return netCash; }
    public Instant getCreatedAt() { return createdAt; }
}
