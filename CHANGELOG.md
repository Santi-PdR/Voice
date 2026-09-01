# Changelog

## 1.3.0 - Bilingual Great Sage identity pass

### Language architecture

- Added client -> server Minecraft language synchronization over bounded Forge networking.
- Spanish Minecraft locales now receive Spanish text + `es_AR-daniela-high` speech.
- English/other locales receive English text + `en_US-lessac-high` speech.
- Language changes are detected while connected.
- Multiplayer supports different Raphael languages per player simultaneously.
- Login narration waits briefly for client language synchronization before falling back.

### Offline voice

- Reworked Piper manager for multiple language profiles sharing one runtime.
- Downloads only the neural model actually needed by connected players.
- Added pinned SHA-256 validation for English Lessac High in addition to Spanish Daniela High.
- Added separate Spanish/English cadence tuning.
- Reduced default inference randomness for a more controlled Great Sage delivery.
- Added automatic v1.2 Spanish-cache migration.
- Added server-side authorized custom-model override support at `great_sage_voice/custom_voice/{es,en}.onnx`.

### Character presentation

- Added conservative client loudness normalization.
- Added configurable speech-presence enhancement.
- Upgraded acoustic aura from one reflection to a restrained dual-reflection system.
- Added click-safe short fades to processed PCM speech.
- Refined activation/typewriter levels.
- HUD title, state labels and voice indicators now localize to Spanish/English.
- Added ES/EN language indicator, processing bus animation, response-lifetime line and typewriter cursor.
- Added configurable HUD opacity.

### Analytical core

- Expanded local telemetry with max health, food, armor, XP level and coordinates.
- Manual diagnostics now answer health, hunger, location and immediate-risk questions in both languages.
- Added low-air / drowning-risk threshold reaction.
- Added stale asynchronous response suppression: a delayed old analysis can no longer overwrite a newer event.
- Preserved post-damage health snapshots for critical warnings.

### Network and operations

- Network protocol upgraded to v3.
- Added bounded C2S language packet and language metadata in S2C speech packet.
- Increased localized voice cache capacity to 64 entries.
- Runtime cache added to `.gitignore`.
- Version bumped to 1.3.0.

## 1.2.0 - Self-managed offline Raphael voice

- Made Piper offline TTS the default.
- Removed normal runtime dependency on API keys/accounts/Python/localhost/terminal windows.
- Added automatic Piper runtime + Spanish high-quality voice installation and checksum validation.
- Added improved local analytical brain, hunger/dimension reactions and client acoustic aura.

## 1.1.0 - Server-native voice architecture

- Removed Python/FastAPI/FFmpeg/localhost runtime backend.
- Voice moved to the Forge server and bounded WAV delivery to clients.
- Added optional cloud providers, cooldowns, improved HUD/audio, GitHub Actions and security documentation.
