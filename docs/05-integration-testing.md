# Integration Testing

Integration tests spin up real PostgreSQL and Kafka containers via Testcontainers. They verify the full in-process pipeline — Kafka consumer → service logic → database write → Kafka producer — without mocking infrastructure.

## Prerequisites

- Docker Desktop must be **running** before executing integration tests.
- On macOS, Testcontainers needs the Docker socket path configured.

### One-time Testcontainers Setup (macOS)

```bash
cat > ~/.testcontainers.properties << EOF
docker.host=unix:///Users/$(whoami)/Library/Containers/com.docker.docker/Data/docker-cli.sock
EOF
```

Verify Docker is accessible:

```bash
docker info
```

---

## Run All Integration Tests

Integration tests are bound to the Maven `verify` phase via the Failsafe plugin.

```bash
cd PostTradeClearingPlatform
mvn verify
```

This runs:
1. `mvn compile`
2. `mvn test` (unit tests)
3. `mvn verify` (integration tests via Failsafe)

To skip unit tests and only run integration tests:

```bash
mvn verify -DskipTests=false -Dsurefire.skip=true
```

---

## Run a Single Integration Test

```bash
# Position service integration test
mvn verify -pl position-service -am \
  -Dit.test=PositionValidationIntegrationTest

# Trade service integration test
mvn verify -pl trade-service -am \
  -Dit.test=TradeServiceIntegrationTest

# API Gateway routing test
mvn verify -pl api-gateway -am \
  -Dit.test=ApiGatewayRoutingTest
```

---

## Integration Test Inventory

### PositionValidationIntegrationTest

**Location:** `position-service/src/test/java/com/clearing/position/integration/`

**Containers:** PostgreSQL 16, Kafka (Confluent 7.5.0)

**What it tests:**

| Scenario | Description |
|----------|-------------|
| Trade with sufficient seller balance | TradeSubmittedEvent → positions updated → TradeValidatedEvent(VALIDATED) published to `trade-validated` |
| Trade with insufficient seller balance | TradeSubmittedEvent → no position change → TradeValidatedEvent(REJECTED) published |
| Idempotency | Duplicate event for same tradeId does not double-debit |
| Lock ordering | Alphabetically first account is always locked first (deadlock prevention) |

---

### TradeServiceIntegrationTest

**Location:** `trade-service/src/test/java/com/clearing/trade/integration/`

**Containers:** PostgreSQL 16, Kafka (Confluent 7.5.0)

**What it tests:**

| Scenario | Description |
|----------|-------------|
| Submit valid trade | POST /api/v1/trades → DB row inserted with SUBMITTED status → TradeSubmittedEvent published to `trade-submitted` |
| Duplicate submission | Second POST with same trade details → handled gracefully |
| Missing required fields | POST with missing instrumentId → 400 Bad Request |

---

### ApiGatewayRoutingTest

**Location:** `api-gateway/src/test/java/com/clearing/gateway/routing/`

**Containers:** Redis (7.2-alpine)

**What it tests:**

| Scenario | Description |
|----------|-------------|
| Trade route | GET /api/v1/trades/** → forwarded to trade-service mock |
| Position route | GET /api/v1/positions/** → forwarded to position-service mock |
| Clearing route | GET /api/v1/clearing/** → forwarded to clearing-service mock |
| Market route | GET /api/v1/market/** → forwarded to market gateway mock |
| Reference data route | GET /instruments/** → forwarded to reference-data-service mock |
| Corporate action route | GET /api/v1/corporate-actions/** → forwarded correctly |
| Notification route | GET /api/v1/notifications/** → forwarded correctly |
| Rate limiting | Exceeding rate limit returns 429 Too Many Requests |
| Unknown route | GET /unknown → 404 Not Found |
| Correlation ID header | X-Correlation-ID header is added to every forwarded request |
| Health endpoint | GET /actuator/health → 200 OK (no auth required) |

---

## How Testcontainers Works

Each integration test class annotated with `@Testcontainers` declares `@Container` fields for the services it needs. Testcontainers pulls the Docker images, starts the containers, exposes random host ports, and wires them into the Spring context via `@DynamicPropertySource`.

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class PositionValidationIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres =
        new PostgreSQLContainer<>("postgres:16");

    @Container
    static KafkaContainer kafka =
        new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.5.0"));

    @DynamicPropertySource
    static void overrideProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }
}
```

Containers are shared across tests in the same class (static `@Container`) and stopped automatically when the JVM exits.

---

## Maven Failsafe Configuration

Integration tests are run by the Maven Failsafe plugin (not Surefire). The configuration in the parent `pom.xml` includes:

**Included patterns:**
- `**/*IntegrationTest.java`
- `**/*RoutingTest.java`

**Environment variables set for Docker Desktop (macOS):**

| Variable | Value |
|----------|-------|
| `DOCKER_HOST` | `unix://${user.home}/Library/Containers/com.docker.docker/Data/docker-cli.sock` |
| `TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE` | `${user.home}/Library/Containers/com.docker.docker/Data/docker-cli.sock` |
| `RYUK_DISABLED` | `true` |

`RYUK_DISABLED=true` skips the Ryuk cleanup container, which can cause issues when Docker permissions are restricted.

---

## Troubleshooting

### `Could not find a valid Docker environment`

Docker Desktop is not running, or the socket path is wrong.

1. Start Docker Desktop.
2. Check the socket exists:
   ```bash
   ls ~/Library/Containers/com.docker.docker/Data/docker-cli.sock
   ```
3. Verify `~/.testcontainers.properties` points to the right path.
4. Run the test again.

### Tests hang / timeout waiting for container

Testcontainers is pulling an image for the first time. Allow 2-5 minutes. Subsequent runs use the cached image.

### `FlywayException: Validate failed` in test

The test database starts clean every run (Testcontainers creates a fresh container). If Flyway fails, it means a migration script has a syntax error or references a table that doesn't exist yet. Check `{service}/src/main/resources/db/migration/`.

### Tests pass in isolation but fail together

Shared state between tests — usually a database row from one test affecting another. Ensure `@AfterEach` or `@Transactional` (with rollback) cleans up test data.

### `Out of memory` when running all integration tests together

Each integration test suite starts its own Postgres + Kafka containers. Running all of them in parallel is memory-intensive. Run them one service at a time:

```bash
mvn verify -pl trade-service -am
mvn verify -pl position-service -am
mvn verify -pl api-gateway -am
```
