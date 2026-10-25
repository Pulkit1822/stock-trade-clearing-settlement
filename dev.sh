#!/usr/bin/env zsh
# dev.sh — PostTradeClearingPlatform local environment manager
#
# Usage:
#   ./dev.sh start     — build, start infra + all 6 services
#   ./dev.sh stop      — gracefully stop all services + infra
#   ./dev.sh restart   — stop then start
#   ./dev.sh status    — show what's running
#   ./dev.sh logs <svc>— tail logs for a service (e.g. trade-service)

set -euo pipefail

# ─── Config ────────────────────────────────────────────────────────────────────
ROOT="$(cd "$(dirname "$0")" && pwd)"
DOCKER_COMPOSE_FILE="$ROOT/infrastructure/docker/docker-compose.yml"
LOG_DIR="$ROOT/.dev/logs"
PID_DIR="$ROOT/.dev/pids"
DB_PORT=5433

SERVICES=(
  trade-service:8081
  position-service:8082
  clearing-service:8083
  notification-service:8084
  reference-data-service:8085
  corporate-action-service:8086
)

INFRA_CONTAINERS=(postgres kafka zookeeper redis kafka-init)

# ─── Colours ───────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
BLUE='\033[0;34m'; BOLD='\033[1m'; RESET='\033[0m'

info()    { echo "${BLUE}[INFO]${RESET}  $*"; }
success() { echo "${GREEN}[OK]${RESET}    $*"; }
warn()    { echo "${YELLOW}[WARN]${RESET}  $*"; }
error()   { echo "${RED}[ERROR]${RESET} $*" >&2; }
step()    { echo "\n${BOLD}▶ $*${RESET}"; }

# ─── Helpers ───────────────────────────────────────────────────────────────────
require() {
  command -v "$1" &>/dev/null || { error "'$1' not found. Please install it."; exit 1; }
}

pid_file()  { echo "$PID_DIR/$1.pid"; }
log_file()  { echo "$LOG_DIR/$1.log"; }

service_name() { echo "${1%%:*}"; }   # trade-service:8081 → trade-service
service_port() { echo "${1##*:}"; }   # trade-service:8081 → 8081

is_running() {
  local pid_f; pid_f="$(pid_file "$1")"
  [[ -f "$pid_f" ]] && kill -0 "$(cat "$pid_f")" 2>/dev/null
}

wait_for_port() {
  local name=$1 port=$2 retries=60
  info "Waiting for $name on port $port..."
  for i in $(seq 1 $retries); do
    if nc -z localhost "$port" 2>/dev/null; then
      success "$name is up (port $port)"
      return 0
    fi
    sleep 2
  done
  error "$name failed to start on port $port after $((retries * 2))s"
  return 1
}

# ─── Rollback ──────────────────────────────────────────────────────────────────
rollback() {
  error "Something went wrong. Rolling back..."
  stop_services
  stop_infra
  error "Rollback complete. Check logs in $LOG_DIR"
  exit 1
}
trap rollback ERR

# ─── Infra ─────────────────────────────────────────────────────────────────────
start_infra() {
  step "Starting infrastructure (Docker)"
  docker-compose -f "$DOCKER_COMPOSE_FILE" up -d "${INFRA_CONTAINERS[@]}"

  # Wait for Postgres
  info "Waiting for Postgres to be ready..."
  local retries=20
  for i in $(seq 1 $retries); do
    if docker exec clearing-db pg_isready -U admin -d clearing &>/dev/null; then
      success "Postgres is ready"
      break
    fi
    [[ $i -eq $retries ]] && { error "Postgres did not become ready in time"; return 1; }
    sleep 2
  done

  # Reset Flyway history tables so each service runs migrations cleanly from scratch
  info "Resetting Flyway schema history..."
  docker exec clearing-db psql -U admin -d clearing -q -c "
    DROP TABLE IF EXISTS flyway_schema_history;
    DROP TABLE IF EXISTS clearing_flyway_history;
    DROP TABLE IF EXISTS notification_flyway_history;
    DROP TABLE IF EXISTS corporate_action_flyway_history;
    DROP TABLE IF EXISTS refdata_flyway_history;
    DROP TABLE IF EXISTS position_flyway_history;
  " && success "Flyway history cleared"

  # Wait for Kafka
  info "Waiting for Kafka topics to be created..."
  sleep 12
  success "Infrastructure ready"
}

stop_infra() {
  step "Stopping infrastructure"
  docker-compose -f "$DOCKER_COMPOSE_FILE" down --remove-orphans 2>/dev/null || true
  success "Infrastructure stopped"
}

# ─── Build ─────────────────────────────────────────────────────────────────────
build_all() {
  step "Building all Maven modules"
  cd "$ROOT"
  mvn clean package -DskipTests -q
  success "Build complete"
}

