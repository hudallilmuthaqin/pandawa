#!/usr/bin/env bash
# Stops everything started by scripts/start-public.sh.
RUN="$(cd "$(dirname "$0")/.." && pwd)/.run"
for name in tunnel frontend backend; do
  if [ -f "$RUN/$name.pid" ]; then
    kill "$(cat "$RUN/$name.pid")" 2>/dev/null && echo "■ $name dihentikan"
    rm -f "$RUN/$name.pid"
  fi
done
pkill -f "vite preview" 2>/dev/null || true
rm -f "$RUN/public-url.txt"
