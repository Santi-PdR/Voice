# 🌟 Great Sage Voice (Rafael) - Minecraft 1.20.1 Forge Mod (Ultimate Cinematic Edition)

¡El mod definitivo de **Minecraft 1.20.1 para Forge** que integra la icónica voz, la interfaz gráfica cinemática arcano-tecnológica y el sistema analítico de **Rafael** (El Gran Sabio / Raphaël) de **Tensei Shitara Slime Datta Ken** (*That Time I Got Reincarnated as a Slime*)!

---

## 💎 Características Principales & Mejoras Profesionales

1. **HUD Cinemático Holográfico (Interfaz del Gran Sabio):**
   - **Núcleo Luminoso Reactivo:** Emite pulsaciones dinámicas de luz blanca y dorada que reaccionan al procesamiento y la escritura.
   - **Doble Anillo Rúnico Concéntrico Contrarrotatorio:** Dos estructuras poligonales girando en direcciones opuestas que simulan el cálculo cuántico y mágico.
   - **Efecto Máquina de Escribir (*Typewriter*):** El texto aparece letra por letra de manera fluida, acompañado de sutiles clics de audio cibernético (`Note Block Pling`).
   - **Partículas Cuánticas Orbitales:** Partículas brillantes de energía dorada y cian que orbitan en torno al núcleo rúnico.
   - **Líneas de Escaneo (*Scanlines*) y Telemetría:** Paneles laterales con indicadores de diagnóstico (`SYS:ACTIVE`, `ID: 0x9AF4`, `CORE 100%`, `STABLE`, `SECURE`).

2. **Comandos de Prueba y Sistema (`/rafael`):**
   - `/rafael test <mensaje>` -> Permite probar manualmente la evaluación y respuesta analítica de Rafael en cualquier momento.
   - `/rafael status` -> Muestra el estado operativo del sistema y la conexión con el Gran Sabio.

3. **Eventos Monitoreados en Tiempo Real:**
   - Conexión al servidor y bienvenida.
   - Muertes (con análisis de la causa exacta).
   - Cambios de modo de juego (`/gamemode survival`, creative, etc.).
   - Reaparición / Respawn (*Clone*).
   - Salud crítica (advertencia cuando el jugador queda con 2 corazones o menos).
   - Objetos droppeados y logros / avances completados.

---

## 📂 Estructura del Proyecto

```tree
Voice/
├── settings.gradle                 # Configuración raíz de Gradle con PluginManagement
├── build.gradle                    # Dependencias y compilación Forge 1.20.1
├── gradle.properties               # Versiones y JVM args optimizados (-Xmx3G)
├── src/
│   └── main/
│       ├── java/
│       │   └── com/
│       │       └── rafael/
│       │           ├── GreatSageMod.java          # Entrada principal Forge
│       │           ├── command/
│       │           │   └── RafaelCommand.java     # Comandos de chat (/rafael test / status)
│       │           ├── config/
│       │           │   ├── GreatSageConfig.java       # Config Servidor (Owner)
│       │           │   └── GreatSageClientConfig.java # Config Cliente (HUD/Audio)
│       │           ├── client/
│       │           │   ├── GreatSageClient.java       # Cliente y registro de HUD
│       │           │   ├── GreatSageHudOverlay.java   # HUD cinemático con Typewriter & Runic Rings
│       │           │   └── GreatSageAudioPlayer.java  # Reproductor de voz y efectos de tecleo
│       │           ├── network/
│       │           │   └── PacketHandler.java     # Red S2C optimizada (PacketDistributor)
│       │           └── server/
│       │               └── AIEventManager.java    # Gestor de eventos avanzados
```

---

## 🚀 Compilación y Deploy Automático

Ejecuta el script de PowerShell en tu terminal para compilar el mod y desplegarlo automáticamente en tu entorno y en tu carpeta de SKLauncher (`C:\Users\santi\AppData\Roaming\.sklauncher\instances\test-1\mods`):

```powershell
$ErrorActionPreference = "Stop"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deploy Ultimate Edition (Rafael Mod)   " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# 1. Actualizar repositorio
if (Test-Path "D:\MC\Voice\.git") {
    Set-Location "D:\MC\Voice"
    git fetch origin
    git reset --hard origin/arena/01a05a43-voice
    git pull origin arena/01a05a43-voice
} else {
    if (Test-Path "D:\MC\Voice") { Remove-Item -Recurse -Force "D:\MC\Voice" }
    git clone -b arena/01a05a43-voice https://github.com/Santi-PdR/Voice.git "D:\MC\Voice"
    Set-Location "D:\MC\Voice"
}

# 2. Configurar memoria JVM y limpiar cachés
$env:GRADLE_OPTS="-Xmx3G -Dfile.encoding=UTF-8"
if (Test-Path ".gradle") { Remove-Item -Recurse -Force ".gradle" }
if (Test-Path "build") { Remove-Item -Recurse -Force "build" }

# 3. Compilar
& ./gradlew.bat build --no-daemon
if ($LASTEXITCODE -ne 0) {
    Write-Host "❌ Error durante la compilación." -ForegroundColor Red
    Read-Host "Presiona Enter para salir..."
    exit 1
}

# 4. Deploy a SKLauncher y D:\MC
$Jar = Get-ChildItem "build/libs/*.jar" | Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-deobf.jar" } | Select-Object -First 1
$DestSK = "C:\Users\santi\AppData\Roaming\.sklauncher\instances\test-1\mods"
if (-not (Test-Path $DestSK)) { New-Item -ItemType Directory -Force -Path $DestSK | Out-Null }

Copy-Item $Jar.FullName -Destination "D:\MC" -Force
Copy-Item $Jar.FullName -Destination $DestSK -Force

Write-Host "🎉 ¡Build y Deploy completados con éxito!" -ForegroundColor Green
Read-Host "Presiona Enter para cerrar..."
```
