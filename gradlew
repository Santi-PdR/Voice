#!/usr/bin/env sh
set -eu

GRADLE_VERSION="8.3"
BOOTSTRAP_ROOT="${GRADLE_USER_HOME:-$HOME/.gradle}/rafael-bootstrap"
GRADLE_HOME="$BOOTSTRAP_ROOT/gradle-$GRADLE_VERSION"
GRADLE_BIN="$GRADLE_HOME/bin/gradle"

if [ ! -x "$GRADLE_BIN" ]; then
  echo "[Rafael Build] Gradle $GRADLE_VERSION not found. Downloading official distribution..."
  mkdir -p "$BOOTSTRAP_ROOT"
  ZIP_FILE="${TMPDIR:-/tmp}/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then curl -fL "$URL" -o "$ZIP_FILE"; elif command -v wget >/dev/null 2>&1; then wget -O "$ZIP_FILE" "$URL"; else echo "ERROR: curl or wget is required to bootstrap Gradle." >&2; exit 1; fi
  rm -rf "$GRADLE_HOME"
  if command -v unzip >/dev/null 2>&1; then unzip -q "$ZIP_FILE" -d "$BOOTSTRAP_ROOT"; else echo "ERROR: unzip is required to bootstrap Gradle." >&2; exit 1; fi
  rm -f "$ZIP_FILE"
fi
exec "$GRADLE_BIN" "$@"