# ─── Services ──────────────────────────────────────────────────────────────────
start_service() {
  local entry=$1
  local name; name="$(service_name "$entry")"
  local port; port="$(service_port "$entry")"
  local log; log="$(log_file "$name")"
  local pid_f; pid_f="$(pid_file "$name")"

  if is_running "$name"; then
    warn "$name already running (pid $(cat "$pid_f"))"
    return 0
  fi

  info "Starting $name on port $port..."
  (
    cd "$ROOT/$name"
    DB_PORT=$DB_PORT \
    mvn spring-boot:run \
      -Dspring-boot.run.profiles=local \
      -Dspring-boot.run.jvmArguments="-Xms128m -Xmx512m" \
      -q > "$log" 2>&1
  ) &
  echo $! > "$pid_f"

  wait_for_port "$name" "$port" || return 1
}

start_services() {
  step "Starting all services"
  mkdir -p "$LOG_DIR" "$PID_DIR"

  for entry in "${SERVICES[@]}"; do
    start_service "$entry"
  done
  success "All services started"
}

stop_services() {
  step "Stopping all services"
  for entry in "${SERVICES[@]}"; do
    local name; name="$(service_name "$entry")"
    local pid_f; pid_f="$(pid_file "$name")"
    if is_running "$name"; then
      info "Stopping $name (pid $(cat "$pid_f"))..."
      kill "$(cat "$pid_f")" 2>/dev/null || true
      # Wait for process to exit
      local timeout=10
      while kill -0 "$(cat "$pid_f")" 2>/dev/null && [[ $timeout -gt 0 ]]; do
        sleep 1; (( timeout-- ))
      done
      rm -f "$pid_f"
      success "$name stopped"
    else
      info "$name was not running"
      rm -f "$pid_f"
    fi
  done
}

# ─── Status ────────────────────────────────────────────────────────────────────
show_status() {
  step "Service status"
  printf "%-30s %-8s %-10s %s\n" "SERVICE" "PORT" "STATUS" "HEALTH"
  printf "%-30s %-8s %-10s %s\n" "-------" "----" "------" "------"

  for entry in "${SERVICES[@]}"; do
    local name; name="$(service_name "$entry")"
    local port; port="$(service_port "$entry")"
    local svc_status health

    if is_running "$name"; then
      svc_status="${GREEN}RUNNING${RESET}"
      health=$(curl -s "http://localhost:$port/actuator/health" 2>/dev/null \
               | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "?")
      [[ "$health" == "UP" ]] && health="${GREEN}UP${RESET}" || health="${YELLOW}$health${RESET}"
    else
      svc_status="${RED}STOPPED${RESET}"
      health="${RED}DOWN${RESET}"
    fi

    printf "%-30s %-8s %-20s %s\n" "$name" "$port" "$(echo -e "$svc_status")" "$(echo -e "$health")"
  done

  echo ""
  step "Infrastructure"
  docker-compose -f "$DOCKER_COMPOSE_FILE" ps --format "table {{.Name}}\t{{.Status}}" 2>/dev/null \
    | grep -E "NAME|kafka|postgres|redis|zookeeper" || true
}

# ─── E2E Test ──────────────────────────────────────────────────────────────────
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

# Poll a URL until a jq expression returns the expected value or timeout
poll_until() {
  local label=$1 url=$2 jq_expr=$3 expected=$4 retries=${5:-20}
  local actual
  for i in $(seq 1 $retries); do
    actual=$(curl -s "$url" 2>/dev/null | grep -o "\"${jq_expr##*.}\":\"[^\"]*\"" | head -1 | cut -d'"' -f4 || echo "")
    [[ "$actual" == "$expected" ]] && { assert_eq "$label" "$actual" "$expected"; return 0; }
    sleep 2
  done
  echo "  ${RED}✘${RESET} $label — timed out waiting for '$expected', last value: '$actual'"
  (( FAIL++ )) || true
  return 1
}

poll_array_nonempty() {
  local label=$1 url=$2 retries=${3:-20}
  local body
  for i in $(seq 1 $retries); do
    body=$(curl -s "$url" 2>/dev/null || echo "")
    if [[ -n "$body" && "$body" != "[]" && "$body" != "null" ]]; then
      assert_nonempty "$label" "$body"
      return 0
    fi
    sleep 2
  done
  echo "  ${RED}✘${RESET} $label — timed out, response still empty"
  (( FAIL++ )) || true
}

