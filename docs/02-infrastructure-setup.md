# Infrastructure Setup

The platform depends on PostgreSQL, Kafka, Redis, and optionally Keycloak. All run as Docker containers managed by Docker Compose.

## Docker Compose File

```
infrastructure/docker/docker-compose.yml
```

## Quick Start (via dev.sh)

The `dev.sh` script handles infrastructure automatically as part of `start` / `restart`. You do not need to touch Docker Compose directly for normal development.

```bash
./dev.sh start      # starts infra + all 6 services
./dev.sh stop       # stops all services + infra
./dev.sh restart    # stop then start
```

---

## Manual Infrastructure Control

If you want to start only the infrastructure (without services):

```bash
# Start only the core infra containers
docker-compose -f infrastructure/docker/docker-compose.yml up -d \
  postgres kafka zookeeper redis kafka-init

# Stop and remove containers
docker-compose -f infrastructure/docker/docker-compose.yml down --remove-orphans

# Start everything including observability stack
docker-compose -f infrastructure/docker/docker-compose.yml up -d
```

---

## Infrastructure Components

### PostgreSQL

| Property | Value |
|----------|-------|
| Container | `clearing-db` |
| Image | `postgres:16-alpine` |
| Host port | `5433` |
| Container port | `5432` |
| Username | `admin` |
| Password | `admin` |
| Database | `clearing` |

All 6 services share one database. Each service owns its own set of tables and uses a dedicated Flyway history table to track its migrations independently.

**Connect manually:**

```bash
# Via docker exec (psql inside container)
docker exec -it clearing-db psql -U admin -d clearing

# Via local psql (if installed)
psql -h localhost -p 5433 -U admin -d clearing
```

**Check Postgres is ready:**

```bash
docker exec clearing-db pg_isready -U admin -d clearing
```

**Flyway history tables (one per service):**

| Service | Flyway Table |
|---------|-------------|
| trade-service | `flyway_schema_history` |
| position-service | `position_flyway_history` |
| clearing-service | `clearing_flyway_history` |
| notification-service | `notification_flyway_history` |
| reference-data-service | `refdata_flyway_history` |
| corporate-action-service | `corporate_action_flyway_history` |

**Reset Flyway history (useful when migration files change):**

```bash
docker exec clearing-db psql -U admin -d clearing -c "
  DROP TABLE IF EXISTS flyway_schema_history;
  DROP TABLE IF EXISTS position_flyway_history;
  DROP TABLE IF EXISTS clearing_flyway_history;
  DROP TABLE IF EXISTS notification_flyway_history;
  DROP TABLE IF EXISTS refdata_flyway_history;
  DROP TABLE IF EXISTS corporate_action_flyway_history;
"
```

> `dev.sh start` and `dev.sh restart` run this reset automatically.

---

### Apache Kafka

| Property | Value |
|----------|-------|
| Container | `kafka` |
| Image | `confluentinc/cp-kafka:7.5.0` |
| Host port | `9092` |
| Bootstrap servers | `localhost:9092` |

Topics are created by the `kafka-init` container on startup. See [07-kafka-topics.md](07-kafka-topics.md) for the full topic list.

**Browse topics in Kafka UI:**

Open http://localhost:8091 in your browser.

**List topics manually:**

```bash
docker exec kafka kafka-topics --bootstrap-server localhost:9092 --list
```

**Describe a topic:**

```bash
docker exec kafka kafka-topics \
  --bootstrap-server localhost:9092 \
  --describe --topic trade-submitted
```

**Read messages from a topic (from beginning):**

```bash
docker exec kafka kafka-console-consumer \
  --bootstrap-server localhost:9092 \
  --topic trade-submitted \
  --from-beginning \
  --max-messages 10
```

---

### Redis

| Property | Value |
|----------|-------|
| Container | `redis` |
| Image | `redis:7.2-alpine` |
| Host port | `6379` |

Used by `api-gateway` for request rate limiting.

**Connect to Redis CLI:**

```bash
docker exec -it redis redis-cli
```

---

### Keycloak (OAuth2 / JWT)

| Property | Value |
|----------|-------|
| Container | `keycloak` |
| Image | `quay.io/keycloak/keycloak:22.0.5` |
| Host port | `8180` |
| Admin console | http://localhost:8180/admin |
| Realm | `clearing` |
| Issuer URI | `http://localhost:8180/realms/clearing` |

> For local development with `dev.sh`, Keycloak is **not required**. All services start with the `local` Spring profile which disables OAuth2 security entirely.

---

### Observability Stack (Optional)

| Component | Container | Port | URL |
|-----------|-----------|------|-----|
| Prometheus | `prometheus` | 9090 | http://localhost:9090 |
| Grafana | `grafana` | 3000 | http://localhost:3000 |
| Elasticsearch | `elasticsearch` | 9200 | http://localhost:9200 |
| Kibana | `kibana` | 5601 | http://localhost:5601 |

Start the observability stack:

```bash
docker-compose -f infrastructure/docker/docker-compose.yml up -d \
  prometheus grafana elasticsearch kibana
```

Each service exposes metrics at `/actuator/prometheus`.

---

## Troubleshooting

### Port 5432 already in use

The Docker container maps PostgreSQL on host port **5433**, not 5432, specifically to avoid conflicts with a local PostgreSQL installation. Services use `DB_PORT=5433` when started via `dev.sh`.

### Kafka topics not created

If services fail to start because topics don't exist, the `kafka-init` container may have exited before Kafka was ready. Re-run:

```bash
docker-compose -f infrastructure/docker/docker-compose.yml restart kafka-init
```

Or wait and check:

```bash
docker logs kafka-init
```

### Container already exists error

```bash
docker-compose -f infrastructure/docker/docker-compose.yml down --remove-orphans
docker-compose -f infrastructure/docker/docker-compose.yml up -d postgres kafka zookeeper redis kafka-init
```
