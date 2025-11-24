#!/bin/bash
# Initialize Keycloak for local development
# This script disables SSL requirement for master and imported realms

set -e

echo "Waiting for Keycloak to be ready..."

# Wait for Keycloak to be fully started (max 60 seconds)
for i in {1..60}; do
  if curl -s http://keycloak:8080/health/ready > /dev/null 2>&1; then
    echo "Keycloak is ready!"
    break
  fi
  if [ $i -eq 60 ]; then
    echo "Timeout waiting for Keycloak to start"
    exit 1
  fi
  sleep 1
done

# Wait an additional 5 seconds to ensure all services are initialized
sleep 5

echo "Configuring Keycloak for local development..."

# Get admin access token
echo "Getting admin token..."
TOKEN=$(curl -s -X POST "http://keycloak:8080/realms/master/protocol/openid-connect/token" \
  -d "client_id=admin-cli" \
  -d "username=admin" \
  -d "password=admin" \
  -d "grant_type=password" | grep -o '"access_token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "Failed to get admin token"
  exit 1
fi

echo "Token obtained successfully"

# Disable SSL requirement for master realm
echo "Disabling SSL requirement for master realm..."
curl -s -X PUT "http://keycloak:8080/admin/realms/master" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"realm":"master","sslRequired":"none"}' > /dev/null

echo "✓ Master realm SSL requirement set to: none"

echo ""
echo "=========================================="
echo "Keycloak initialization complete!"
echo "Master realm: http://localhost:8080"
echo "Credentials: admin / admin"
echo "=========================================="
