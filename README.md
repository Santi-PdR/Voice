# Great Sage Voice (Rafael)

Forge 1.20.1 mod focused on a server/client Rafael-style analytical companion: cinematic HUD, contextual event reactions and self-managed offline neural speech.

## v1.2.0: voice with no account, API key or terminal

The default voice path is now fully local:

1. Forge starts Rafael on the integrated or dedicated server.
2. The mod prepares Piper TTS asynchronously.
3. On the first installation only, it downloads the correct Piper runtime for the host OS and the open Spanish `daniela-high` ONNX voice.
4. Files are cached under `great_sage_voice/offline_voice/` and reused afterwards.
5. Rafael synthesizes WAV locally on the server.
6. Forge sends the bounded WAV bytes directly to the target client.
7. The client applies a subtle configurable acoustic aura and plays the speech together with the HUD.

There is **no Python, FastAPI, FFmpeg service, localhost server, API key, paid account or terminal window to keep open**.

The first preparation downloads roughly 140 MB (runtime + high-quality Spanish model). After that, normal offline synthesis uses the cached files. Windows x64, Linux x64, Linux ARM64, macOS x64 and macOS ARM64 are handled automatically.

## Voice identity

The built-in profile uses the open Piper `es_AR-daniela-high` voice and conservative inference settings to produce a calm, controlled feminine Spanish system voice. It is intentionally an original Rafael-inspired presentation, not a clone of a dub actor.

Default tuning:

- `offlineLengthScale = 1.10` — calm cadence.
- `offlineNoiseScale = 0.48` — reduced generator randomness.
- `offlineNoiseWidth = 0.55` — restrained phoneme variation.
- client `voiceAuraIntensity = 0.10` — short subtle acoustic reflection without changing pitch or duration.

See `THIRD_PARTY_NOTICES.md` for source/licensing notices.

## Local brain

Rafael no longer becomes useless when no cloud API exists. The server has an integrated deterministic analytical brain for:

- login/synchronization;
- death and respawn;
- critical health;
- critical hunger threshold crossing;
- gamemode changes;
- dimension transitions;
- advancements;
- optional item tosses;
- manual diagnostics about health, current dimension, risk and system state.

Cloud-enhanced analysis remains optional. If configured it can supplement the local brain; if it fails, local behavior continues automatically.

## Commands

```text
/rafael status
/rafael prepare
/rafael voice
/rafael test Hola Rafael, realiza un diagnostico del sistema
```

`/rafael prepare` simply requests asynchronous preparation; it does not open a console or start a persistent service. Normally server startup already prewarms the voice automatically.

## Singleplayer

Singleplayer has an integrated Minecraft server, so it exercises the same architecture as multiplayer. Install the same JAR in the client instance and run `/rafael voice` in a world.

## Dedicated server

Install the same mod version on server and clients. The dedicated server performs local TTS synthesis and sends speech only to the relevant player. Clients never need Piper files, provider accounts or secrets.

The host needs outbound HTTPS only for the **first automatic runtime/model download**. Once cached, voice synthesis itself is local.

## Automatic events and anti-spam

Rafael tracks event cooldowns per player/event. Threshold events fire only when crossing into the critical state, preventing constant repeated speech. Death can bypass the normal cooldown because it is a high-priority event.

Item toss narration remains disabled by default because it is naturally noisy.

## Client presentation

- screen-safe compact holographic panel;
- dynamic wrapping and duration;
- typewriter reveal;
- real fade-in/fade-out and easing;
- emotion/state accents;
- animated runic core and particles;
- independent voice/UI volume;
- configurable voice aura;
- new layered activation signature;
- no duplicated vanilla chat message.

## Build

Java 17 toolchain, Forge 47.2.20 and Gradle 8.3.

```powershell
.\gradlew.bat clean build --no-daemon
```

The project also includes GitHub Actions validation on Java 17.

## Deploy target used for local testing

After build, the normal JAR is under `build/libs/`. For SKLauncher `test-1`, copy the current JAR to:

```text
C:\Users\santi\AppData\Roaming\.sklauncher\instances\test-1\mods
```
