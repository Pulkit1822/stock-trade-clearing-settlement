# End-to-End (E2E) Testing

The E2E test validates the complete trade settlement flow across all running services using real HTTP calls and real Kafka events. All 6 services + infrastructure must be running before executing the test.

## Full Flow Under Test

```
[1] Seed seller position  →  position-service
[2] Submit trade          →  trade-service
[3] Poll settlement       →  clearing-service  (waits up to 50s)
[4] Check notifications   →  notification-service
```

The Kafka event chain that happens in the background between steps 2 and 3:

```
trade-service       →  trade-submitted      →  position-service
position-service    →  position-updated (x2) →  clearing-service
position-service    →  trade-validated       →  clearing-service
clearing-service    →  settlement-completed  →  notification-service
```

---

## Step 1 — Start Everything

```bash
./dev.sh start
```

This will:
1. Start Docker containers (postgres, kafka, zookeeper, redis, kafka-init)
2. Wait for Postgres to be ready
3. Reset all Flyway history tables
4. Wait 12s for Kafka topics to be created
5. Build all Maven modules (`mvn clean package -DskipTests`)
6. Start all 6 services in the background with the `local` Spring profile
7. Print a status table when all 6 ports are up

Expected output when healthy:

```
SERVICE                        PORT     STATUS     HEALTH
-------                        ----     ------     ------
trade-service                  8081     RUNNING    UP
position-service               8082     RUNNING    UP
clearing-service               8083     RUNNING    UP
notification-service           8084     RUNNING    UP
reference-data-service         8085     RUNNING    UP
corporate-action-service       8086     RUNNING    UP
```

---

## Step 2 — Run the E2E Test

```bash
./dev.sh test
```

### Expected Passing Output

```
▶ E2E Test: Full Trade Settlement Flow

[1] Seeding seller position (ACC002 → 200 AAPL)
  ✔ Fund seller position (HTTP 201) (got: 201)

[2] Submitting trade (ACC001 buys 100 AAPL from ACC002 @ $150)
  ✔ Trade submission (HTTP 201) (got: 201)
  ✔ Trade ID returned
     Trade ID: eb96157e-33c6-4a4a-abb8-9453ab7d1406
  ✔ Trade status is SUBMITTED (got: SUBMITTED)

[3] Waiting for settlement (clearing-service)
  ✔ Settlement status is SETTLED (got: SETTLED)

[4] Checking notifications (notification-service)
  ✔ Notifications dispatched for trade

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
[OK]    All tests passed (6/6)
━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━
```

---

## What Each Step Asserts

### Step 1 — Seed Seller Position

```
POST http://localhost:8082/api/v1/positions/fund
Body: {"accountId":"ACC002","instrumentId":"AAPL","quantity":200}
```

| Assertion | Expected |
|-----------|----------|
| HTTP status | 201 Created |

Seeds 200 AAPL shares into account ACC002 so the seller has enough to trade.

---

### Step 2 — Submit Trade

```
POST http://localhost:8081/api/v1/trades
Body: {
  "instrumentId": "AAPL",
  "quantity": 100,
  "price": 150.00,
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002"
}
```

| Assertion | Expected |
|-----------|----------|
| HTTP status | 201 Created |
| Response body contains `id` | non-empty UUID |
| Response body `status` | `SUBMITTED` |

Trade is persisted and a `trade-submitted` event is published to Kafka.

---

### Step 3 — Poll Settlement

```
GET http://localhost:8083/api/v1/clearing/settlements/{tradeId}
```

Polled every 2 seconds for up to 50 seconds.

| Assertion | Expected |
|-----------|----------|
| Response body `status` | `SETTLED` |

The full Kafka chain must complete within the polling window:
- position-service consumes `trade-submitted`, validates, publishes `position-updated` + `trade-validated`
- clearing-service consumes both `position-updated` events (buyer + seller), flips settlement to `SETTLED`

---

### Step 4 — Check Notifications

```
GET http://localhost:8084/api/v1/notifications/trades/{tradeId}
```

Polled every 2 seconds for up to 40 seconds.

| Assertion | Expected |
|-----------|----------|
| Response body | non-empty array (at least one notification) |

notification-service consumed the `settlement-completed` event and created a notification record.

