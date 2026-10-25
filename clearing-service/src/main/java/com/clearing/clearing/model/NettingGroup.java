package com.clearing.clearing.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "netting_groups")
public class NettingGroup {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "business_date", nullable = false)
    private String businessDate; // e.g. "2026-06-15"

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected NettingGroup() {
    }

    public NettingGroup(UUID id, String businessDate) {
        this.id = id;
        this.businessDate = businessDate;
    }

    public UUID getId() { return id; }
    public String getBusinessDate() { return businessDate; }
    public Instant getCreatedAt() { return createdAt; }
}
