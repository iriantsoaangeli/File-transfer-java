#!/usr/bin/env bash
# =============================================================================
#  package-jar.sh — Compile + package le fat JAR + copie dans app-jar/
#  Usage : bash package-jar.sh
#
#  Résultat :
#    app-jar/ftpapp-1.0.0.jar   ← JAR exécutable standalone
#    lib/                        ← dépendances JavaFX
#    app/target/                 ← artefacts de build Maven
# =============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

JAR_NAME="ftpapp-1.0.0.jar"
JAR_PATH="app-jar/$JAR_NAME"

echo "============================================="
echo "  FTPApp — Packaging JAR"
echo "============================================="

# Build complet (compile + shade + copie deps + copie jar)
mvn -B package -DskipTests

echo ""
if [ -f "$JAR_PATH" ]; then
    SIZE=$(du -sh "$JAR_PATH" | cut -f1)
    echo "✅ JAR généré : $JAR_PATH  ($SIZE)"
    echo ""
    echo "▶  Pour lancer l'application :"
    echo "   bash run.sh"
    echo "   ou"
    echo "   java --module-path lib --add-modules javafx.controls,javafx.fxml -jar $JAR_PATH"
else
    echo "❌ Erreur : JAR introuvable dans app-jar/"
    exit 1
fi
