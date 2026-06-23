#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# run-demo.sh — launch a specific demo by number
# Usage: ./scripts/run-demo.sh <demo-number> [extra gradle args]
#
# Examples:
#   ./scripts/run-demo.sh 1               # Hello World
#   ./scripts/run-demo.sh 7               # AI Sentiment
#   GEMINI_API_KEY=... ./scripts/run-demo.sh 9  # AI ETL
#   ./scripts/run-demo.sh 10              # Ollama (local, no key needed)
#   ./scripts/run-demo.sh 11              # Ollama + RAG (requires: ollama pull nomic-embed-text)
# ────────────────────────────────────────────────────────────────
set -euo pipefail

DEMO=${1:-1}
shift || true

PROFILE="demo$(printf '%02d' "$DEMO")"

echo ""
echo "╔══════════════════════════════════════════╗"
echo "║   Apache Camel + AI Demo — dev.bg        ║"
echo "╠══════════════════════════════════════════╣"
echo "║   Profile: $PROFILE                        "
echo "╚══════════════════════════════════════════╝"
echo ""

./gradlew bootRun \
  -Dspring.profiles.active="$PROFILE" \
  "$@"
