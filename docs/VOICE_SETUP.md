# Voice setup — v1.2.0

## Default: nothing to configure

Great Sage Voice now uses self-managed offline Piper speech by default.

You do **not** need:

- an OpenAI key;
- an ElevenLabs key;
- Python;
- FastAPI;
- FFmpeg running as a service;
- localhost ports;
- a second PowerShell/CMD window;
- an account with a TTS provider.

## First run

When a Minecraft integrated/dedicated server starts, Rafael begins voice preparation asynchronously if `prewarmOfflineVoice=true`.

If the runtime/model are not cached yet the mod downloads:

- the matching Piper runtime for Windows/Linux/macOS;
- the `es_AR-daniela-high` ONNX voice and its config.

The model file is checked against a pinned SHA-256 before use. Downloads use fixed HTTPS upstream locations and safe archive extraction.

Runtime cache:

```text
<game-or-server-dir>/great_sage_voice/offline_voice/
```

Approximate first-time download: 140 MB. It is not repeated while valid cached files remain.

## Test

```text
/rafael status
/rafael voice
```

During the first preparation `/rafael status` may report `preparando` or `descargando`. When ready it reports `Piper local + Daniela high`.

Then test contextual local analysis:

```text
/rafael test realiza un diagnostico del sistema
/rafael test cual es mi salud
/rafael test en que dimension estoy
/rafael test hay peligro
```

## Voice character tuning

Server config values:

```toml
ttsProvider = "offline"
autoInstallOfflineVoice = true
prewarmOfflineVoice = true
offlineLengthScale = 1.10
offlineNoiseScale = 0.48
offlineNoiseWidth = 0.55
```

Client config:

```toml
voiceVolume = 1.0
voiceAuraIntensity = 0.10
```

Higher `offlineLengthScale` = slower speech. Lower noise values = more controlled/systematic delivery.

## Optional cloud providers

`openai` and `elevenlabs` remain optional compatibility/enhancement paths. They are never required. If selected but unconfigured or unavailable, voice automatically falls back to Piper offline.

## Dedicated hosts

Clients never download the voice model because the Forge server synthesizes the WAV and sends only the resulting bounded audio packet to the player who should hear it.

The host needs outbound HTTPS for initial automatic installation. Once cached, synthesis is local and does not depend on a provider being online.