---

## Running Tests Multiple Times

The test is **not idempotent** by design — each run seeds new positions and submits a new trade with a new trade ID. Re-running `./dev.sh test` multiple times is safe.

However, the position state accumulates. If you run the test many times, ACC002 will have an ever-growing position. To reset all data:

```bash
./dev.sh restart
```

`restart` drops and recreates all Flyway history tables, which causes services to re-run their migrations on startup and reset all tables.

---

## Manual E2E Test (curl)

You can reproduce the same flow manually step by step:

### 1. Seed seller position

```bash
curl -s -X POST http://localhost:8082/api/v1/positions/fund \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC002","instrumentId":"AAPL","quantity":200}' \
  | python3 -m json.tool
```

### 2. Submit trade

```bash
TRADE=$(curl -s -X POST http://localhost:8081/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{"instrumentId":"AAPL","quantity":100,"price":150.00,"buyerAccountId":"ACC001","sellerAccountId":"ACC002"}')

echo "$TRADE" | python3 -m json.tool

TRADE_ID=$(echo "$TRADE" | python3 -c "import sys,json; print(json.load(sys.stdin)['id'])")
echo "Trade ID: $TRADE_ID"
```

### 3. Check settlement (poll manually)

```bash
# Run this a few times until status is SETTLED
curl -s "http://localhost:8083/api/v1/clearing/settlements/$TRADE_ID" \
  | python3 -m json.tool
```

### 4. Check notifications

```bash
curl -s "http://localhost:8084/api/v1/notifications/trades/$TRADE_ID" \
  | python3 -m json.tool
```

---

## Troubleshooting

### Step 1 fails — 500 on fund position

Check position-service logs:

```bash
./dev.sh logs position-service | grep -E "ERROR|Exception" | tail -30
```

Common cause: Flyway migration failed on startup. Restart everything:

```bash
./dev.sh restart
```

### Step 3 times out — settlement stays PENDING

The Kafka event chain broke somewhere. Debug in order:

```bash
# 1. Did position-service receive and process the trade event?
./dev.sh logs position-service | grep "Processing Trade"

# 2. Did position-service publish position-updated events?
./dev.sh logs position-service | grep "Publishing PositionUpdatedEvent"

# 3. Did clearing-service receive the events?
./dev.sh logs clearing-service | grep "Received PositionUpdatedEvent"

# 4. Did both sides confirm?
./dev.sh logs clearing-service | grep "confirmed for tradeId"

# 5. Was settlement finalized?
./dev.sh logs clearing-service | grep "Settlement FINALIZED"
```

Also check Kafka UI at http://localhost:8091 to see if messages landed in the topics.

### Step 4 times out — notifications not dispatched

```bash
# Did clearing-service publish settlement-completed?
./dev.sh logs clearing-service | grep "publishSettlementCompleted"

# Did notification-service receive it?
./dev.sh logs notification-service | grep "Received SettlementCompletedEvent"
```

### Service says it's running but returning 401

The `local` Spring profile was not applied. Stop the service and restart via `dev.sh`:

```bash
./dev.sh stop
./dev.sh start
```

### Settlement says FAILED instead of SETTLED

The trade was REJECTED by position-service — most likely the seller had insufficient balance.

Check:

```bash
./dev.sh logs position-service | grep "REJECTED"
```

Run the seed step again, then submit a new trade:

```bash
curl -s -X POST http://localhost:8082/api/v1/positions/fund \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC002","instrumentId":"AAPL","quantity":200}'
```

---

## Checking Service Health

```bash
./dev.sh status
```

Or individually:

```bash
curl -s http://localhost:8081/actuator/health | python3 -m json.tool
curl -s http://localhost:8082/actuator/health | python3 -m json.tool
curl -s http://localhost:8083/actuator/health | python3 -m json.tool
curl -s http://localhost:8084/actuator/health | python3 -m json.tool
```

---

## Viewing Logs

```bash
./dev.sh logs trade-service
./dev.sh logs position-service
./dev.sh logs clearing-service
./dev.sh logs notification-service
./dev.sh logs reference-data-service
./dev.sh logs corporate-action-service
```

Log files are at `.dev/logs/{service}.log`.
