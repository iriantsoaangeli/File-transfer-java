#!/usr/bin/env bash
# =============================================================================
#  compile.sh — Compile le projet FTPApp (sans packager le JAR)
#  Usage : bash compile.sh
# =============================================================================

set -e  # Arrêt immédiat en cas d'erreur

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "============================================="
echo "  FTPApp — Compilation"
echo "============================================="

mvn -B compile

echo ""
echo "✅ Compilation réussie → app/target/classes/"
