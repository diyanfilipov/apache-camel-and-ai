#!/usr/bin/env bash
# ────────────────────────────────────────────────────────────────
# setup.sh — first-time project setup
# ────────────────────────────────────────────────────────────────
set -euo pipefail

echo "=== Apache Camel + AI Demo — Setup ==="

# 1. Generate Gradle wrapper
gradle wrapper --gradle-version=9.2.1

# 2. Create required input/output directories
mkdir -p \
  input/files \
  input/sentiment \
  input/documents \
  output/processed \
  output/sftp-received \
  output/sentiment/positive \
  output/sentiment/negative \
  output/sentiment/neutral \
  output/summaries/errors \
  output/answers

echo "✓ Directories ready"

# 3. Copy sample files from resources
cp src/main/resources/input/sentiment/*.txt   input/sentiment/ 2>/dev/null || true
cp src/main/resources/input/documents/*.txt   input/documents/ 2>/dev/null || true
echo "✓ Sample files copied"

# 4. Check for GEMINI_API_KEY
if [[ -z "${GEMINI_API_KEY:-}" ]]; then
  echo ""
  echo "⚠️  GEMINI_API_KEY is not set."
  echo "   Demos 07-09 require a valid key. Export it before running:"
  echo "   export GEMINI_API_KEY=<your-google-ai-studio-key>"
fi

echo ""
echo "Setup complete. Run demos with:"
echo "  ./scripts/run-demo.sh <1-10>"
echo ""
echo "Profile map:"
echo "  1 → Hello World          4 → Aggregator (EIP)"
echo "  2 → File Processor       5 → SFTP Poller"
echo "  3 → Content-Based Router 6 → Database"
echo "                           7 → AI Sentiment (Gemini)"
echo "                           8 → AI Summariser (Gemini)"
echo "                           9 → AI ETL Pipeline (Gemini)"
echo "                          10 → Local LLM via Ollama"
echo "  groovy → Groovy DSL bonus route"
