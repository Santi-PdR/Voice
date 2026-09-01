# Changelog

## 1.4.0 - Automatic authorized Raphael tone cloning

### Voice identity

- Added local OpenVoice V2 ONNX tone conversion after bilingual Piper synthesis.
- Added operator-local authorization gate: cloning activates only when `great_sage_voice/authorized_voice/authorization.accepted` and `sources.json` are present.
- Actor/source URLs are intentionally not committed to the public repository.
- Added automatic target-tone embedding extraction and disk cache.
- Added configurable `authorizedVoiceCloneStrength` with conservative default.
- Conversion failure degrades to the existing Piper speech instead of breaking HUD/events.
- Base Spanish/English cadence/noise values were tightened before tone conversion.

### Automatic source acquisition

- Added local authorized source manifest support for direct WAV/MP3 references.
- Added pinned automatic yt-dlp acquisition for media references on Windows x64/Linux x64.
- yt-dlp is invoked only during reference acquisition and exits; no persistent service is created.
- Added JLayer MP3 decoding in-process; no FFmpeg service or manual conversion is required.
- Added reference resampling, mono conversion, segmentation and multi-sample embedding averaging.

### Local inference

- Added quantized OpenVoice reference encoder + tone converter model bootstrap with pinned SHA-256 checks.
- Added Java implementation of OpenVoice magnitude-spectrogram preprocessing (22.05 kHz, Hann STFT, FFT 1024, hop 256, reflect padding).
- Added ONNX Runtime CPU inference and bounded postprocessing.
- Added source/target embedding caches to reduce repeated inference cost.
- Capped conversion input length and download sizes for predictable resource use.

### Packaging and operations

- Bundled ONNX Runtime 1.24.3 and JLayer 1.0.1 with Forge Jar-in-Jar.
- `jar` now produces a `-slim` diagnostic artifact while `jarJar` produces the distributable JAR.
- CI uploads only the distributable bundled artifact.
- `/rafael status` now reports base TTS and authorized tone-transfer state separately.
- Version bumped to 1.4.0.

## 1.3.0 - Bilingual Great Sage identity pass

### Language architecture

- Added client -> server Minecraft language synchronization over bounded Forge networking.
- Spanish Minecraft locales receive Spanish text + `es_AR-daniela-high` speech.
- English/other locales receive English text + `en_US-lessac-high` speech.
- Language changes are detected while connected.
- Multiplayer supports different Raphael languages per player simultaneously.
- Login narration waits briefly for client language synchronization before falling back.

### Offline voice and presentation

- Reworked Piper manager for multiple language profiles sharing one runtime.
- Added pinned voice SHA-256 validation, separate cadence tuning and v1.2 cache migration.
- Added optional authorized custom Piper model override support.
- Added client normalization/presence, dual acoustic aura, localized HUD states, ES/EN indicator, processing bus and HUD opacity.

### Analytical core/network

- Expanded telemetry with max health, food, armor, XP, coordinates and low-air risk.
- Added stale asynchronous response suppression.
- Network protocol upgraded to v3 with bounded C2S language sync and language metadata in S2C speech.

## 1.2.0 - Self-managed offline Raphael voice

- Made Piper offline TTS the default.
- Removed normal runtime dependency on API keys/accounts/Python/localhost/terminal windows.
- Added automatic Piper runtime + Spanish high-quality voice installation and checksum validation.
- Added improved local analytical brain, hunger/dimension reactions and client acoustic aura.

## 1.1.0 - Server-native voice architecture

- Removed Python/FastAPI/FFmpeg/localhost runtime backend.
- Voice moved to the Forge server and bounded WAV delivery to clients.
- Added optional cloud providers, cooldowns, improved HUD/audio, GitHub Actions and security documentation.
