# Local Setup Guide

Welcome to the Post-Trade Clearing Platform. This guide outlines how to bootstrap the local infrastructure and start working on the microservices.

## Prerequisites

Ensure the following are installed on your machine:

- Docker Desktop or OrbStack (running)
- Java 21
- Maven (`mvn`)
- Minimum 16GB RAM recommended for running all containers simultaneously.

---

## 1. Start the Infrastructure (Docker)

The entire backbone (Database, Kafka, Security, Observability) runs locally via Docker.

1. Navigate to the docker directory:

   ```bash
   cd infrastructure/docker
   ```

2. Confirm your `.env` file exists in this directory (it holds DB and Keycloak passwords). Note: If missing, refer to the project admin or create it with default local credentials.

3. Spin up the environment:

   ```bash
   docker-compose up -d
   ```

4. **Wait ~60 seconds** for all services (especially Kafka and Keycloak) to fully boot.

---

## 2. Verify Success Criteria

Before starting the backend code, ensure the infra is running properly. You can verify this by opening the following URLs in your browser:

- **Kafka UI:** [http://localhost:8091](http://localhost:8091)
  *(Check to ensure the 9 active topics like `trade-submitted` have been created)*
- **Keycloak Admin:** [http://localhost:8180](http://localhost:8180)
  *(Login `admin` / `admin`. Ensure `clearing-platform` realm load was successful)*
- **Grafana Dashboards:** [http://localhost:3000](http://localhost:3000)
- **Kibana Logging:** [http://localhost:5601](http://localhost:5601)
- **PostgreSQL:** Accessible on `localhost:5432` internally via any DB Tool (DBeaver, DataGrip).

---

## 3. Running the Microservices

Once the Docker infrastructure is healthy, you can boot individual microservices.

**From the root project directory:**
To build the shared libraries and start a service via maven:

```bash
# Build the entire project and shared-libraries first
mvn clean install -DskipTests

# Start the trade-service alone (as an example)
cd trade-service
mvn spring-boot:run
```

Each service exposes health metrics at `/actuator/health` upon startup.
