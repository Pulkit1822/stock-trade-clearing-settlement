#!/usr/bin/env bash
# dev.sh — PostTradeClearingPlatform local environment manager
#
# ── Commands ───────────────────────────────────────────────────────────────────
#   ./dev.sh start          Build Fluentd image + start full infra + all services
#   ./dev.sh stop           Gracefully stop all services + infra
#   ./dev.sh restart        stop then start
#   ./dev.sh status         Show running state and health for everything
#   ./dev.sh logs <svc>     Tail logs for a Spring service (e.g. trade-service)
#   ./dev.sh test           Run full E2E trade settlement flow + assertions
#   ./dev.sh portal         Start the React Participant Portal (Vite dev server)
#   ./dev.sh infra-only     Start infra containers only (no Spring services)
#   ./dev.sh build          Maven build without starting anything
#
# ── Prerequisites ──────────────────────────────────────────────────────────────
#   docker, docker compose v2 (docker compose), mvn, nc, node, npm, curl
#
# ── Architecture summary ───────────────────────────────────────────────────────
#   Infrastructure (Docker Compose):
#     Postgres :5433  |  Kafka :9092  |  Zookeeper  |  Redis :6379
#     Kafka UI :8091
#     Elasticsearch :9200  |  Kibana :5601
#     Prometheus :9090     |  Alertmanager :9093  |  Grafana :3000
#     Fluentd :24224  (custom Docker image — built from infrastructure/fluentd/)
#
#   Spring Services (JVM, local):
#     api-gateway          :8080   (JWT + rate-limiting + routing)
#     trade-service        :8081   (trade submission + settlement consumer)
#     position-service     :8082   (position validation, lock-ordered update)
#     clearing-service     :8083   (settlement engine)
#     notification-service :8084   (settlement-completed consumer)
#     reference-data-service :8085 (instruments, participants, accounts)
#     corporate-action-service :8086
#
#   Frontend (Node):
#     participant-portal   :5173   (Vite + React 18 + TS)

set -euo pipefail

# ─── Config ────────────────────────────────────────────────────────────────────
# Ensure common macOS paths are included for docker, brew-installed mvn, lsof, and nc
export PATH="/usr/local/bin:/opt/homebrew/bin:/usr/bin:/bin:/usr/sbin:/sbin:$PATH"

ROOT="$(cd "$(dirname "$0")" && pwd)"
DOCKER_COMPOSE_FILE="$ROOT/infrastructure/docker/docker-compose.yml"
LOG_DIR="$ROOT/logs"
PID_DIR="$ROOT/logs/pids"
DB_PORT=5433
DB_HOST="localhost"

# All Spring services in startup order (dependency-first)
SERVICES=(
  api-gateway:8080
  trade-service:8081
  position-service:8082
  clearing-service:8083
  notification-service:8084
  reference-data-service:8085
  corporate-action-service:8086
)

# Infra containers that must be healthy before services start.
# Ordered: data-layer first, then messaging, then observability.
INFRA_CORE=(postgres zookeeper kafka redis)
INFRA_OBS=(elasticsearch fluentd prometheus alertmanager grafana kibana kafka-ui kafka-init)