run_e2e_tests() {
  trap - ERR   # disable rollback inside test runner
  PASS=0; FAIL=0
  step "E2E Test: Full Trade Settlement Flow"

  # ── 1. Seed seller position ─────────────────────────────────────────────────
  echo "\n${BOLD}[1] Seeding seller position (ACC002 → 200 AAPL)${RESET}"
  local fund_resp
  fund_resp=$(curl -s -o /dev/null -w "%{http_code}" -X POST http://localhost:8082/api/v1/positions/fund \
    -H "Content-Type: application/json" \
    -d '{"accountId":"ACC002","instrumentId":"AAPL","quantity":200}')
  assert_eq "Fund seller position (HTTP 201)" "$fund_resp" "201"

  # ── 2. Submit trade ──────────────────────────────────────────────────────────
  echo "\n${BOLD}[2] Submitting trade (ACC001 buys 100 AAPL from ACC002 @ \$150)${RESET}"
  local trade_resp trade_id trade_status
  trade_resp=$(curl -s -w "\n%{http_code}" -X POST http://localhost:8081/api/v1/trades \
    -H "Content-Type: application/json" \
    -d '{"instrumentId":"AAPL","quantity":100,"price":150.00,"buyerAccountId":"ACC001","sellerAccountId":"ACC002"}')
  local trade_body trade_http
  trade_body=$(echo "$trade_resp" | head -1)
  trade_http=$(echo "$trade_resp" | tail -1)
  assert_eq "Trade submission (HTTP 201)" "$trade_http" "201"

  trade_id=$(echo "$trade_body" | grep -o '"id":"[^"]*"' | head -1 | cut -d'"' -f4)
  assert_nonempty "Trade ID returned" "$trade_id"
  echo "     Trade ID: ${BOLD}$trade_id${RESET}"

  trade_status=$(echo "$trade_body" | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4)
  assert_eq "Trade status is SUBMITTED" "$trade_status" "SUBMITTED"

  # ── 3. Poll settlement ───────────────────────────────────────────────────────
  echo "\n${BOLD}[3] Waiting for settlement (clearing-service)${RESET}"
  local settle_url="http://localhost:8083/api/v1/clearing/settlements/$trade_id"
  local settle_status=""
  for i in $(seq 1 25); do
    settle_status=$(curl -s "$settle_url" 2>/dev/null | grep -o '"status":"[^"]*"' | head -1 | cut -d'"' -f4 || echo "")
    [[ "$settle_status" == "SETTLED" ]] && break
    sleep 2
  done
  assert_eq "Settlement status is SETTLED" "$settle_status" "SETTLED"

  # ── 4. Check notifications ───────────────────────────────────────────────────
  echo "\n${BOLD}[4] Checking notifications (notification-service)${RESET}"
  poll_array_nonempty "Notifications dispatched for trade" \
    "http://localhost:8084/api/v1/notifications/trades/$trade_id" 20

  # ── Summary ──────────────────────────────────────────────────────────────────
  echo ""
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  if [[ $FAIL -eq 0 ]]; then
    success "All tests passed (${PASS}/${PASS})"
  else
    error "Tests: ${PASS} passed, ${FAIL} failed"
    return 1
  fi
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

# ─── Logs ──────────────────────────────────────────────────────────────────────
show_logs() {
  local name=${1:-""}
  [[ -z "$name" ]] && { error "Usage: ./dev.sh logs <service-name>"; exit 1; }
  local log; log="$(log_file "$name")"
  [[ -f "$log" ]] || { error "No log file found for $name at $log"; exit 1; }
  tail -f "$log"
}

# ─── Main ──────────────────────────────────────────────────────────────────────
case "${1:-}" in
  start)
    require docker; require docker-compose; require mvn; require nc
    step "PostTradeClearingPlatform — Starting"
    start_infra
    build_all
    start_services
    echo ""
    show_status
    echo ""
    success "Platform is UP. Test with:"
    echo "  curl -X POST http://localhost:8081/api/v1/trades -H 'Content-Type: application/json' \\"
    echo "    -d '{\"instrumentId\":\"AAPL\",\"quantity\":100,\"price\":150.00,\"buyerAccountId\":\"ACC001\",\"sellerAccountId\":\"ACC002\"}'"
    echo "  Kafka UI: http://localhost:8091"
    ;;
  stop)
    require docker; require docker-compose
    trap - ERR
    stop_services
    stop_infra
    success "Platform is DOWN"
    ;;
  restart)
    require docker; require docker-compose; require mvn; require nc
    trap - ERR
    stop_services
    stop_infra
    trap rollback ERR
    start_infra
    build_all
    start_services
    show_status
    ;;
  status)
    trap - ERR
    show_status
    ;;
  test)
    trap - ERR
    run_e2e_tests
    ;;
  logs)
    trap - ERR
    show_logs "${2:-}"
    ;;
  *)
    echo "Usage: $0 {start|stop|restart|status|logs <service>}"
    echo ""
    echo "  start              Build and start everything"
    echo "  stop               Stop all services and infrastructure"
    echo "  restart            stop + start"
    echo "  status             Show running state and health of all services"
    echo "  test               Run full E2E trade flow and assert results"
  echo "  logs <service>     Tail logs for a specific service"
    echo ""
    echo "Services: trade-service, position-service, clearing-service,"
    echo "          notification-service, reference-data-service, corporate-action-service"
    exit 1
    ;;
esac
