#!/usr/bin/env bash
# ==============================================================================
# Sonic Share — Linux Mint Launcher
# ==============================================================================

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$SCRIPT_DIR"

echo "🎵 Starting Sonic Share on Linux Mint..."

# Check if Java is installed
if ! command -v java >/dev/null 2>&1; then
    echo "❌ Error: Java is not installed."
    echo "👉 Please install OpenJDK 17 on Linux Mint with:"
    echo "   sudo apt update && sudo apt install -y openjdk-17-jre"
    exit 1
fi

# Verify Java version (must be >= 17)
JAVA_VER=$(java -version 2>&1 | head -n 1 | awk -F '"' '{print $2}' | awk -F '.' '{print $1}')
if [ -n "$JAVA_VER" ] && [ "$JAVA_VER" -lt 17 ] 2>/dev/null; then
    echo "⚠️ Warning: Detected Java version $JAVA_VER. Sonic Share requires Java 17 or higher."
    echo "👉 You can install Java 17 with:"
    echo "   sudo apt install -y openjdk-17-jre"
fi

JAR_FILE="$SCRIPT_DIR/build/libs/sonic-share-desktop.jar"

# Build JAR if missing
if [ ! -f "$JAR_FILE" ]; then
    echo "📦 Building Sonic Share standalone JAR..."
    if command -v gradle >/dev/null 2>&1; then
        gradle jar
    elif [ -f "$SCRIPT_DIR/gradlew" ]; then
        "$SCRIPT_DIR/gradlew" jar
    elif [ -f "$SCRIPT_DIR/../gradlew" ]; then
        "$SCRIPT_DIR/../gradlew" :desktop:jar
    else
        echo "❌ Error: Could not locate gradle to build JAR."
        exit 1
    fi
fi

echo "🚀 Launching Sonic Share Desktop UI..."
java -jar "$JAR_FILE" "$@"
