#!/usr/bin/env bash
# Exposes PB Pandawa publicly through a Cloudflare quick tunnel (https://*.trycloudflare.com).
# Runs on this machine: backend (prod profile) + production frontend build + tunnel.
# Requires: Java 21, Node, cloudflared, PostgreSQL with database "pandawa", and ./.env (see .env.example).
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RUN="$ROOT/.run"
mkdir -p "$RUN"
cd "$ROOT"

[ -f .env ] || { echo "File .env tidak ada. Salin .env.example ke .env lalu isi DB_* dan JWT_SECRET."; exit 1; }
set -a; source .env; set +a
: "${DB_URL:?}" "${DB_USERNAME:?}" "${DB_PASSWORD:?}" "${JWT_SECRET:?}"

echo "▶ Build backend…"
(cd backend && ./mvnw -q -B package -DskipTests)
echo "▶ Build frontend…"
(cd frontend && npm run build --silent >/dev/null)

echo "▶ Start backend (prod)…"
SPRING_PROFILES_ACTIVE=prod nohup java -jar backend/target/pandawa-backend-1.0.0.jar >"$RUN/backend.log" 2>&1 &
echo $! >"$RUN/backend.pid"
for _ in $(seq 1 90); do curl -fs localhost:8080/actuator/health >/dev/null 2>&1 && break; sleep 2; done
curl -fs localhost:8080/actuator/health >/dev/null || { echo "Backend gagal start, lihat $RUN/backend.log"; exit 1; }

echo "▶ Start frontend…"
(cd frontend && nohup npx vite preview >"$RUN/frontend.log" 2>&1 & echo $! >"$RUN/frontend.pid")
sleep 3

echo "▶ Start Cloudflare tunnel…"
nohup cloudflared tunnel --no-autoupdate --protocol http2 --url http://127.0.0.1:4173 >"$RUN/tunnel.log" 2>&1 &
echo $! >"$RUN/tunnel.pid"
URL=""
for _ in $(seq 1 30); do
  # The public URL is the generated subdomain, never api.trycloudflare.com (which appears in error lines).
  URL=$(grep -oE 'https://[a-z0-9-]+\.trycloudflare\.com' "$RUN/tunnel.log" | grep -v '^https://api\.' | head -1 || true)
  [ -n "$URL" ] && break; sleep 1
done
if [ -z "$URL" ]; then
  echo "Tunnel gagal:"; grep -E "ERR|failed" "$RUN/tunnel.log" | tail -3
  echo "Jika ada 'certificate is not trusted', jaringan ini memblokir tunnel (mis. firewall kantor). Coba jaringan lain / hotspot HP."
  exit 1
fi
echo "$URL" >"$RUN/public-url.txt"
echo
echo "✅ PB Pandawa publik di: $URL"
echo "   Log: $RUN/  ·  Hentikan: scripts/stop-public.sh"
