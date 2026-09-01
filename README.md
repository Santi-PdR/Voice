# 🌟 Great Sage Voice (Rafael) - Minecraft 1.20.1 Forge Mod

Mod Forge 1.20.1 con HUD cinemático, análisis de eventos y backend de síntesis de voz para Rafael / Gran Sabio.

## Características principales

- HUD holográfico con núcleo luminoso, anillos contrarrotatorios, partículas y efecto typewriter.
- `/rafael test <mensaje>` para probar la cadena completa servidor → backend → cliente → voz.
- `/rafael status` para comprobar el HUD y la red S2C sin depender del backend.
- Eventos automáticos: conexión, muerte, respawn, salud crítica, cambio de gamemode, items descartados y avances.
- Backend FastAPI con caché de voces WAV.
- Audio cliente mediante Java Sound con diagnóstico detallado en `latest.log`.

## Cadena real de voz

1. `AIEventManager` envía el evento a `POST /rafael/evaluate`.
2. FastAPI genera el texto y sintetiza la voz con gTTS.
3. `imageio-ffmpeg` convierte el MP3 a WAV PCM signed 16-bit, mono, 24 kHz. No hace falta instalar FFmpeg manualmente.
4. El backend devuelve `text`, `emotion`, `audio_url`, `audio_status` y, si falla la voz, `audio_error`.
5. El servidor Minecraft parsea esa respuesta y envía los datos mediante `RafaelSpeechPacket` al cliente correcto.
6. `GreatSageAudioPlayer` descarga el WAV, valida HTTP/formato, mantiene el `Clip` activo hasta terminar y registra cualquier error real.

## Backend de voz

Desde `D:\MC\Voice`:

```powershell
cd D:\MC\Voice\ai_backend
python -m pip install -r requirements.txt
python main.py
```

El backend escucha por defecto en `http://0.0.0.0:8000`.

Prueba de salud:

```powershell
Invoke-RestMethod http://localhost:8000/health
```

Debe devolver `status = ok`.

Configuración Minecraft (`config/great_sage_voice-server.toml` o `world/serverconfig/great_sage_voice-server.toml`):

```toml
aiEndpointUrl = "http://localhost:8000/rafael/evaluate"
enableAI = true
```

Después, dentro del juego:

```text
/rafael test Hola Rafael, haz un diagnóstico del sistema
```

En la consola de Python debería aparecer `[Rafael] Voz lista: ...wav -> ...` y en `latest.log` debería aparecer `Reproduciendo voz de Rafael` con URL, formato y duración aproximada.

### Servidor dedicado / jugadores remotos

Si Minecraft Server llama al backend mediante `localhost`, esa dirección no sirve como URL de audio para los clientes remotos. Define una URL pública o LAN accesible por los jugadores antes de iniciar Python:

```powershell
$env:RAFAEL_PUBLIC_BASE_URL = "http://IP_O_DOMINIO_DEL_BACKEND:8000"
python main.py
```

El backend usará esa dirección al construir `audio_url`.

## Diagnóstico rápido

- HUD aparece pero solo suena el efecto mágico: revisa `latest.log`; ahora indica si `audio_url` llegó vacío o si falló la descarga/reproducción.
- `audio_status=unavailable`: revisa la consola de Python; `audio_error` identifica el fallo de síntesis/conversión.
- HTTP 404/500 al bajar el WAV: comprueba `/health`, la carpeta `ai_backend/generated_audio/` y `RAFAEL_PUBLIC_BASE_URL` si usas servidor dedicado.
- El backend funciona pero Minecraft cae al fallback: revisa `aiEndpointUrl` y el firewall.

## Estructura del proyecto

```text
Voice/
├── ai_backend/
│   ├── main.py
│   ├── requirements.txt
│   └── generated_audio/          # generado en runtime
├── src/main/java/com/rafael/
│   ├── GreatSageMod.java
│   ├── command/RafaelCommand.java
│   ├── config/
│   │   ├── GreatSageConfig.java
│   │   └── GreatSageClientConfig.java
│   ├── client/
│   │   ├── GreatSageClient.java
│   │   ├── GreatSageHudOverlay.java
│   │   └── GreatSageAudioPlayer.java
│   ├── network/PacketHandler.java
│   └── server/AIEventManager.java
├── build.gradle
├── gradle.properties
└── settings.gradle
```

## Compilación y deploy a `test-1`

```powershell
$ErrorActionPreference = "Stop"
Write-Host "========================================" -ForegroundColor Cyan
Write-Host " Deploy Great Sage Voice / Rafael       " -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

if (Test-Path "D:\MC\Voice\.git") {
    Set-Location "D:\MC\Voice"
    git fetch origin
    git reset --hard origin/arena/01a05a43-voice
} else {
    if (Test-Path "D:\MC\Voice") { Remove-Item -Recurse -Force "D:\MC\Voice" }
    git clone -b arena/01a05a43-voice https://github.com/Santi-PdR/Voice.git "D:\MC\Voice"
    Set-Location "D:\MC\Voice"
}

$env:GRADLE_OPTS = "-Xmx3G -Dfile.encoding=UTF-8"
if (Test-Path ".gradle") { Remove-Item -Recurse -Force ".gradle" }
if (Test-Path "build") { Remove-Item -Recurse -Force "build" }

& ./gradlew.bat build --no-daemon
if ($LASTEXITCODE -ne 0) { throw "Gradle build falló." }

$Jar = Get-ChildItem "build/libs/*.jar" |
    Where-Object { $_.Name -notlike "*-sources.jar" -and $_.Name -notlike "*-deobf.jar" } |
    Select-Object -First 1

if (-not $Jar) { throw "No se encontró el JAR compilado." }

$DestSK = "C:\Users\santi\AppData\Roaming\.sklauncher\instances\test-1\mods"
New-Item -ItemType Directory -Force -Path $DestSK | Out-Null
Copy-Item $Jar.FullName -Destination "D:\MC" -Force
Copy-Item $Jar.FullName -Destination $DestSK -Force

Write-Host "Build y deploy completados: $($Jar.Name)" -ForegroundColor Green
```
