# Build and Compile

## Project Structure

Multi-module Maven project. The root `pom.xml` declares all modules:

```
PostTradeClearingPlatform/
├── pom.xml                          # Parent POM
├── shared-libraries/                # Common events, DTOs, exceptions
├── api-gateway/                     # Spring Cloud Gateway (port 8080)
├── market-integration-gateway/      # Market data ingestion (port 8088)
├── trade-service/                   # Trade submission (port 8081)
├── position-service/                # Position management (port 8082)
├── clearing-service/                # Settlement tracking (port 8083)
├── notification-service/            # Notifications (port 8084)
├── reference-data-service/          # Reference data (port 8085)
└── corporate-action-service/        # Corporate actions (port 8086)
```

`shared-libraries` must be built first — all other services depend on it. Maven handles this automatically via the module order in the parent POM.

---

## Build Commands

### Full Build (all modules, skip tests)

This is the standard command used before starting services locally.

```bash
cd PostTradeClearingPlatform
mvn clean package -DskipTests
```

Expected output:

```
[INFO] BUILD SUCCESS
[INFO] Total time: ~60-90s
```

### Full Build with Unit Tests

```bash
mvn clean package
```

This compiles and runs all unit tests (excludes integration tests). Expect ~60-120s.

### Build a Single Module

```bash
# Build only trade-service (and its dependencies)
mvn clean package -DskipTests -pl trade-service -am

# -pl  : select the module
# -am  : also build modules it depends on (shared-libraries in this case)
```

### Compile Only (no JAR packaging)

```bash
mvn compile
```

### Check for Compilation Errors Without Running Tests

```bash
mvn clean compile -q
```

### Quiet Build (suppress most output)

```bash
mvn clean package -DskipTests -q
```

---

## Build Artifacts

Each service produces a fat JAR in `{service}/target/`:

```
trade-service/target/trade-service-1.0.0-SNAPSHOT.jar
position-service/target/position-service-1.0.0-SNAPSHOT.jar
clearing-service/target/clearing-service-1.0.0-SNAPSHOT.jar
notification-service/target/notification-service-1.0.0-SNAPSHOT.jar
reference-data-service/target/reference-data-service-1.0.0-SNAPSHOT.jar
corporate-action-service/target/corporate-action-service-1.0.0-SNAPSHOT.jar
api-gateway/target/api-gateway-1.0.0-SNAPSHOT.jar
market-integration-gateway/target/market-integration-gateway-1.0.0-SNAPSHOT.jar
shared-libraries/target/shared-libraries-1.0.0-SNAPSHOT.jar
```

---

## Running a Service Manually

`dev.sh` handles this automatically, but if you want to start a service by hand:

```bash
cd trade-service
DB_PORT=5433 mvn spring-boot:run \
  -Dspring-boot.run.profiles=local \
  -Dspring-boot.run.jvmArguments="-Xms128m -Xmx512m"
```

The `local` profile disables OAuth2/Keycloak so the service starts without Keycloak running.

**Service startup environment variables:**

| Variable | Default | Description |
|----------|---------|-------------|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5432` | PostgreSQL port (set to `5433` for Docker) |
| `DB_NAME` | `clearing` | Database name |
| `DB_USERNAME` | `admin` | Database user |
| `DB_PASSWORD` | `admin` | Database password |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka broker |

---

## Common Build Errors

### `package com.clearing.shared.events does not exist`

shared-libraries wasn't built first. Run from the root:

```bash
mvn clean package -DskipTests
```

Do not run `mvn` from inside individual service directories unless you include `-am`.

### `package org.testcontainers.junit.jupiter does not exist`

Missing testcontainers JUnit 5 dependency. Check the service's `pom.xml` includes:

```xml
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <scope>test</scope>
</dependency>
```

### `jakarta.persistence` not found

Service uses JPA entities but is missing the Spring Data JPA starter. Add to `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

### Flyway validation error on startup

Migration file was changed after it was already applied. Reset the Flyway history for that service:

```bash
docker exec clearing-db psql -U admin -d clearing \
  -c "DROP TABLE IF EXISTS <service>_flyway_history;"
```

Then restart the service. Flyway will re-run from the baseline.
