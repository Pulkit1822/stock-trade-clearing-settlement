# Prerequisites

Everything you need installed before working on PostTradeClearingPlatform.

## Required Tools

### Java 21

The project uses Java 21. Verify:

```bash
java -version
# Expected: openjdk version "21.x.x"
```

Install via SDKMAN (recommended):

```bash
curl -s "https://get.sdkman.io" | bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
sdk install java 21.0.3-tem
sdk use java 21.0.3-tem
```

Or download from: https://adoptium.net

Set `JAVA_HOME`:

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21)   # macOS
```

---

### Maven 3.9+

```bash
mvn -version
# Expected: Apache Maven 3.9.x
```

Install on macOS:

```bash
brew install maven
```

---

### Docker Desktop (macOS)

Docker Desktop runs the infrastructure containers (PostgreSQL, Kafka, Redis, Keycloak).

```bash
docker version
docker-compose version
```

**Important — Docker Desktop on macOS uses a non-standard socket path:**

```
~/Library/Containers/com.docker.docker/Data/docker-cli.sock
```

Testcontainers needs to know about this. Create the config file once:

```bash
cat > ~/.testcontainers.properties << 'EOF'
docker.host=unix:///Users/YOUR_USERNAME/Library/Containers/com.docker.docker/Data/docker-cli.sock
EOF
```

Replace `YOUR_USERNAME` with your actual macOS username (`echo $USER`).

Verify Docker is running:

```bash
docker info
```

---

### netcat (nc)

Used by `dev.sh` to poll service ports during startup.

```bash
nc -h 2>&1 | head -1
```

Install on macOS:

```bash
brew install netcat
```

---

### curl and jq (optional but useful)

Used for manual API testing and parsing JSON responses.

```bash
brew install jq
```

---

## Port Availability

Before starting the platform, confirm these ports are free:

```bash
lsof -i :8080,8081,8082,8083,8084,8085,8086,8088,5433,9092,6379,8180,8091
```

If port 5432 is in use (local PostgreSQL), that is fine — the Docker container maps on 5433.

| Port | Service |
|------|---------|
| 8080 | api-gateway |
| 8081 | trade-service |
| 8082 | position-service |
| 8083 | clearing-service |
| 8084 | notification-service |
| 8085 | reference-data-service |
| 8086 | corporate-action-service |
| 8088 | market-integration-gateway |
| 5433 | PostgreSQL (Docker) |
| 9092 | Kafka |
| 6379 | Redis |
| 8180 | Keycloak |
| 8091 | Kafka UI |

---

## First-Time Setup

Clone the repo and make the dev script executable:

```bash
git clone <repo-url>
cd PostTradeClearingPlatform
chmod +x dev.sh
```

That's it. The `dev.sh` script handles everything else.
