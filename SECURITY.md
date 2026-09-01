# Security policy

## API keys

Provider keys are secrets. Keep them on the Minecraft server only.

Preferred locations:

- `OPENAI_API_KEY` server environment variable
- `ELEVENLABS_API_KEY` server environment variable
- server-only `world/serverconfig/great_sage_voice-server.toml` when environment variables are unavailable

Never place keys in:

- Java source
- `src/main/resources`
- Git commits
- client config
- screenshots/logs shared publicly
- Minecraft chat commands

The Rafael S2C packet contains response text, emotion metadata and generated audio bytes only. Provider credentials are not sent to clients.

## Networking

Provider calls are restricted to HTTPS endpoints implemented by the mod. Audio URLs supplied by arbitrary remote responses are no longer downloaded by clients.

## Reporting

Report security issues privately to the repository owner before publishing exploit details.
