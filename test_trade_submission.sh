#!/bin/bash

# Configuration — no Keycloak needed.
# Uses the API Gateway's own /api/auth/login to issue a signed HS256 JWT.
GATEWAY_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"

echo "--------------------------------------------------"
echo "Step 1: Acquiring JWT from API Gateway..."
echo "--------------------------------------------------"

TOKEN_RESPONSE=$(curl -s -X POST "$GATEWAY_URL/api/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}")

TOKEN=$(echo "$TOKEN_RESPONSE" | jq -r .token)

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
    echo "ERROR: Failed to acquire token. Full response:"
    echo "$TOKEN_RESPONSE"
    exit 1
fi

echo "Token acquired successfully."

echo "--------------------------------------------------"
echo "Step 2: Submitting Standardized Trade Request..."
echo "--------------------------------------------------"

RESPONSE=$(curl -s -i -X POST "$GATEWAY_URL/api/v1/trades" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "instrumentId": "AAPL",
    "buyerAccountId": "BUYER-999",
    "sellerAccountId": "SELLER-001",
    "quantity": 100,
    "price": 150.75
}')

echo "$RESPONSE"

echo "--------------------------------------------------"
echo "Step 3: Verification Instructions"
echo "--------------------------------------------------"
echo "1. Check trade-service logs for 'Successfully published TradeSubmittedEvent'"
echo "2. Check position-service logs for 'Trade validated successfully'"
echo "3. Query DB: docker exec -t clearing-db psql -U admin -d clearing -c \"SELECT * FROM trades ORDER BY created_at DESC LIMIT 1;\""
