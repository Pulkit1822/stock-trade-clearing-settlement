# API Reference

All services expose REST APIs. In local development (no Keycloak), all endpoints are accessible without authentication when started via `./dev.sh start`.

For production or gateway-routed calls, prefix paths with the API Gateway base URL: `http://localhost:8080`.

---

## Trade Service — port 8081

### Submit a Trade

```
POST /api/v1/trades
```

**Request body:**

```json
{
  "instrumentId": "AAPL",
  "quantity": 100,
  "price": 150.00,
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002"
}
```

**Response — 201 Created:**

```json
{
  "id": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "instrumentId": "AAPL",
  "quantity": 100,
  "price": 150.00,
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002",
  "status": "SUBMITTED",
  "createdAt": "2024-01-15T10:30:00Z"
}
```

**curl example:**

```bash
curl -s -X POST http://localhost:8081/api/v1/trades \
  -H "Content-Type: application/json" \
  -d '{
    "instrumentId": "AAPL",
    "quantity": 100,
    "price": 150.00,
    "buyerAccountId": "ACC001",
    "sellerAccountId": "ACC002"
  }'
```

---

### Get a Trade by ID

```
GET /api/v1/trades/{tradeId}
```

**Response — 200 OK:**

```json
{
  "id": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "instrumentId": "AAPL",
  "quantity": 100,
  "price": 150.00,
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002",
  "status": "SUBMITTED"
}
```

---

## Position Service — port 8082

### Seed / Fund an Account Position

Used to pre-load test balances. In production, positions are set via corporate actions or cash settlement.

```
POST /api/v1/positions/fund
```

**Request body:**

```json
{
  "accountId": "ACC002",
  "instrumentId": "AAPL",
  "quantity": 200
}
```

**Response — 201 Created:**

```json
{
  "id": "a1b2c3d4-...",
  "accountId": "ACC002",
  "instrumentId": "AAPL",
  "quantity": 200,
  "createdAt": "2024-01-15T10:00:00Z",
  "updatedAt": "2024-01-15T10:00:00Z"
}
```

**curl example:**

```bash
curl -s -X POST http://localhost:8082/api/v1/positions/fund \
  -H "Content-Type: application/json" \
  -d '{"accountId":"ACC002","instrumentId":"AAPL","quantity":200}'
```

---

## Clearing Service — port 8083

### Get Settlement by Trade ID

```
GET /api/v1/clearing/settlements/{tradeId}
```

**Response — 200 OK:**

```json
{
  "id": "c3d4e5f6-...",
  "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
  "status": "SETTLED",
  "buyerAccountId": "ACC001",
  "sellerAccountId": "ACC002",
  "instrumentId": "AAPL",
  "buyerConfirmed": true,
  "sellerConfirmed": true,
  "settledAt": "2024-01-15T10:30:03Z"
}
```

**Settlement status values:**

| Status | Meaning |
|--------|---------|
| `PENDING` | Trade validated; waiting for both position confirmations |
| `SETTLED` | Both buyer and seller sides confirmed; finalized |
| `FAILED` | Trade was rejected by position-service |

**curl example:**

```bash
TRADE_ID="eb96157e-33c6-4a4a-abb8-9453ab7d1406"
curl -s "http://localhost:8083/api/v1/clearing/settlements/$TRADE_ID"
```

---

### Get Settlements by Status

```
GET /api/v1/clearing/settlements?status=PENDING
GET /api/v1/clearing/settlements?status=SETTLED
GET /api/v1/clearing/settlements?status=FAILED
```

---

### Get Settlements by Account

```
GET /api/v1/clearing/settlements/account/{accountId}
```

Returns all settlements where the account was buyer or seller.

---

## Notification Service — port 8084

### Get Notifications for a Trade

```
GET /api/v1/notifications/trades/{tradeId}
```

**Response — 200 OK:**

```json
[
  {
    "id": "d4e5f6a7-...",
    "tradeId": "eb96157e-33c6-4a4a-abb8-9453ab7d1406",
    "type": "SETTLEMENT_COMPLETED",
    "message": "Trade settled successfully",
    "createdAt": "2024-01-15T10:30:04Z"
  }
]
```

**curl example:**

```bash
TRADE_ID="eb96157e-33c6-4a4a-abb8-9453ab7d1406"
curl -s "http://localhost:8084/api/v1/notifications/trades/$TRADE_ID"
```

---

## Reference Data Service — port 8085

### List All Instruments

```
GET /instruments
```

### Get Instrument by ID

```
GET /instruments/{instrumentId}
```

**Response — 200 OK:**

```json
{
  "id": "AAPL",
  "name": "Apple Inc.",
  "type": "EQUITY",
  "currency": "USD",
  "exchange": "NASDAQ"
}
```

### Create Instrument

```
POST /instruments
```

```json
{
  "id": "AAPL",
  "name": "Apple Inc.",
  "type": "EQUITY",
  "currency": "USD",
  "exchange": "NASDAQ"
}
```

### List All Participants

```
GET /participants
```

### Get Participant by ID

```
GET /participants/{participantId}
```

---

## Corporate Action Service — port 8086

### List Corporate Actions

```
GET /api/v1/corporate-actions
```

### Submit Corporate Action

```
POST /api/v1/corporate-actions
```

```json
{
  "instrumentId": "AAPL",
  "type": "DIVIDEND",
  "amount": 0.24,
  "exDate": "2024-02-01",
  "recordDate": "2024-02-05",
  "payDate": "2024-02-15"
}
```

---

## Market Integration Gateway — port 8088

### Submit External Market Order

```
POST /api/v1/market/orders
```

Translates an external market order (FIX-like format) into an internal TradeSubmittedEvent and publishes it to Kafka.

---

## Actuator Health Endpoints

All services expose Spring Actuator endpoints:

```
GET /actuator/health
GET /actuator/info
GET /actuator/metrics
GET /actuator/prometheus
```

**Check all services at once:**

```bash
for port in 8081 8082 8083 8084 8085 8086 8088; do
  echo -n "Port $port: "
  curl -s "http://localhost:$port/actuator/health" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('status','?'))"
done
```

---

## API Gateway — port 8080

The API Gateway proxies all service routes. In local dev, use the service ports directly. In a deployed environment, all traffic goes through port 8080.

**Route table:**

| Path Pattern | Target Service | Port |
|-------------|----------------|------|
| `/api/v1/trades/**` | trade-service | 8081 |
| `/api/v1/positions/**` | position-service | 8082 |
| `/api/v1/clearing/**` | clearing-service | 8083 |
| `/api/v1/notifications/**` | notification-service | 8084 |
| `/instruments/**`, `/participants/**` | reference-data-service | 8085 |
| `/api/v1/corporate-actions/**` | corporate-action-service | 8086 |
| `/api/v1/market/**` | market-integration-gateway | 8088 |

**Rate limiting** (trade route): 100 requests/second, 200 burst capacity.

**Headers added:** `X-Correlation-ID` (UUID generated per request).
