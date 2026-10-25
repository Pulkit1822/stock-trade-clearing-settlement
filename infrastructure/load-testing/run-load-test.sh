#!/bin/bash

# Navigate to the script's directory and then project root just to be safe
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" &> /dev/null && pwd)"
ROOT_DIR="$(dirname $(dirname "$SCRIPT_DIR"))"

cd "$SCRIPT_DIR"

echo "--------------------------------------------------"
echo "Step 1: Scaling/Seeding DB Positions..."
echo "--------------------------------------------------"

echo "Seeding 100 heavily-funded seller accounts into the PostgreSQL database..."
docker exec -i clearing-db psql -U admin -d clearing < seed_positions.sql
echo "DB successfully seeded."

echo "--------------------------------------------------"
echo "Step 2: Starting k6 Throughput Test..."
echo "--------------------------------------------------"

echo "Target: 1000 trades/minute (17 RPS continuous for 1 min)"
# Using docker to run k6 so it's not required to be installed natively
# Passing the script via stdin (-) since we are mounting from OSX
docker run --rm -i --add-host host.docker.internal:host-gateway grafana/k6 run - < k6-trade-throughput.js

echo "--------------------------------------------------"
echo "Done."
