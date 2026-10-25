# 🏛️ System Architecture — Post-Trade Clearing & Settlement Platform

> **Document Version:** 2.0.0  
> **Author & Maintainer:** Pulkit Mathur ([@Pulkit1822](https://github.com/Pulkit1822))  
> **Architecture Pattern:** Event-Driven Microservices (Hexagonal + CQRS + Transactional Outbox)  
> **Core Runtime:** Java 21 LTS | Spring Boot 3.3.5 | Apache Kafka 3.x | PostgreSQL 16 | Redis 7.2 | Keycloak 22

---

## 1. System Overview & Core Mission

The **Post-Trade Clearing Platform** is a distributed, event-driven financial transaction processing backend engineered to simulate institutional clearing house (CCP) and central securities depository (CSD) operations.

Following order execution on a matching engine or exchange, this platform handles the post-trade lifecycle:
1. **Trade Intake & Validation**: Real-time schema validation, participant account verification, and idempotency checks.
2. **Pessimistic Position Allocation & Oversell Guard**: Deadlock-free alphabetical balance locking and atomic asset transfers.
3. **Delivery-versus-Payment (DvP) Clearing**: Bilateral cash and security finality ensuring neither party is exposed to principal settlement risk.
4. **Corporate Action Adjustments**: Stock splits, dividends, and bonus share adjustments applied to active holdings.
5. **Real-Time Notification & Auditing**: Multichannel advisories (webhooks/websockets) and immutable audit trails.

---

## 2. High-Level Architectural Topology

```mermaid
flowchart TD
    subgraph Market["Market Participants & External Systems"]
        MP["Trader / Broker-Dealer System"]
    end

    subgraph Edge["Security & Gateway Layer"]
        KC["Keycloak 22\n(OAuth2 / OIDC Auth Server)"]
        GW["API Gateway (:8080)\n• Spring Cloud Gateway\n• JWT Verification\n• Redis Token Bucket Rate Limiter"]
        MIG["Market Integration Gateway (:8088)\n• Protocol Translation\n• External Trade Normalization"]
    end

    subgraph EventBus["Apache Kafka Clustered Event Bus"]
        T_SUB["trade-submitted\n(Key: buyerAccountId)"]
        T_VAL["trade-validated\n(Key: tradeId)"]
        T_POS["position-updated\n(Key: accountId)"]
        T_SET["settlement-completed\n(Key: tradeId)"]
        T_DLQ["trade-dead-letter (DLQ)\n(Key: tradeId)"]
        T_REF["reference-data-events\n(Key: instrumentId)"]
        T_CORP["corporate-action-events\n(Key: instrumentId)"]
    end

    subgraph CoreServices["Domain Microservices"]
        TS["Trade Service (:8081)\n• Ingestion & Persistence\n• Outbox Publisher"]
        PS["Position Service (:8082)\n• Deadlock-Free Locking\n• Oversell Guard"]
        CS["Clearing Service (:8083)\n• DvP Finality\n• Settlement Finalization"]
        RDS["Reference Data Service (:8085)\n• Master Instruments & Accounts\n• Redis Cache-Aside"]
        CAS["Corporate Action Service (:8086)\n• Stock Splits & Dividends"]
        NS["Notification Service (:8084)\n• Participant Alerts\n• Audit History"]
    end

    subgraph Persistence["Storage & Caching Layer"]
        PG[("PostgreSQL 16 (clearing-db)\n• Service-Isolated Schemas\n• Flyway Migrations")]
        RD[("Redis 7.2\n• Instrument Cache & Rate Limits")]
    end

    MP -->|1. Submit REST with JWT| GW
    MP -.->|Auth / Token| KC
    GW --> MIG
    MIG -->|2. Ingest Normalized| TS
    TS <-->|Validate Metadata| RDS
    TS -->|3. Persist Trade| PG
    TS -->|4. Publish| T_SUB

    T_SUB --> PS
    PS <--> PG
    PS <--> RD
    PS -->|5a. Validated Event| T_VAL
    PS -->|5b. Position Updated| T_POS
    PS -.->|Oversell Rejection| T_DLQ

    T_VAL --> CS
    T_POS --> CS
    CS <--> PG
    CS -->|6. Settle DvP| T_SET
    CS -.->|Settlement Failure| T_DLQ

    T_SET --> NS
    T_DLQ --> NS
    NS -->|7. Push Alert via Webhook/WS| MP

    RDS -.->|Cache Invalidation| T_REF
    CAS -.->|Position Adjustments| T_CORP
```

---

## 3. Microservice Catalog & Port Matrix

| Service | Port | Database Schema | Primary Responsibilities |
|---------|:----:|-----------------|--------------------------|
| **`api-gateway`** | `8080` | *None* (Redis cache) | Edge reverse proxy, JWT claim verification, rate limiting, and request correlation tracing |
| **`market-integration-gateway`** | `8088` | *Stateless* | Ingestion of raw external orders (REST/FIX), validation, and translation to canonical `TradeRequest` |
| **`trade-service`** | `8081` | `trades` | Trade entity persistence (`SUBMITTED`), idempotency verification, and event publication |
| **`position-service`** | `8082` | `positions` | Account position ledgers, deterministic lock ordering, balance debits/credits, and oversell guard |
| **`clearing-service`** | `8083` | `settlements` | Multi-event orchestration (`trade-validated` + `position-updated`), DvP finality, and settlement records |
| **`reference-data-service`** | `8085` | `instruments`, `participants` | Master instruments catalog, participant accounts, exchange schedules, and Redis cache-aside |
| **`corporate-action-service`** | `8086` | `corporate_actions` | Stock splits, cash dividends, and bonus allocations modifying holding positions |
| **`notification-service`** | `8084` | `notifications` | Participant advisories, webhook alerts, email dispatch simulation, and audit queries |

---

## 4. End-to-End Trade Clearing Lifecycle

```mermaid
sequenceDiagram
    autonumber

    actor Trader as "Trader / Broker"
    participant Gateway as "API Gateway (8080)"
    participant TradeSvc as "Trade Service (8081)"
    participant RefData as "Reference Data (8085)"
    participant Kafka as "Kafka Event Bus"
    participant PosSvc as "Position Service (8082)"
    participant ClearSvc as "Clearing Service (8083)"
    participant NotifSvc as "Notification Service (8084)"

    Trader->>Gateway: POST /api/v1/trades (Bearer JWT)
    Gateway->>TradeSvc: Validated & Forwarded Request
    TradeSvc->>RefData: Validate Instrument & Participant Status
    RefData-->>TradeSvc: Status Active & Accounts Verified
    TradeSvc->>TradeSvc: Persist Trade [SUBMITTED] via Flyway Schema
    TradeSvc->>Kafka: Publish 'trade-submitted' (Key: buyerAccountId)
    TradeSvc-->>Gateway: HTTP 201 Created (tradeId, status: SUBMITTED)
    Gateway-->>Trader: HTTP 201 Created

    Kafka->>PosSvc: Consume 'trade-submitted'
    PosSvc->>PosSvc: Acquire Alphabetical Row Lock (min(buyer, seller))

    alt Insufficient Seller Balance (Oversell Guard)
        PosSvc->>Kafka: Publish to 'trade-dead-letter'
        Kafka->>NotifSvc: Consume 'trade-dead-letter'
        NotifSvc-->>Trader: Dispatch Trade Rejection Alert
    else Sufficient Seller Balance
        PosSvc->>PosSvc: Debit Seller & Credit Buyer Position
        PosSvc->>Kafka: Publish 'trade-validated' & 'position-updated'

        Kafka->>ClearSvc: Consume 'trade-validated' + 'position-updated'
        ClearSvc->>ClearSvc: Execute Delivery-versus-Payment (DvP)
        ClearSvc->>Kafka: Publish 'settlement-completed'

        Kafka->>TradeSvc: Consume 'settlement-completed'
        TradeSvc->>TradeSvc: Transition Trade Status to [SETTLED]

        Kafka->>NotifSvc: Consume 'settlement-completed'
        NotifSvc-->>Trader: Dispatch Settlement Confirmation Notice
    end
```

---

## 5. Architectural Guarantees & Concurrency Controls

### 1. Deadlock-Free Lock Ordering
To prevent RDBMS distributed circular-wait deadlocks under high-frequency trading bursts, `PositionValidationService` enforces **strict alphabetical lock acquisition**:
```java
final String firstId = buyerId.compareTo(sellerId) < 0 ? buyerId : sellerId;
final String secondId = firstId.equals(buyerId) ? sellerId : buyerId;

Position firstPos = getOrCreatePosition(firstId, instrumentId);
Position secondPos = isSelfTrade ? firstPos : getOrCreatePosition(secondId, instrumentId);
```
Regardless of trade direction, the account with the smaller ID is always locked first.

### 2. Partition-Level Serial Ordering
Kafka topics are partitioned with explicit business keys:
* `trade-submitted`: Keyed by `buyerAccountId` $\rightarrow$ all buy transactions for an account process in FIFO sequence.
* `position-updated`: Keyed by `accountId` $\rightarrow$ maintains exact chronological ledger updates without race conditions.
* `settlement-completed`: Keyed by `tradeId` $\rightarrow$ maintains trade-specific idempotent processing.

### 3. Database per Service & Isolation
Each microservice maintains its own Flyway migration history table:
* `trades` $\rightarrow$ `flyway_schema_history`
* `clearing-service` $\rightarrow$ `clearing_flyway_history`
* `position-service` $\rightarrow$ `position_flyway_history`
* `notification-service` $\rightarrow$ `notification_flyway_history`
* `reference-data-service` $\rightarrow$ `refdata_flyway_history`
* `corporate-action-service` $\rightarrow$ `corporate_action_flyway_history`

Cross-service data queries occur strictly via asynchronous event subscriptions or caching layers, never direct cross-database SQL joins.
