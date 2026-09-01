# Security policy

## Default offline mode

Great Sage Voice 1.3 does not require provider credentials. The Forge server/integrated server downloads pinned offline runtime/model assets from fixed HTTPS upstream locations and caches them locally.

Security controls include:

- fixed Piper release/model locations;
- pinned SHA-256 validation for built-in ONNX models;
- minimum download-size checks;
- archive path-traversal protection;
- bounded audio packets;
- bounded client-language packets;
- synthesis and downloads outside the Minecraft main thread;
- stale asynchronous response suppression;
- no arbitrary client-side audio URL downloads.

Downloaded runtime/models live under:

```text
great_sage_voice/offline_voice/
```

## Language synchronization

Clients send only their Minecraft language identifier through the mod's C2S channel. It is length-bounded, normalized to Spanish/English behavior and contains no credentials or private configuration.

## Custom voice models

Optional custom Piper-compatible models may be placed under:

```text
great_sage_voice/custom_voice/
```

These files remain on the server/integrated-server machine. They are not uploaded by the mod and are not sent to clients; only generated bounded WAV speech is transmitted.

Server owners are responsible for ensuring custom voice models are authorized/licensed for their use.

## Optional API keys

OpenAI/ElevenLabs remain optional enhancement providers. If used, keep keys server-side only. Prefer server environment variables; never place credentials in Java source, resources, Git commits, client config, screenshots/logs or chat commands.

The S2C speech packet contains localized text, emotion/state metadata, language metadata and generated WAV bytes only. Provider credentials are never transmitted to clients.

## Reporting

Report security issues privately to the repository owner before publishing exploit details.
