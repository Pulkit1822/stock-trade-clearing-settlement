# PostTradeClearingPlatform — Project Overview

## What It Is

A distributed, event-driven post-trade clearing and settlement platform built with Java 21 and Spring Boot 3.3.5. It simulates the lifecycle of a securities trade from submission through position validation, clearing, settlement, and notification — all wired together through Apache Kafka.

## Technology Stack

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 21 |
| Framework | Spring Boot | 3.3.5 |
| Gateway | Spring Cloud Gateway | 2023.0.3 |
| Messaging | Apache Kafka | 7.5.0 (Confluent) |
| Database | PostgreSQL | 16 |
| Migrations | Flyway | 10.15.0 |
| Cache | Redis | 7.2 |
| Auth | Keycloak | 22.0.5 |
| Testing | Testcontainers | 1.19.8 |
| Build | Maven | Multi-module |

## Services

| Service | Port | Responsibility |
|---------|------|---------------|
| api-gateway | 8080 | Rate-limiting, JWT validation, routing |
| trade-service | 8081 | Accept and publish trade submissions |
| position-service | 8082 | Validate seller position, debit/credit accounts |
| clearing-service | 8083 | Track settlement lifecycle (PENDING → SETTLED) |
| notification-service | 8084 | Dispatch notifications on settlement |
| reference-data-service | 8085 | Instruments and participant lookup |
| corporate-action-service | 8086 | Corporate events (dividends, splits) |
| market-integration-gateway | 8088 | External market data ingestion |

## Event Flow

```
Client
  │
  ▼
api-gateway:8080
  │  JWT validate + rate-limit
  ▼
trade-service:8081  ──── trade-submitted ────►  position-service:8082
                                                       │ validates seller qty
                                                       │ debits seller, credits buyer
                                                       ├── position-updated (seller) ──►┐
                                                       ├── position-updated (buyer)  ──►│ clearing-service:8083
                                                       └── trade-validated ────────────►│
                                                                                        │ both sides confirmed?
                                                                                        │ → status: SETTLED
                                                                                        │
                                                                                        └── settlement-completed ──► notification-service:8084
                                                                                                                             │
                                                                                                                             └── notification-events
```

## Infrastructure

| Component | Port | Purpose |
|-----------|------|---------|
| PostgreSQL | 5433 (host) → 5432 | Shared DB, per-service schemas |
| Kafka | 9092 | Event bus |
| Zookeeper | (internal) | Kafka coordination |
| Redis | 6379 | API Gateway rate limiting + caching |
| Keycloak | 8180 | OAuth2/JWT issuer |
| Kafka UI | 8091 | Browse topics and messages |
| Prometheus | 9090 | Metrics scraping |
| Grafana | 3000 | Metrics dashboards |

## Documentation Index

| File | Contents |
|------|----------|
| [01-prerequisites.md](01-prerequisites.md) | What to install before you start |
| [02-infrastructure-setup.md](02-infrastructure-setup.md) | Start/stop Docker infrastructure |
| [03-build-and-compile.md](03-build-and-compile.md) | Maven build commands |
| [04-unit-testing.md](04-unit-testing.md) | Run unit tests |
| [05-integration-testing.md](05-integration-testing.md) | Run integration tests (Testcontainers) |
| [06-e2e-testing.md](06-e2e-testing.md) | Full trade flow E2E test |
| [07-kafka-topics.md](07-kafka-topics.md) | Topic reference and event contracts |
| [08-api-reference.md](08-api-reference.md) | REST endpoints and example payloads |
