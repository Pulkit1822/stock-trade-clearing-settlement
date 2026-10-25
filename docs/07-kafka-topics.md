# Kafka Topics Reference

## Topic Configuration

All topics are created by the `kafka-init` container on infrastructure startup.

| Setting | Value |
|---------|-------|
| Partitions | 3 |
| Replication Factor | 1 (local dev) |
| Bootstrap Servers | `localhost:9092` |

---

## Topic Map

| Topic | Producer | Consumer(s) | Purpose |
|-------|----------|-------------|---------|
| `trade-submitted` | trade-service | position-service | A new trade has been accepted and needs position validation |
| `trade-validated` | position-service | clearing-service | Position check complete — VALIDATED or REJECTED |
| `position-updated` | position-service | clearing-service | One account side (buyer or seller) has been debited/credited |
| `settlement-completed` | clearing-service | notification-service | Settlement has been finalized — both sides confirmed |
| `notification-events` | notification-service | (downstream/external) | Notification dispatched to participants |
| `reference-data-events` | reference-data-service | (broadcast) | Instrument or participant data changed |
| `corporate-action-events` | corporate-action-service | (broadcast) | Corporate event (dividend, split, merger) |
| `trade-processed` | (reserved) | (future use) | Future downstream processing status |
| `trade-dead-letter` | error handlers | monitoring | Failed events that exhausted retries |

---

## Event Contracts

All events are in `shared-libraries/src/main/java/com/clearing/shared/events/`.

---

### TradeSubmittedEvent

**Topic:** `trade-submitted`
**Producer:** trade-service
**Consumer:** position-service
**Partition key:** `tradeId`

```json
{
  "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002",
  "instrumentId": "AAPL",
  "quantity": 100,
  "price": 150.00,
  "timestamp": "2024-01-15T10:30:00Z"
}
```

---

### TradeValidatedEvent

**Topic:** `trade-validated`
**Producer:** position-service
**Consumer:** clearing-service
**Partition key:** `tradeId`

```json
{
  "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "status": "VALIDATED",
  "rejectionReason": null,
  "timestamp": "2024-01-15T10:30:01Z"
}
```

`status` is one of: `VALIDATED`, `REJECTED`

When `REJECTED`, `rejectionReason` contains the reason (e.g., `"Insufficient position in Seller account"`).

---

### PositionUpdatedEvent

**Topic:** `position-updated`
**Producer:** position-service
**Consumer:** clearing-service
**Partition key:** `accountId`

Two events are published per trade — one for the seller, one for the buyer.

**Seller side (debit):**

```json
{
  "accountId": "ACC002",
  "instrumentId": "AAPL",
  "quantityDelta": -100,
  "newQuantity": 100,
  "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "timestamp": "2024-01-15T10:30:01Z"
}
```

**Buyer side (credit):**

```json
{
  "accountId": "ACC001",
  "instrumentId": "AAPL",
  "quantityDelta": 100,
  "newQuantity": 100,
  "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "timestamp": "2024-01-15T10:30:01Z"
}
```

`quantityDelta > 0` = buyer side (shares credited).
`quantityDelta < 0` = seller side (shares debited).

---

### SettlementCompletedEvent

**Topic:** `settlement-completed`
**Producer:** clearing-service
**Consumer:** notification-service
**Partition key:** `tradeId`

```json
{
  "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002",
  "settledAt": "2024-01-15T10:30:02Z"
}
```

---

### NotificationEvent

**Topic:** `notification-events`
**Producer:** notification-service
**Partition key:** `tradeId`

---

## Consumer Groups

Each service uses its own consumer group so it maintains its own offset independently.

| Service | Consumer Group |
|---------|---------------|
| position-service | `position-service` |
| clearing-service | `clearing-service` |
| notification-service | `notification-service` |

---

## Kafka Reliability Settings

All producers are configured with:

```yaml
properties:
  enable.idempotence: true   # Exactly-once delivery at Kafka layer
  acks: all                  # Wait for all in-sync replicas
  retries: 3
  max.in.flight.requests.per.connection: 1  # Preserve ordering on retry
```

All consumers use manual acknowledgment:

```yaml
listener:
  ack-mode: manual_immediate
consumer:
  enable-auto-commit: false
  auto-offset-reset: earliest
```

This means if a consumer crashes mid-processing, the offset is not committed and the message is redelivered. The service must be idempotent.

---

## Browsing Topics

### Kafka UI (web interface)

Open http://localhost:8091

- Browse all topics
- View messages per partition
- Check consumer group lag

### Command Line

```bash
# List all topics
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list

# Read all messages from trade-submitted topic
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic trade-submitted \
  --from-beginning

# Check consumer group lag for clearing-service
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group clearing-service \
  --describe

# Check consumer group lag for position-service
docker exec kafka kafka-consumer-groups \
  --bootstrap-server localhost:9092 \
  --group position-service \
  --describe
```

---

## Trusted Packages (JSON Deserialization)

Kafka consumers use Spring's `JsonDeserializer`. The trusted package is set in each service's `application.yml`:

```yaml
spring:
  kafka:
    consumer:
      properties:
        spring.json.trusted.packages: "com.clearing.shared.events"
```

All event classes live in `shared-libraries` under `com.clearing.shared.events`. If you add a new event class, put it in that package.
