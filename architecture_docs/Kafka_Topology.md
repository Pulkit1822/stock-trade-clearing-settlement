# 📬 Kafka Topology & Event Streaming Specification

> **Platform:** Post-Trade Clearing & Settlement Platform  
> **Author & Maintainer:** Pulkit Mathur ([@Pulkit1822](https://github.com/Pulkit1822))  
> **Broker Stack:** Apache Kafka 3.x / Confluent 7.5.0 + Zookeeper 2181 | Clustered Event Bus

---

## 1. Event Streaming Architecture Overview

The **Post-Trade Clearing Platform** uses Apache Kafka as its distributed nervous system. Every state transition in a financial trade lifecycle is modeled as an immutable, strictly-ordered domain event.

```mermaid
flowchart LR
    subgraph Producers["Event Producers"]
        TS["Trade Service"]
        PS["Position Service"]
        CS["Clearing Service"]
        RDS["Reference Data"]
        CAS["Corporate Actions"]
    end

    subgraph Broker["Apache Kafka Broker Cluster"]
        T1["trade-submitted\n(3 Partitions)"]
        T2["trade-validated\n(3 Partitions)"]
        T3["position-updated\n(3 Partitions)"]
        T4["settlement-completed\n(3 Partitions)"]
        T5["trade-dead-letter\n(3 Partitions)"]
        T6["reference-data-events\n(Compacted)"]
        T7["corporate-action-events\n(3 Partitions)"]
    end

    subgraph Consumers["Event Consumers"]
        PS_C["Position Service Group"]
        CS_C["Clearing Service Group"]
        TS_C["Trade Service Group"]
        NS_C["Notification Service Group"]
    end

    TS -->|Key: buyerAccountId| T1
    T1 --> PS_C

    PS -->|Key: tradeId| T2
    PS -->|Key: accountId| T3
    PS -.->|Key: tradeId (Rejection)| T5

    T2 --> CS_C
    T3 --> CS_C

    CS -->|Key: tradeId| T4
    T4 --> TS_C
    T4 --> NS_C
    T5 --> NS_C

    RDS -->|Key: instrumentId| T6
    CAS -->|Key: instrumentId| T7
```

---

## 2. Topic Master Catalog

| Topic Name | Producer | Consumer Group(s) | Partition Key | SLA / Semantics | Purpose |
|------------|----------|-------------------|---------------|-----------------|---------|
| `trade-submitted` | `TradeService` | `position-service` | `buyerAccountId` | At-least-once, FIFO per account | Ingested trades awaiting position checks |
| `trade-validated` | `PositionService` | `clearing-service` | `tradeId` | At-least-once, Deduplicated | Result of balance validation (`VALIDATED` / `REJECTED`) |
| `position-updated` | `PositionService` | `clearing-service`, `analytics` | `accountId` | Strictly ordered per account | Double-entry balance debit/credit confirmations |
| `settlement-completed`| `ClearingService` | `trade-service`, `notification-service` | `tradeId` | Exactly-once business semantics | Final DvP settlement confirmation |
| `notification-events` | `NotificationService`| Downstream Dispatchers | `recipientId` | Best-effort with retry | Participant alerts and advisories |
| `reference-data-events`| `ReferenceDataService`| `trade-service`, `position-service` | `instrumentId` | Log compacted | Updates to instrument or market metadata |
| `corporate-action-events`| `CorporateActionService`| `position-service`, `trade-service` | `instrumentId` | Ordered per instrument | Stock split, dividend, and bonus announcements |
| `trade-dead-letter` | All Microservices | Monitoring Alerting System | `tradeId` | Poison-pill / Alerting queue | Trades failing validation, oversell violations, or malformed data |

---

## 3. Event Contracts (Java Records & JSON Payloads)

All events are defined as immutable Java `record` types in `shared-libraries`:

### 1. `TradeSubmittedEvent`
* **Topic:** `trade-submitted`
* **Partition Key:** `buyerAccountId`
* **Java Record:** `com.clearing.shared.events.TradeSubmittedEvent`

```json
{
  "tradeId": "TRD-882019",
  "instrumentId": "AAPL",
  "buyerAccountId": "ACC-BUYER-001",
  "sellerAccountId": "ACC-SELLER-002",
  "quantity": 100,
  "price": 182.50,
  "submittedAt": "2026-10-25T14:30:00.125Z"
}
```

---

### 2. `TradeValidatedEvent`
* **Topic:** `trade-validated`
* **Partition Key:** `tradeId`
* **Java Record:** `com.clearing.shared.events.TradeValidatedEvent`

```json
{
  "tradeId": "TRD-882019",
  "status": "VALIDATED",
  "reason": null,
  "timestamp": "2026-10-25T14:30:00.180Z"
}
```
*(If rejected due to oversell, `status: "REJECTED"` with `reason: "Insufficient position in Seller account"`)*

---

### 3. `PositionUpdatedEvent`
* **Topic:** `position-updated`
* **Partition Key:** `accountId`
* **Java Record:** `com.clearing.shared.events.PositionUpdatedEvent`

```json
{
  "accountId": "ACC-SELLER-002",
  "instrumentId": "AAPL",
  "quantityDelta": -100,
  "newTotalQuantity": 150,
  "tradeId": "TRD-882019",
  "updatedAt": "2026-10-25T14:30:00.210Z"
}
```

---

### 4. `SettlementCompletedEvent`
* **Topic:** `settlement-completed`
* **Partition Key:** `tradeId`
* **Java Record:** `com.clearing.shared.events.SettlementCompletedEvent`

```json
{
  "settlementId": "SET-991204",
  "tradeId": "TRD-882019",
  "status": "SETTLED",
  "settledAt": "2026-10-25T14:30:00.340Z"
}
```

---

## 4. Keying Strategy & Concurrency Guarantees

### Why Key by `accountId`?
In electronic clearing houses, processing two sell transactions for the same account simultaneously across multiple threads can cause race conditions or overdrafts. 
By keying `trade-submitted` and `position-updated` by `accountId`, Apache Kafka's murmur2 hashing guarantees that **all events affecting a specific trading account land on the exact same partition**, guaranteeing sequential, FIFO processing.

---

## 5. Consumer Configuration & Resilience Settings

All Spring Boot microservice consumers use the following production-hardened consumer properties:

```yaml
spring:
  kafka:
    producer:
      properties:
        enable.idempotence: true      # Prevents duplicate broker writes
        acks: all                     # Requires quorum acknowledgment from replicas
        retries: 3                    # Automatic transient retry
        max.in.flight.requests.per.connection: 1
    consumer:
      auto-offset-reset: earliest     # Never lose unconsumed events
      enable-auto-commit: false       # Explicit manual acknowledgment
      properties:
        spring.json.trusted.packages: "com.clearing.shared.events"
    listener:
      ack-mode: manual_immediate      # Commit offset only after successful DB persistence
```
