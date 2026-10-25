package com.clearing.corporate.model;

import com.clearing.shared.events.CorporateActionType;
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
@Table(name = "corporate_actions")
public class CorporateAction {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "corporate_action_id", nullable = false, unique = true, length = 36)
    private String corporateActionId;

    @Column(name = "instrument_id", nullable = false, length = 20)
    private String instrumentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 20)
    private CorporateActionType actionType;

    /**
     * DIVIDEND: dividend amount per share (e.g. 0.25 = $0.25/share).
     * STOCK_SPLIT: split ratio (e.g. 2.0 = 2-for-1 split).
     */
    @Column(name = "action_value", nullable = false, precision = 19, scale = 4)
    private BigDecimal actionValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CorporateActionStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CorporateAction() {
    }

    public CorporateAction(UUID id, String corporateActionId, String instrumentId,
                            CorporateActionType actionType, BigDecimal actionValue,
                            CorporateActionStatus status) {
        this.id = id;
        this.corporateActionId = corporateActionId;
        this.instrumentId = instrumentId;
        this.actionType = actionType;
        this.actionValue = actionValue;
        this.status = status;
    }

    public UUID getId() { return id; }
    public String getCorporateActionId() { return corporateActionId; }
    public String getInstrumentId() { return instrumentId; }
    public CorporateActionType getActionType() { return actionType; }
    public BigDecimal getActionValue() { return actionValue; }
    public CorporateActionStatus getStatus() { return status; }
    public void setStatus(CorporateActionStatus status) { this.status = status; }
    public Instant getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Instant publishedAt) { this.publishedAt = publishedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
