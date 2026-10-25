#!/bin/bash

# Configuration — uses API Gateway's local AuthController
GATEWAY_URL="http://localhost:8080"
USERNAME="admin"
PASSWORD="admin123"
ACCOUNT_ID="MARGIN-ACCT-001"

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
echo "Step 2: Depositing Collateral to Margin Service..."
echo "--------------------------------------------------"

DEPOSIT_RESPONSE=$(curl -s -i -X POST "$GATEWAY_URL/api/v1/margin/collateral/$ACCOUNT_ID/deposit?amount=500.00" \
  -H "Authorization: Bearer $TOKEN")

echo "$DEPOSIT_RESPONSE"

echo "--------------------------------------------------"
echo "Step 3: Submitting Trade to trigger Margin Calculation..."
echo "--------------------------------------------------"

TRADE_RESPONSE=$(curl -s -i -X POST "$GATEWAY_URL/api/v1/trades" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{
    \"instrumentId\": \"AAPL\",
    \"buyerAccountId\": \"$ACCOUNT_ID\",
    \"sellerAccountId\": \"SELLER-002\",
    \"quantity\": 500,
    \"price\": 150.00
}")

echo "$TRADE_RESPONSE"

echo "--------------------------------------------------"
echo "Step 4: Checking Margin Status (Wait ~2 seconds)..."
echo "--------------------------------------------------"
sleep 2

STATUS_RESPONSE=$(curl -s -X GET "$GATEWAY_URL/api/v1/margin/status/$ACCOUNT_ID" \
  -H "Authorization: Bearer $TOKEN")

echo "$STATUS_RESPONSE" | jq .

echo "--------------------------------------------------"
echo "Verification Instructions:"
echo "1. The status response above should show a deficit if Required Margin > 500.00."
echo "2. Check notification-service logs to see the generated Margin Call notification."
echo "--------------------------------------------------"