# ─── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; CYAN='\033[0;36m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo "${BLUE}[INFO]${RESET}  $*"; }
success() { echo "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo "${RED}[ERROR]${RESET} $*" >&2; }
step()    { echo "\n${BOLD}▶ $*${RESET}"; }
dim()     { echo "${CYAN}  $*${RESET}"; }

# ─── Helpers ───────────────────────────────────────────────────────────────────
require() {
  command -v "$1" &>/dev/null || { error "'$1' not found. Please install it."; exit 1; }
}

pid_file()     { echo "$PID_DIR/$1.pid"; }
log_file()     { echo "$LOG_DIR/$1.log"; }
service_name() { echo "${1%%:*}"; }   # trade-service:8081 → trade-service
service_port() { echo "${1##*:}"; }   # trade-service:8081 → 8081

is_running() {
  local pid_f; pid_f="$(pid_file "$1")"
  [[ -f "$pid_f" ]] && kill -0 "$(cat "$pid_f")" 2>/dev/null
}

wait_for_port() {
  local name=$1 port=$2 retries=${3:-60}
  info "Waiting for ${name} on port ${port}…"
  for i in $(seq 1 $retries); do
    if nc -z localhost "$port" 2>/dev/null; then
      success "$name is up  (port $port)"
      return 0
    fi
    sleep 2
  done
  error "$name failed to start on port $port after $((retries * 2))s"
  return 1
}

# Use 'docker compose' (v2) with fallback to 'docker-compose' (v1)
dc() {
  if docker compose version &>/dev/null 2>&1; then
    docker compose "$@"
  else
    docker-compose "$@"
  fi
}

# ─── Rollback ──────────────────────────────────────────────────────────────────
rollback() {
  error "Something went wrong. Rolling back…"
  stop_services 2>/dev/null || true
  stop_infra    2>/dev/null || true
  error "Rollback complete. Check logs in $LOG_DIR"
  exit 1
}
trap rollback ERR

# ─── Build Fluentd image ───────────────────────────────────────────────────────
build_fluentd_image() {
  step "Building Fluentd Docker image (fluent-plugin-elasticsearch)"
  dc -f "$DOCKER_COMPOSE_FILE" build fluentd
  success "Fluentd image ready"
}

# ─── Infrastructure ────────────────────────────────────────────────────────────
start_infra() {
  step "Starting core infrastructure (Docker)"

  # Build Fluentd image first — must exist before any service sends logs
  build_fluentd_image

  # Start Elasticsearch early — Fluentd depends_on it
  info "Starting Elasticsearch…"
  dc -f "$DOCKER_COMPOSE_FILE" up -d elasticsearch
  # Give ES time to warm up before Fluentd starts
  sleep 15

  # Bring up the rest of core infra
  dc -f "$DOCKER_COMPOSE_FILE" up -d "${INFRA_CORE[@]}"

  # Wait for Postgres
  info "Waiting for Postgres to be ready…"
  local retries=20
  for i in $(seq 1 $retries); do
    if docker exec clearing-db pg_isready -U admin -d clearing &>/dev/null; then
      success "Postgres is ready (internal)"
      break
    fi
    [[ $i -eq $retries ]] && { error "Postgres did not become ready in time"; return 1; }
    sleep 2
  done

  # On macOS/Docker Desktop, the container might be ready internally before
  # the host port mapping is active. We wait for host reachability here.
  wait_for_port "Postgres (Host Port Binding)" 5433 15

  # Reset Postgres schemas so migrations run cleanly on every start
  info "Resetting Postgres schemas…"
  docker exec clearing-db psql -U admin -d clearing -q -c "
    DROP SCHEMA IF EXISTS public CASCADE; CREATE SCHEMA public;
    DROP SCHEMA IF EXISTS clearing CASCADE; CREATE SCHEMA clearing;
    DROP SCHEMA IF EXISTS notification CASCADE; CREATE SCHEMA notification;
    DROP SCHEMA IF EXISTS corporate_action CASCADE; CREATE SCHEMA corporate_action;
    DROP SCHEMA IF EXISTS refdata CASCADE; CREATE SCHEMA refdata;
    DROP SCHEMA IF EXISTS position CASCADE; CREATE SCHEMA position;
    DROP SCHEMA IF EXISTS margin CASCADE; CREATE SCHEMA margin;
    DROP SCHEMA IF EXISTS trade CASCADE; CREATE SCHEMA trade;
  " && success "Postgres schemas cleared and recreated"

  # Wait for Kafka
  info "Waiting for Kafka topics to be created…"
  sleep 12

  # Start observability stack (Fluentd, Prometheus, Alertmanager, Grafana, Kibana)
  step "Starting observability stack"
  dc -f "$DOCKER_COMPOSE_FILE" up -d "${INFRA_OBS[@]}"
  sleep 5
  sleep 5

  success "Infrastructure ready"
  info "Waiting 10s for Postgres and Kafka to fully initialize..."
  sleep 10
  start_infra_logs
}

start_infra_only() {
  step "Starting infrastructure (core + observability)"
  build_fluentd_image
  dc -f "$DOCKER_COMPOSE_FILE" up -d
  wait_for_port "Postgres"     5433 30
  wait_for_port "Kafka"        9092 60
  wait_for_port "Elasticsearch" 9200 60
  success "All infrastructure containers started"
  start_infra_logs
}

stop_infra() {
  step "Stopping infrastructure"
  dc -f "$DOCKER_COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
  
  # Clean up background log-tailing PIDs for infra
  rm -f "$PID_DIR"/*_logs.pid 2>/dev/null || true

  # Gracefully terminate the 3MB auto-archive log rotator
  if [[ -f "$PID_DIR/log_rotator_daemon.pid" ]]; then
    kill "$(cat "$PID_DIR/log_rotator_daemon.pid")" 2>/dev/null || true
    rm -f "$PID_DIR/log_rotator_daemon.pid" 2>/dev/null || true
  fi

  success "Infrastructure stopped"
}

start_infra_logs() {
  info "Tailing infrastructure logs to ${LOG_DIR}…"
  mkdir -p "$PID_DIR"
  local containers
  containers=$(dc -f "$DOCKER_COMPOSE_FILE" ps --services 2>/dev/null || true)
  for c in $containers; do
    # Tail logs in background and save standard out + err to the service log file
    dc -f "$DOCKER_COMPOSE_FILE" logs -f "$c" > "$LOG_DIR/${c}.log" 2>&1 &
    echo $! > "$PID_DIR/${c}_logs.pid"
  done

  info "Starting background 3MB auto-archive daemon for all logs…"
  (
    while true; do
      for log in "$LOG_DIR"/*.log; do
        [[ -f "$log" ]] || continue
        size=$(wc -c < "$log" 2>/dev/null | tr -d ' ' || echo 0)
        if [[ "$size" -gt 3145728 ]]; then
          local ts
          ts=$(date +%Y%m%d_%H%M%S)
          cp "$log" "${log}.${ts}.archive" && > "$log"
        fi
      done
      sleep 10
    done
  ) &
  echo $! > "$PID_DIR/log_rotator_daemon.pid"
}

# ─── Maven build ───────────────────────────────────────────────────────────────
build_all() {
  step "Building all Maven modules (skipping tests)"
  cd "$ROOT"
  mvn clean install -DskipTests -q
  success "Build complete — all JARs installed locally"
}

# ─── Spring Services ───────────────────────────────────────────────────────────
start_service() {
  local entry=$1
  local name; name="$(service_name "$entry")"
  local port; port="$(service_port "$entry")"
  local log;  log="$(log_file "$name")"
  local pid_f; pid_f="$(pid_file "$name")"

  if is_running "$name"; then
    warn "$name already running (pid $(cat "$pid_f"))"
    return 0
  fi

  info "Starting ${name} on port ${port}…"
  (
    cd "$ROOT/$name"
    DB_PORT=$DB_PORT \
    DB_HOST=$DB_HOST \
    DB_USERNAME=admin \
    DB_PASSWORD=admin \
    mvn spring-boot:run \
      -Dspring-boot.run.profiles=local \
      -Dspring-boot.run.jvmArguments="-Xms128m -Xmx512m" \
      -q >"$log" 2>&1
  ) &
  echo $! >"$pid_f"

  wait_for_port "$name" "$port" || return 1
}

start_services() {
  step "Starting all Spring services"
  mkdir -p "$LOG_DIR" "$PID_DIR"

  for entry in "${SERVICES[@]}"; do
    start_service "$entry"
  done
  success "All services started"
}

stop_services() {
  step "Stopping all Spring services"
  for entry in "${SERVICES[@]}"; do
    local name; name="$(service_name "$entry")"
    local port; port="$(service_port "$entry")"
    local pid_f; pid_f="$(pid_file "$name")"
    
    # Try PID file first
    if is_running "$name"; then
      local pid; pid=$(cat "$pid_f")
      info "Stopping ${name} (pid ${pid})…"
      kill "$pid" 2>/dev/null || true
      local timeout=10
      while kill -0 "$pid" 2>/dev/null && [[ $timeout -gt 0 ]]; do
        sleep 0.5; (( timeout-- ))
      done
      
      # Forced kill if still running
      if kill -0 "$pid" 2>/dev/null; then
        warn "Service $name did not stop gracefully; force killing…"
        kill -9 "$pid" 2>/dev/null || true
      fi
      rm -f "$pid_f"
      success "$name stopped"
    fi
    
    # Secondary cleanup: kill any orphan on the service port
    kill_by_port "$port"
  done
}

kill_by_port() {
  local port=$1
  local pids; pids=$(lsof -ti :"$port" 2>/dev/null || true)
  for p in $pids; do
    info "Purging orphaned process on port ${port} (PID: ${p})…"
    kill -9 "$p" 2>/dev/null || true
  done
}

# ─── Status ────────────────────────────────────────────────────────────────────
show_status() {
  step "Spring Services"
  printf "%-32s %-8s %-12s %s\n" "SERVICE" "PORT" "STATUS" "HEALTH"
  printf "%-32s %-8s %-12s %s\n" "───────" "────" "──────" "──────"

  for entry in "${SERVICES[@]}"; do
    local name; name="$(service_name "$entry")"
    local port; port="$(service_port "$entry")"
    local svc_status health

    if is_running "$name"; then
      svc_status="${GREEN}RUNNING${RESET}"
      health=$(curl -s "http://localhost:$port/actuator/health" 2>/dev/null \
               | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "?")
      [[ "$health" == "UP" ]] && health="${GREEN}UP${RESET}" || health="${YELLOW}${health:-?}${RESET}"
    else
      svc_status="${RED}STOPPED${RESET}"
      health="${RED}DOWN${RESET}"
    fi

    printf "%-32s %-8s %-20b %b\n" "$name" "$port" "$svc_status" "$health"
  done

  step "Infrastructure (Docker)"
  dc -f "$DOCKER_COMPOSE_FILE" ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}" 2>/dev/null || true

  step "Observability URLs"
  dim "Grafana         → http://localhost:3000      (admin / admin)"
  dim "Prometheus      → http://localhost:9090"
  dim "Alertmanager    → http://localhost:9093"
  dim "Kibana          → http://localhost:5601"
  dim "Kafka UI        → http://localhost:8091"
  dim "Elasticsearch   → http://localhost:9200"
  dim "Participant Portal → http://localhost:5173   (npm run dev)"
}

# ─── Portal ────────────────────────────────────────────────────────────────────
start_portal() {
  step "Starting React Participant Portal"
  require node; require npm

  local portal_dir="$ROOT/participant-portal"
  [[ -d "$portal_dir/node_modules" ]] || {
    info "Installing npm dependencies…"
    (cd "$portal_dir" && npm install --silent)
  }

  info "Launching Vite dev server on http://localhost:5173"
  info "Press Ctrl+C to stop the portal"
  (cd "$portal_dir" && npm run dev)
}

# ─── E2E Tests ─────────────────────────────────────────────────────────────────
PASS=0; FAIL=0

assert_eq() {
  local label=$1 actual=$2 expected=$3
  if [[ "$actual" == "$expected" ]]; then
    echo "  ${GREEN}✔${RESET} $label (got: $actual)"
    (( PASS++ )) || true
  else
    echo "  ${RED}✘${RESET} $label (expected: $expected, got: $actual)"
    (( FAIL++ )) || true
  fi
}

assert_nonempty() {
  local label=$1 actual=$2
  if [[ -n "$actual" && "$actual" != "null" && "$actual" != "[]" ]]; then
    echo "  ${GREEN}✔${RESET} $label"
    (( PASS++ )) || true
  else
    echo "  ${RED}✘${RESET} $label (empty or null)"
    (( FAIL++ )) || true
  fi
}

run_e2e_tests() {
  trap - ERR
  PASS=0; FAIL=0
  step "E2E Test: Full Trade Settlement Flow (with JWT Auth)"

  # ── 0. Acquire JWT from Gateway ───────────────────────────────────────────
  info "Acquiring access token for test user (admin)..."
  local token_resp
  token_resp=$(curl -s -X POST "http://localhost:8080/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"admin123"}')
  
  local TOKEN
  TOKEN=$(echo "$token_resp" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
  
  if [[ -z "$TOKEN" || "$TOKEN" == "null" ]]; then
    error "Failed to acquire JWT. Response: $token_resp"
    return 1
  fi
  success "Token acquired"
  local AUTH_HDR="Authorization: Bearer $TOKEN"

  # ── 1. Seed seller position ────────────────────────────────────────────────
  echo "\n${BOLD}[1] Seeding seller position (ACC002 → 200 AAPL via position-service)${RESET}"
  local fund_resp
  fund_resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST \
    http://localhost:8082/api/v1/positions/fund \
    -H "$AUTH_HDR" \
    -H "Content-Type: application/json" \
    -d '{"accountId":"ACC002","instrumentId":"AAPL","quantity":200}')
  assert_eq "Fund seller position (HTTP 201)" "$fund_resp" "201"

  # ── 2. Submit trade ────────────────────────────────────────────────────────
  # Route via API Gateway (8080) to verify routing + authentication
  echo "\n${BOLD}[2] Submitting trade via API Gateway (ACC001 buys 100 AAPL @ \$150)${RESET}"
  local trade_resp trade_body trade_http trade_id trade_status
  trade_resp=$(curl -s -w "\n%{http_code}" -X POST \
    http://localhost:8080/api/v1/trades \
    -H "$AUTH_HDR" \
    -H "Content-Type: application/json" \
    -d '{"instrumentId":"AAPL","quantity":100,"price":150.00,"buyerAccountId":"ACC001","sellerAccountId":"ACC002"}')
  trade_body=$(echo "$trade_resp" | head -1)
  trade_http=$(echo "$trade_resp" | tail -1)
  assert_eq "Trade submission via gateway (HTTP 201)" "$trade_http" "201"

  trade_id=$(echo "$trade_body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  assert_nonempty "Trade ID returned" "$trade_id"
  echo "     Trade ID: ${BOLD}$trade_id${RESET}"

  trade_status=$(echo "$trade_body" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
  assert_eq "Initial trade status is SUBMITTED" "$trade_status" "SUBMITTED"

  # ── 3. Poll clearing settlement ────────────────────────────────────────────
  echo "\n${BOLD}[3] Polling clearing-service for READY_FOR_NETTING status${RESET}"
  local settle_status=""
  for i in $(seq 1 25); do
    settle_status=$(curl -s "http://localhost:8083/api/v1/clearing/settlements/$trade_id" \
      -H "$AUTH_HDR" \
      2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
    [[ "$settle_status" == "READY_FOR_NETTING" ]] && break
    sleep 2
  done
  assert_eq "Clearing settlement status is READY_FOR_NETTING" "$settle_status" "READY_FOR_NETTING"

  # ── 3.5. Trigger EOD Netting ───────────────────────────────────────────────
  echo "\n${BOLD}[3.5] Triggering Multilateral EOD Netting${RESET}"
  local netting_resp
  netting_resp=$(curl -s -w "\n%{http_code}" -X POST \
    "http://localhost:8083/api/v1/netting/trigger?businessDate=2026-06-25" \
    -H "$AUTH_HDR")
  local netting_http
  netting_http=$(echo "$netting_resp" | tail -1)
  assert_eq "Trigger Netting (HTTP 200)" "$netting_http" "200"

  echo "\n${BOLD}[3.6] Polling clearing-service for NETTED status${RESET}"
  for i in $(seq 1 10); do
    settle_status=$(curl -s "http://localhost:8083/api/v1/clearing/settlements/$trade_id" \
      -H "$AUTH_HDR" \
      2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
    [[ "$settle_status" == "NETTED" ]] && break
    sleep 2
  done
  assert_eq "Clearing settlement status is NETTED" "$settle_status" "NETTED"

  # ── 4. Poll trade-service for SETTLED status ───────────────────────────────
  echo "\n${BOLD}[4] Polling trade-service — trade status should flip to SETTLED${RESET}"
  local trade_settled_status=""
  for i in $(seq 1 20); do
    trade_settled_status=$(curl -s "http://localhost:8081/api/v1/trades/$trade_id" \
      -H "$AUTH_HDR" \
      2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
    [[ "$trade_settled_status" == "SETTLED" ]] && break
    sleep 2
  done
  assert_eq "Trade status updated to SETTLED (via Kafka consumer)" "$trade_settled_status" "SETTLED"

  # ── 5. Check notifications ─────────────────────────────────────────────────
  echo "\n${BOLD}[5] Checking notification-service for settlement notifications${RESET}"
  local notif_body=""
  for i in $(seq 1 20); do
    notif_body=$(curl -s "http://localhost:8084/api/v1/notifications/trades/$trade_id" \
      -H "$AUTH_HDR" \
      2>/dev/null || echo "")
    [[ -n "$notif_body" && "$notif_body" != "[]" ]] && break
    sleep 2
  done
  assert_nonempty "Notifications dispatched for trade" "$notif_body"

  # ── 6. Verify positions updated ────────────────────────────────────────────
  echo "\n${BOLD}[6] Verifying buyer position (ACC001 should have +100 AAPL)${RESET}"
  local buyer_qty
  buyer_qty=$(curl -s "http://localhost:8082/api/v1/positions?accountId=ACC001" \
    -H "$AUTH_HDR" \
    2>/dev/null | grep -o '"quantity":[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
  assert_eq "Buyer ACC001 holds 100 AAPL" "$buyer_qty" "100"

  echo "\n${BOLD}[7] Verifying seller position (ACC002 should have 100 AAPL remaining)${RESET}"
  local seller_qty
  seller_qty=$(curl -s "http://localhost:8082/api/v1/positions?accountId=ACC002" \
    -H "$AUTH_HDR" \
    2>/dev/null | grep -o '"quantity":[0-9]*' | grep -o '[0-9]*' | head -1 || echo "0")
  assert_eq "Seller ACC002 holds 100 AAPL" "$seller_qty" "100"


  # ── Summary ────────────────────────────────────────────────────────────────
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  if [[ $FAIL -eq 0 ]]; then
    success "All E2E tests passed ✅  (${PASS}/${PASS})"
  else
    error   "Tests: ${PASS} passed, ${FAIL} FAILED ❌"
    return 1
  fi
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# ─── Logs ──────────────────────────────────────────────────────────────────────
show_logs() {
  local name=${1:-""}
  [[ -z "$name" ]] && { error "Usage: ./dev.sh logs <service-name>"; exit 1; }
  local log; log="$(log_file "$name")"
  [[ -f "$log" ]] || { error "No log file for '$name' at $log"; exit 1; }
  info "Tailing $log — Ctrl+C to exit"
  tail -f "$log"
}

# ─── Main dispatch ─────────────────────────────────────────────────────────────
case "${1:-}" in

  start)
    require docker; require mvn; require nc
    step "PostTradeClearingPlatform — Starting Full Stack"
    start_infra
    build_all
    start_services
    echo ""
    show_status
    echo ""
    success "Platform is UP 🚀"
    echo ""
    dim "Quick test:"
    dim "  curl -X POST http://localhost:8081/api/v1/trades \\"
    dim "    -H 'Content-Type: application/json' \\"
    dim "    -d '{\"instrumentId\":\"AAPL\",\"quantity\":100,\"price\":150.00,\"buyerAccountId\":\"ACC001\",\"sellerAccountId\":\"ACC002\"}'"
    echo ""
    dim "React Portal:   cd participant-portal && npm run dev   → http://localhost:5173"
    dim "Grafana:        http://localhost:3000"
    dim "Prometheus:     http://localhost:9090"
    dim "Alertmanager:   http://localhost:9093"
    dim "Kibana:         http://localhost:5601"
    dim "Kafka UI:       http://localhost:8091"
    ;;

  stop)
    require docker
    trap - ERR
    stop_services
    stop_infra
    success "Platform is DOWN"
    ;;

  restart)
    require docker; require mvn; require nc
    trap - ERR
    stop_services
    stop_infra
    trap rollback ERR
    start_infra
    build_all
    start_services
    show_status
    ;;

  infra-only)
    require docker
    start_infra_only
    show_status
    ;;

  build)
    require mvn
    trap - ERR
    build_all
    ;;

  status)
    trap - ERR
    show_status
    ;;

  test)
    trap - ERR
    run_e2e_tests
    ;;

  portal)
    trap - ERR
    start_portal
    ;;

  logs)
    trap - ERR
    show_logs "${2:-}"
    ;;

  *)
    echo ""
    echo "${BOLD}PostTradeClearingPlatform — dev.sh${RESET}"
    echo ""
    echo "Usage: $0 <command> [options]"
    echo ""
    printf "  %-18s %s\n" "start"       "Build Fluentd image + start all infra + all Spring services"
    printf "  %-18s %s\n" "stop"        "Stop all services and infrastructure"
    printf "  %-18s %s\n" "restart"     "stop + start"
    printf "  %-18s %s\n" "infra-only"  "Start Docker infra only (no Spring services)"
    printf "  %-18s %s\n" "build"       "Maven build only (no start)"
    printf "  %-18s %s\n" "status"      "Show running state and health of all services"
    printf "  %-18s %s\n" "test"        "Run full E2E trade settlement flow (7 assertions)"
    printf "  %-18s %s\n" "portal"      "Start React Participant Portal on http://localhost:5173"
    printf "  %-18s %s\n" "logs <svc>"  "Tail Spring service log (e.g. trade-service)"
    echo ""
    echo "Spring Services:"
    for e in "${SERVICES[@]}"; do
      printf "  %-32s port %s\n" "$(service_name "$e")" "$(service_port "$e")"
    done
    echo ""
    echo "Observability:"
    dim "  Grafana       http://localhost:3000"
    dim "  Prometheus    http://localhost:9090"
    dim "  Alertmanager  http://localhost:9093"
    dim "  Kibana        http://localhost:5601"
    dim "  Kafka UI      http://localhost:8091"
    dim "  Portal        http://localhost:5173"
    echo ""
    exit 1
    ;;
esac
