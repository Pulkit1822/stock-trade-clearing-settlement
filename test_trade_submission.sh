#!/bin/bash

# Configuration
REALM_URL="http://localhost:8180/realms/clearing"
GATEWAY_URL="http://localhost:8080/api/v1/trades"
CLIENT_ID="clearing-client"
CLIENT_SECRET="secret"
USERNAME="user"
PASSWORD="password"

echo "--------------------------------------------------"
echo "Step 1: Acquiring fresh JWT from Keycloak..."
echo "--------------------------------------------------"

TOKEN_RESPONSE=$(curl -s -X POST "$REALM_URL/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=$CLIENT_ID" \
  -d "client_secret=$CLIENT_SECRET" \
  -d "username=$USERNAME" \
  -d "password=$PASSWORD" \
  -d "grant_type=password")

TOKEN=$(echo $TOKEN_RESPONSE | jq -r .access_token)

if [ "$TOKEN" == "null" ] || [ -z "$TOKEN" ]; then
    echo "ERROR: Failed to acquire token. Full response:"
    echo $TOKEN_RESPONSE
    exit 1
fi

echo "Token acquired successfully."

echo "--------------------------------------------------"
echo "Step 2: Submitting Standardized Trade Request..."
echo "--------------------------------------------------"

RESPONSE=$(curl -s -i -X POST "$GATEWAY_URL" \
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
