package com.clearing.refdata.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "instruments")
public class Instrument implements Serializable {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "instrument_id", nullable = false, unique = true, length = 20)
    private String instrumentId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "asset_class", nullable = false, length = 30)
    private String assetClass;

    @Column(name = "currency", nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private InstrumentStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Instrument() {
    }

    public Instrument(UUID id, String instrumentId, String name, String assetClass,
                      String currency, InstrumentStatus status) {
        this.id = id;
        this.instrumentId = instrumentId;
        this.name = name;
        this.assetClass = assetClass;
        this.currency = currency;
        this.status = status;
    }

    public UUID getId() { return id; }

    public String getInstrumentId() { return instrumentId; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAssetClass() { return assetClass; }
    public void setAssetClass(String assetClass) { this.assetClass = assetClass; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public InstrumentStatus getStatus() { return status; }
    public void setStatus(InstrumentStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
