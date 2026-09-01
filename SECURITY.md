# Security policy

## Default local architecture

Great Sage Voice 1.4 does not require provider credentials. The Forge server/integrated server performs analysis, base TTS and optional authorized tone conversion locally.

Security controls include:

- fixed Piper runtime/model locations;
- pinned SHA-256 validation for built-in Piper voice models;
- pinned SHA-256 validation for quantized OpenVoice ONNX models;
- pinned yt-dlp release/checksum for supported automatic media acquisition;
- HTTPS-only authorized source URLs;
- minimum/maximum download-size checks;
- safe archive extraction/path traversal protection;
- bounded language and speech network payloads;
- voice preparation outside the Minecraft main thread;
- maximum OpenVoice conversion duration;
- stale asynchronous response suppression;
- no arbitrary client-side audio downloads.

## Authorization boundary

The public repository does not contain actor-specific reference URLs, reference recordings or extracted actor embeddings.

Authorized tone conversion activates only when the server/integrated-server installation contains both:

```text
great_sage_voice/authorized_voice/authorization.accepted
great_sage_voice/authorized_voice/sources.json
```

The source manifest is installation-local and ignored by Git through the runtime-directory ignore rule. The mod treats its presence as an operator-controlled authorization/configuration boundary; the server owner remains responsible for ensuring the referenced audio and voice use are actually authorized.

References, OpenVoice weights and extracted target embeddings remain local under:

```text
great_sage_voice/authorized_voice/
```

Only generated bounded WAV speech is sent to clients. Source recordings and speaker embeddings are never sent through Forge networking.

## Failure isolation

Authorized tone conversion is optional. A failed reference download, MP3 decode, ONNX model load or inference pass does not stop the server or remove the HUD: the speech pipeline falls back to the normal offline Piper voice for that response.

## Java dependencies

ONNX Runtime and JLayer are bundled using Forge Jar-in-Jar instead of requiring users to install jars manually. The distributable artifact is the bundled Jar-in-Jar output; the `*-slim.jar` artifact is not intended for normal installation.

## Optional cloud providers

OpenAI/ElevenLabs remain optional enhancement providers. If used, keep keys server-side only. Prefer server environment variables; never place credentials in Java source, resources, Git commits, client config, screenshots/logs or chat commands.

The S2C packet contains localized text, emotion/state metadata, language metadata and generated WAV bytes only. Provider credentials are never transmitted to clients.

## Reporting

Report security issues privately to the repository owner before publishing exploit details.
