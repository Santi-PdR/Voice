# Voice setup — v1.3.0

## Default: no setup required

Raphael uses self-managed offline Piper speech by default. No provider account/API key/Python/FastAPI/localhost service or persistent terminal is required.

## Automatic language selection

The client sends its selected Minecraft language to the Forge server through the mod channel.

- `es_*` locales -> Spanish response text + Spanish voice.
- all other locales -> English response text + English voice.

The client resends the language when it changes, so switching Minecraft language while connected updates Raphael automatically.

Use:

```text
/rafael language
```

to confirm what the server currently sees.

## First use and cache

The Piper executable is shared. Voice models are downloaded on demand:

```text
Spanish: es_AR-daniela-high
English: en_US-lessac-high
```

Each high-quality model is roughly 114 MB. A server with only Spanish players does not need the English model and vice versa.

Cache:

```text
<game-or-server-dir>/great_sage_voice/offline_voice/
```

v1.3 automatically migrates the valid Spanish cache layout from v1.2 when possible.

## Tests

```text
/rafael status
/rafael prepare
/rafael voice
/rafael test realiza un diagnostico completo
/rafael test run a complete diagnostic
```

## Character tuning

Server defaults:

```toml
ttsProvider = "offline"
autoInstallOfflineVoice = true
prewarmOfflineVoice = true
preferCustomVoiceModels = true
offlineLengthScale = 1.08
offlineEnglishLengthScale = 1.05
offlineNoiseScale = 0.44
offlineNoiseWidth = 0.50
```

Client defaults:

```toml
voiceVolume = 1.0
voiceAuraIntensity = 0.11
voicePresence = 0.10
uiSoundVolume = 0.36
```

The goal is restrained, feminine, analytical, precise delivery with a subtle internal/system presence. The default open profiles are not represented as recordings or clones of the franchise's real voice performers.

## Properly authorized custom character voice

The mod supports Piper-compatible custom models without recompilation:

```text
<game-or-server-dir>/great_sage_voice/custom_voice/es.onnx
<game-or-server-dir>/great_sage_voice/custom_voice/es.onnx.json

<game-or-server-dir>/great_sage_voice/custom_voice/en.onnx
<game-or-server-dir>/great_sage_voice/custom_voice/en.onnx.json
```

When `preferCustomVoiceModels=true`, a valid custom pair takes priority for that language. Keep custom models server-side; the server sends only generated WAV to clients.

Only use models you are permitted to use and distribute/operate.

## Dedicated servers

Install the same v1.3 mod on the Forge server and clients. Different connected players can receive different languages simultaneously. The dedicated host performs TTS and clients never need the ONNX models.

Outbound HTTPS is needed only for first-time automatic downloads of the Piper runtime/model files that are not already cached.

## Optional cloud paths

OpenAI and ElevenLabs remain optional compatibility/enhancement paths. If selected but unavailable/unconfigured, the mod automatically falls back to the correct offline language voice.
