# Week 1: Infrastructure & Local Environment

## Objective

Deploy the complete local development infrastructure stack using Docker Compose to support the microservices architecture. This allows developers to run the entire data, messaging, and observability planes instantly.

## 1. Docker Compose Stack

The central nervous system of the platform infrastructure is housed in `infrastructure/docker/docker-compose.yml`.

### Deployed Services

1. **PostgreSQL 16**: The central relational database serving all entity persistence (Trades, Positions).
2. **Apache Kafka & Zookeeper (Confluent 7.5.0)**: The event-bus managing the asynchronous stream of trade lifecycle events.
3. **Redis 7.2**: In-memory data grid utilized by the Reference Data service for caching massive instrument lists.
4. **Keycloak 22.0.5**: OpenID Connect (OIDC) identity provider handling JWT token generation and endpoint security.
5. **Prometheus & Grafana**: The metrics aggregator and visualization dashboard (Observability Plane).
6. **Elasticsearch & Kibana (ELK)**: Distributed logging plane for centralizing microservice operational logs.
7. **Kafka UI**: A web-based manager for executing health checks and viewing real-time streams across Kafka topics.

## 2. Data Persistence & Messaging Configurations

- **PostgreSQL**: Bound to local port `5432`. Requires an isolated database schema `clearing`.
- **Kafka Topics**: An initialization container (`kafka-init`) automatically runs `create-topics.sh` during the cluster boot phase.
  - Topics created: `trade-submitted`, `trade-validated`, `trade-processed`, `position-updated`, `settlement-completed`, `notification-events`, `reference-data-events`, `corporate-action-events`, `trade-dead-letter`.
  - All standard topics are provisioned with exactly **3 partitions** to facilitate ordered processing by `accountId`.

## 3. Security Configuration (Keycloak)

To streamline local development, a pre-configured Keycloak realm (`realm-export.json`) is mounted as a volume.

- **Realm:** `clearing-platform`
- **Clients:** `api-gateway`, `frontend-client`
- **Roles:** `participant`, `admin`, `operations`

## 4. Observability Mapping

- **Prometheus (`prometheus.yml`)**: Configure to automatically scrape `/actuator/prometheus` endpoints on standard ports across the 8 microservices (e.g., Trade Service at `8081`).
- **Grafana**: Utilizing provisioning files to map Prometheus as the default data source on startup automatically, eliminating manual GUI config.
