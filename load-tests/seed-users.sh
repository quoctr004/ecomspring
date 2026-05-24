#!/usr/bin/env bash
# Seeds 20 test users for the k6 login load test.
# Output: users.json (consumed by 02-login-flow.js)
set -euo pipefail

GW="${BASE_URL:-http://localhost:8080}"
COUNT="${USERS:-20}"
OUT="$(dirname "$0")/users.json"

echo "Seeding $COUNT test users at $GW ..."
echo "[" > "$OUT"

for i in $(seq 1 "$COUNT"); do
    EMAIL="loadtest+$i@k6.local"
    USERNAME="loadtest$i"
    PASSWORD="LoadTest123!"

    # Register (idempotent — 409 conflict OK if already exists)
    curl -s -X POST "$GW/api/v1/auth/register" \
        -H 'Content-Type: application/json' \
        -d "{\"username\":\"$USERNAME\",\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\",\"firstName\":\"K6\",\"lastName\":\"User\"}" \
        >/dev/null 2>&1 || true

    # Append entry; trailing comma except last
    if [ "$i" -lt "$COUNT" ]; then
        echo "  {\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}," >> "$OUT"
    else
        echo "  {\"email\":\"$EMAIL\",\"password\":\"$PASSWORD\"}" >> "$OUT"
    fi
done

echo "]" >> "$OUT"
echo "✓ Seeded $COUNT users → $OUT"
