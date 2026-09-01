# Great Sage Voice (Raphael)

Forge 1.20.1 companion mod for Raphael / Gran Sabio: automatic Spanish/English language selection, cinematic HUD, local analytical reactions and self-managed offline speech.

## v1.4.0 — authorized local voice identity

v1.4 adds an optional **local OpenVoice V2 ONNX tone-conversion layer** on top of the existing Piper speech pipeline.

Runtime flow:

```text
Minecraft event
 -> bilingual local Raphael brain
 -> Piper ES/EN base speech
 -> authorized OpenVoice tone transfer (when locally enabled)
 -> bounded WAV
 -> Forge S2C
 -> client voice + Great Sage HUD
```

No persistent Python process, FastAPI backend, localhost server, paid TTS account or API key is required.

### Authorization stays local

The public repository intentionally does **not** contain actor-specific reference URLs or recordings. Authorized installations may contain these local files:

```text
<game-dir>/great_sage_voice/authorized_voice/authorization.accepted
<game-dir>/great_sage_voice/authorized_voice/sources.json
```

When both are present and `enableAuthorizedVoiceClone=true`, the server automatically:

1. prepares Piper for the player's language;
2. downloads the pinned OpenVoice V2 ONNX encoder/converter models;
3. acquires the operator-authorized public reference sources listed in the local manifest;
4. decodes and resamples the references locally;
5. extracts a target tone embedding;
6. caches that compact embedding;
7. transfers the target timbre onto future Raphael speech locally.

The reference sources themselves are not committed to Git. This keeps the public code generic and makes the authorization/source selection installation-specific.

If any cloning stage is unavailable, Raphael automatically falls back to the normal Piper voice instead of breaking gameplay.

## Automatic language

Raphael follows each Minecraft client's selected language:

- any locale starting with `es` -> Spanish response + Spanish base voice + Spanish authorized tone profile;
- other locales -> English response + English base voice + English authorized tone profile.

Language changes are detected while connected. One multiplayer server can speak Spanish to one player and English to another simultaneously.

## Base offline voices

The fallback/base layer remains self-managed Piper:

- Spanish: `es_AR-daniela-high`;
- English: `en_US-lessac-high`.

Only the language actually requested by a player is prepared. These base voices provide stable pronunciation and restrained Great Sage cadence before optional tone transfer.

## Zero-manual runtime design

The distributable v1.4 JAR bundles its Java inference dependencies with Forge Jar-in-Jar:

- Microsoft ONNX Runtime CPU;
- JLayer MP3 decoder.

The local authorized-reference manager can also fetch its pinned `yt-dlp` executable when a configured media source requires it. `yt-dlp` is invoked only for acquisition and exits; it is not a background service.

Downloaded/cached runtime data lives under:

```text
<game-dir>/great_sage_voice/
```

## Local analytical core

Cloud AI remains optional. The built-in core reacts in Spanish/English using real Minecraft telemetry:

- login and language synchronization;
- death / respawn;
- post-damage critical health;
- critical hunger;
- low-air / drowning risk;
- game mode changes;
- dimension transitions;
- advancements;
- optional item tosses;
- health/max health, food, armor and XP;
- dimension and approximate coordinates;
- immediate-risk diagnostics.

Asynchronous results are sequence-checked so a delayed old event cannot overwrite a newer warning.

## Commands

```text
/rafael status
/rafael language
/rafael prepare
/rafael voice
/rafael test realiza un diagnostico completo
/rafael test run a full diagnostic
```

`/rafael status` exposes the base voice state and the authorized tone-conversion state independently.

## Voice character

Piper tuning is deliberately restrained before conversion:

```toml
offlineLengthScale = 1.07
offlineEnglishLengthScale = 1.04
offlineNoiseScale = 0.42
offlineNoiseWidth = 0.48
authorizedVoiceCloneStrength = 0.96
```

The client then applies only conservative presence/aura processing. There is no artificial pitch shift intended to fake identity; identity comes from the authorized target embedding when that mode is active.

## HUD

The HUD remains localized and screen-safe:

Spanish:

```text
RAFAEL // GRAN SABIO
ANÁLISIS / CRÍTICO / SINCRONÍA / HITO
VOZ LOCAL // ES
```

English:

```text
RAPHAEL // GREAT SAGE
ANALYSIS / CRITICAL / SYNC / MILESTONE
LOCAL VOICE // EN
```

It includes dynamic wrapping/lifetime, typewriter reveal, cursor, processing bus, fade/easing, runic core, particles, emotion accents and configurable scale/opacity/audio levels.

## Singleplayer and dedicated servers

Singleplayer runs an integrated Minecraft server and exercises the same pipeline as multiplayer.

For dedicated servers, install the same v1.4 JAR on server and clients. Voice preparation/conversion runs server-side; clients receive only localized text and bounded WAV for their own responses.

## Build

- Minecraft 1.20.1
- Forge 47.2.20
- Java 17 toolchain
- Gradle 8.3
- ONNX Runtime 1.24.3

```powershell
.\gradlew.bat clean build --no-daemon
```

The distributable artifact is the Jar-in-Jar JAR; the `*-slim.jar` build artifact is diagnostic and does not contain the inference libraries.

See `docs/VOICE_SETUP.md`, `THIRD_PARTY_NOTICES.md`, `SECURITY.md` and the changelog for details.
