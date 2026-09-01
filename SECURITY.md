# Security policy

## Default offline mode

Great Sage Voice 1.2.0 does not require provider credentials. The default voice runtime is downloaded from fixed HTTPS upstream locations and cached locally by the Minecraft server/integrated server.

Security controls include:

- fixed Piper release URLs;
- fixed Piper voice-model URL;
- pinned SHA-256 validation for the large ONNX voice model;
- minimum download-size checks;
- archive path-traversal protection;
- bounded speech payloads sent to clients;
- synthesis outside the Minecraft main thread;
- no client-side arbitrary URL downloads.

Downloaded runtime/model files live under `great_sage_voice/offline_voice/` in the game/server directory.

## Optional API keys

OpenAI and ElevenLabs remain optional enhancement providers. If the server owner chooses to use them, keys are secrets and remain server-side only.

Preferred locations:

- `OPENAI_API_KEY` server environment variable
- `ELEVENLABS_API_KEY` server environment variable
- server-only `world/serverconfig/great_sage_voice-server.toml` when environment variables are unavailable

Never place keys in Java source, resources, Git commits, client config, public screenshots/logs or Minecraft chat commands.

The Rafael S2C packet contains response text, emotion metadata and bounded generated WAV bytes only. Provider credentials are never transmitted to clients.

## Networking

Cloud provider calls, when enabled, are restricted to HTTPS endpoints implemented by the mod. Offline installation also uses fixed HTTPS upstream URLs. Clients do not fetch audio from arbitrary URLs.

## Reporting

Report security issues privately to the repository owner before publishing exploit details.
