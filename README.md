# Great Sage Voice (Raphael)

Forge 1.20.1 companion mod inspired by the analytical presence of Raphael / Gran Sabio: compact cinematic HUD, local event analysis and self-managed offline neural speech for singleplayer and dedicated servers.

## v1.3.0 — automatic Spanish / English Raphael

Raphael now follows the **language selected by each Minecraft client**.

- Any Minecraft locale beginning with `es` -> Spanish text + Spanish neural voice.
- All other locales -> English text + English neural voice.
- Language changes are detected while connected; no reconnect or config edit is required.
- A multiplayer server may speak Spanish to one player and English to another at the same time.

The client sends only a tiny bounded language code to the Forge server. The server owns analysis and speech synthesis and sends back localized text + bounded WAV audio.

## Zero-account voice architecture

Default operation still requires **no API key, paid account, Python, FastAPI, FFmpeg service, localhost server or persistent terminal**.

The first time a language is needed, the server downloads and caches:

- the pinned Piper runtime for the host OS;
- Spanish: `es_AR-daniela-high` (high quality, 22.05 kHz);
- English: `en_US-lessac-high` (high quality, 22.05 kHz).

Only languages actually used by connected players are downloaded. The Piper runtime is shared by both profiles. Model SHA-256 values are pinned before a downloaded ONNX model is accepted.

Cache location:

```text
<game-or-server-dir>/great_sage_voice/offline_voice/
```

After installation, normal synthesis is local and can continue without a TTS provider.

## Voice identity: closer to the Great Sage without pretending

The default profiles are **original/open neural voices tuned to evoke Raphael's character**, not copies of the real dub performers. v1.3 improves the character impression with:

- separate Spanish and English cadence tuning;
- lower synthesis randomness for controlled delivery;
- shorter sentence silences;
- conservative loudness normalization;
- subtle speech-presence enhancement;
- dual short acoustic reflections for the internal/system-like aura;
- a quieter layered activation signature;
- no pitch shift or artificial slowdown after synthesis.

### Authorized custom Raphael voice models

If you have a properly licensed/authorized Piper-compatible voice model, the mod can use it automatically without code changes.

Place server-side files here:

```text
great_sage_voice/custom_voice/es.onnx
great_sage_voice/custom_voice/es.onnx.json

great_sage_voice/custom_voice/en.onnx
great_sage_voice/custom_voice/en.onnx.json
```

With `preferCustomVoiceModels=true`, those files take priority over built-in Daniela/Lessac profiles. This is the supported path for an actual licensed character voice model.

## Local analytical core

Cloud AI is optional. Without it, Raphael can still react and answer in Spanish or English using real Minecraft telemetry:

- login synchronization;
- death and respawn;
- post-damage critical health;
- hunger threshold crossing;
- low-air / drowning risk;
- game mode changes;
- dimension transitions;
- advancements;
- optional item tosses;
- health/max health;
- hunger;
- armor;
- XP level;
- dimension;
- approximate coordinates;
- basic immediate-risk diagnosis.

Responses created asynchronously are sequence-checked. If a newer event arrives first, an older delayed response is discarded instead of overwriting the current warning.

## Commands

```text
/rafael status
/rafael language
/rafael prepare
/rafael voice
/rafael test realiza un diagnostico completo
/rafael test run a full diagnostic
```

Command feedback follows the player's detected language.

## HUD v1.3

- `RAFAEL // GRAN SABIO` in Spanish;
- `RAPHAEL // GREAT SAGE` in English;
- localized ANALYSIS / CRITICAL / SYNC / MILESTONE states;
- visible ES / EN language link;
- `VOZ LOCAL // ES` or `LOCAL VOICE // EN` indicator;
- compact screen-safe layout;
- dynamic wrapping and lifetime;
- typewriter reveal and cursor;
- processing bus animation;
- real fade-in/fade-out;
- runic core, particles and emotion accents;
- configurable opacity, scale and line limit.

## Audio client controls

`great_sage_voice-client.toml` includes:

```toml
voiceVolume = 1.0
voiceAuraIntensity = 0.11
voicePresence = 0.10
uiSoundVolume = 0.36
hudOpacity = 0.94
```

Keep aura/presence conservative; extreme values intentionally remain capped to avoid metallic distortion.

## Server / singleplayer

Singleplayer uses Minecraft's integrated server and therefore exercises the same language/voice architecture as multiplayer.

Dedicated server: install the same v1.3 JAR on server and clients. The server downloads models and synthesizes speech; clients receive only their own localized text/audio packets.

## Build

- Minecraft 1.20.1
- Forge 47.2.20
- Java 17 toolchain
- Gradle 8.3

```powershell
.\gradlew.bat clean build --no-daemon
```

GitHub Actions validates the same Java 17 / Gradle 8.3 build path.

## Local deploy target used for testing

```text
C:\Users\santi\AppData\Roaming\.sklauncher\instances\test-1\mods
```

See `docs/VOICE_SETUP.md`, `THIRD_PARTY_NOTICES.md` and `SECURITY.md` for details.
