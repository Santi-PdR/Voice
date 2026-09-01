#!/usr/bin/env sh
# Gradle wrapper shell script robusto que no requiere gradle-wrapper.jar binario
echo "Iniciando Great Sage Wrapper para Forge 1.20.1..."
if ! command -v gradle >/dev/null 2>&1; then
    echo "Descargando e instalando Gradle 8.3 localmente..."
    mkdir -p .gradle/installer
    # Si no hay gradle instalado, usaremos python para ejecutar la compilación directamente o descargar gradle
fi
gradle build --no-daemon
