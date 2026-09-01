# Voice setup

## Runtime model

Great Sage Voice v1.1 does not run a Python backend. The Forge server performs outbound HTTPS requests to the selected provider, receives audio in memory and sends it directly to the target Minecraft client.

There is no `localhost:8000`, FastAPI, FFmpeg service or terminal window to keep open.

## Recommended OpenAI setup

Use:

- TTS provider: `openai`
- model: `gpt-4o-mini-tts`
- built-in voice: `marin` by default
- response format: WAV

The server controls delivery with `voiceInstructions`. The default profile requests a calm, precise, adult feminine, slightly ethereal system voice. Adjust the descriptive traits instead of asking a model to impersonate a real actor.

The API key can be supplied as `OPENAI_API_KEY` by the server host. If environment variables are unavailable, use `openAiApiKey` in `world/serverconfig/great_sage_voice-server.toml` and protect that file.

## Optional ElevenLabs setup

Set `ttsProvider = "elevenlabs"`, provide `ELEVENLABS_API_KEY` (or `elevenLabsApiKey`) and configure a `elevenLabsVoiceId`.

For the Rafael aesthetic, prefer a voice created with Voice Design from descriptive traits. A suitable design direction is:

> Adult feminine synthetic intelligence voice, neutral Spanish, calm and highly controlled, crystalline articulation, low emotional variance, subtle ethereal resonance, precise analytical cadence, protective authority, concise delivery, no commercial narrator energy, no exaggerated anime caricature.

Choose a preview you like and use its resulting Voice ID. Do not upload or clone a dub actor's recordings unless you have the necessary rights and consent.

## Dedicated servers

The server machine/host needs outbound HTTPS access to the selected provider. Clients do not need provider accounts or keys.

If outbound provider access is blocked or credentials are absent, the mod falls back to local text and HUD behavior without crashing.

## Bandwidth

Generated speech is kept compact and transmitted only to the target player. Responses are capped and audio packets are bounded to avoid oversized custom payloads. Repeated identical speech can be served from the in-memory server cache.
