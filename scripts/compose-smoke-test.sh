#!/usr/bin/env bash
# End-to-end check of the Docker Compose setup:
#   1. AutoBook and PostgreSQL start and GET /health reports UP (database included).
#   2. A customer books an appointment through the REST API.
#   3. The confirmation notification is sent after the booking commits.
#   4. The app container is restarted; the booking is still there afterwards.
#
# Usage (from the project folder, with Docker running and a .env file present):
#   docker compose up -d --build
#   ./scripts/compose-smoke-test.sh
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:${AUTOBOOK_PORT:-8080}}"
COOKIES="$(mktemp)"
trap 'rm -f "$COOKIES"' EXIT

json() { python3 -c "import sys, json; data = json.load(sys.stdin); print($1)"; }

wait_for_health() {
  for _ in $(seq 1 60); do
    if curl -fsS "$BASE_URL/health" > /dev/null 2>&1; then
      return 0
    fi
    sleep 2
  done
  echo "AutoBook did not report healthy at $BASE_URL/health" >&2
  docker compose logs --tail=80 app >&2 || true
  return 1
}

csrf_token() {
  curl -fsS -c "$COOKIES" -b "$COOKIES" "$BASE_URL/api/csrf" | json 'data["token"]'
}

login() {
  rm -f "$COOKIES"
  local token
  token="$(csrf_token)"
  curl -fsS -c "$COOKIES" -b "$COOKIES" -o /dev/null \
    --data-urlencode "username=$1" --data-urlencode "password=password123" --data-urlencode "_csrf=$token" \
    "$BASE_URL/login"
}

echo "== Waiting for AutoBook"
wait_for_health
health="$(curl -fsS "$BASE_URL/health")"
echo "$health"
[ "$(echo "$health" | json 'data["status"]')" = "UP" ]
[ "$(echo "$health" | json 'data["components"]["db"]["status"]')" = "UP" ]

echo "== Booking an appointment as a customer"
slot_id="$(curl -fsS "$BASE_URL/api/slots?size=1" | json 'data["content"][0]["slotId"]')"
login "alex.rivera@example.com"
token="$(csrf_token)"
appointment_id="$(curl -fsS -c "$COOKIES" -b "$COOKIES" -H "X-CSRF-TOKEN: $token" -H "Content-Type: application/json" \
  -d "{\"slotId\": $slot_id}" "$BASE_URL/api/appointments" | json 'data["appointmentId"]')"
echo "Booked slot $slot_id as appointment $appointment_id"

echo "== Checking the confirmation notification (admin view of the outbox)"
login "morgan.lee@example.com"
notification_status=""
for _ in $(seq 1 30); do
  notification_status="$(curl -fsS -b "$COOKIES" "$BASE_URL/api/admin/notifications" \
    | json "next((n['status'] for n in data if n['appointmentId'] == $appointment_id and n['type'] == 'BOOKING_CONFIRMED'), '')")"
  [ "$notification_status" = "SENT" ] && break
  sleep 1
done
echo "Notification status: ${notification_status:-missing}"
[ "$notification_status" = "SENT" ]

echo "== Restarting the application container"
docker compose restart app
sleep 3
wait_for_health

echo "== Verifying the booking survived the restart"
login "alex.rivera@example.com"
status="$(curl -fsS -b "$COOKIES" "$BASE_URL/api/appointments/$appointment_id" | json 'data["status"]')"
echo "Appointment $appointment_id status after restart: $status"
[ "$status" = "BOOKED" ]

echo "Smoke test passed."
