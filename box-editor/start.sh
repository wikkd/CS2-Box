#!/usr/bin/env sh
# CS2-Box box editor - local deployment launcher (macOS / Linux / Git Bash)
# Usage: ./start.sh [--port 4173] [--no-open]
cd "$(dirname "$0")"
if ! command -v node >/dev/null 2>&1; then
  echo "[ERROR] Node.js was not found. Install it from https://nodejs.org/ first."
  exit 1
fi
echo "Starting CS2-Box box editor at http://127.0.0.1:4173/  (Ctrl+C to stop)"
exec node server.mjs "$@"