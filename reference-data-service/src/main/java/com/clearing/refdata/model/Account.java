package com.clearing.refdata.model;

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

/**
 * JPA entity representing a trading account owned by a {@link Participant}.
 *
 * <p>An Account tracks the cash balance available for settlement.
 * Share quantities owned by the account are tracked separately in position-service.
 *
 * <p>Table: {@code accounts}
 * <p>Flyway migration: {@code V2__add_accounts_table.sql}
 */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @Column(columnDefinition = "UUID", updatable = false, nullable = false)
    private UUID id;

    /** Business identifier (e.g. "ACC-001"). Unique across all accounts. */
    @Column(name = "account_id", nullable = false, unique = true, length = 50)
    private String accountId;

    /** The participant who owns this account. FK to participants.participant_id. */
    @Column(name = "participant_id", nullable = false, length = 50)
    private String participantId;

    /** Available cash balance for settlement. Never negative. */
    @Column(precision = 19, scale = 4, nullable = false)
    private BigDecimal balance;

    /** ISO 4217 currency code (e.g. "USD", "GBP"). */
    @Column(nullable = false, length = 10)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccountStatus status;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    /** Protected no-arg constructor for Hibernate. */
    protected Account() {
    }

    public Account(UUID id, String accountId, String participantId,
                   BigDecimal balance, String currency, AccountStatus status) {
        this.id = id;
        this.accountId = accountId;
        this.participantId = participantId;
        this.balance = balance;
        this.currency = currency;
        this.status = status;
    }

    // ── Getters ──────────────────────────────────────────────────────────────

    public UUID getId()              { return id; }
    public String getAccountId()     { return accountId; }
    public String getParticipantId() { return participantId; }
    public BigDecimal getBalance()   { return balance; }
    public String getCurrency()      { return currency; }
    public AccountStatus getStatus() { return status; }
    public Instant getCreatedAt()    { return createdAt; }
    public Instant getUpdatedAt()    { return updatedAt; }

    // ── Setters (only for mutable fields) ─────────────────────────────────────

    public void setBalance(BigDecimal balance)   { this.balance = balance; }
    public void setStatus(AccountStatus status)  { this.status = status; }
}
