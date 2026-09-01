# Voice setup — v1.4.0

## Normal operation

Raphael is self-managed. No provider account/API key/Python/FastAPI/localhost service or persistent terminal is required.

The server selects Spanish or English from each Minecraft client's locale and prepares only the required base voice.

## Authorized tone-conversion mode

v1.4 can locally transfer an authorized target voice identity with OpenVoice V2 ONNX.

The public repository deliberately does not contain actor-specific sources. An authorized installation is enabled by two operator-local files:

```text
<game-dir>/great_sage_voice/authorized_voice/authorization.accepted
<game-dir>/great_sage_voice/authorized_voice/sources.json
```

The deploy/bootstrap command for an authorized installation can create those files automatically. After that, Minecraft handles the remaining preparation by itself.

When a language is first needed the server automatically:

1. prepares/downloads Piper and the corresponding ES/EN base voice;
2. downloads the pinned OpenVoice quantized encoder/converter;
3. acquires the authorized references listed in the local manifest;
4. downloads pinned yt-dlp only when a configured media source requires it;
5. decodes WAV/MP3 in-process;
6. resamples references to the OpenVoice 22.05 kHz pipeline;
7. extracts/averages the target speaker embedding;
8. stores the embedding cache locally;
9. converts future Raphael speech locally.

Nothing needs to remain open after the game starts.

## Runtime folders

```text
<game-dir>/great_sage_voice/offline_voice/       # Piper runtime/base models
<game-dir>/great_sage_voice/authorized_voice/    # OpenVoice, references, embeddings, acquisition tools
```

These runtime folders are ignored by Git.

## Language

```text
es_* -> Spanish text + Daniela base + authorized ES target tone
everything else -> English text + Lessac base + authorized EN target tone
```

Use `/rafael language` after changing Minecraft's language to verify synchronization.

## Tests

```text
/rafael status
/rafael prepare
/rafael voice
/rafael test realiza un diagnostico completo
/rafael test run a complete diagnostic
```

`/rafael status` reports both the base voice and authorized tone state. During first preparation states may show `pending`, `preparing` or `downloading`; once the target embedding is cached it should report the authorized profile as ready.

## Server defaults

```toml
ttsProvider = "offline"
autoInstallOfflineVoice = true
prewarmOfflineVoice = true
preferCustomVoiceModels = true
enableAuthorizedVoiceClone = true
authorizedVoiceCloneStrength = 0.96
offlineLengthScale = 1.07
offlineEnglishLengthScale = 1.04
offlineNoiseScale = 0.42
offlineNoiseWidth = 0.48
```

The clone-strength default aims for strong identity transfer while leaving some source stability. If tone conversion fails, the same response is still delivered with the Piper base voice.

## Client processing

The client keeps its existing conservative voice aura/presence layer. Those effects are deliberately mild; target identity is created server-side by OpenVoice rather than by pitch-shifting the final WAV.

## Existing custom Piper models

The v1.3 custom-model path is still supported:

```text
great_sage_voice/custom_voice/es.onnx
great_sage_voice/custom_voice/es.onnx.json
great_sage_voice/custom_voice/en.onnx
great_sage_voice/custom_voice/en.onnx.json
```

A valid custom Piper model becomes the base voice. Authorized OpenVoice conversion may then operate on top of that base when enabled.

## Dedicated server

Install the same v1.4 bundled JAR on server and clients. The server performs base synthesis/tone conversion and sends only bounded localized WAV speech to each target player. Clients do not need actor references or target embeddings.
