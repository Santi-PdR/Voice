# Authorized voice architecture

Great Sage Voice v1.4 supports installation-local, authorized zero-shot tone conversion without placing performer-specific references in the public repository.

## Public code vs local authorization

The repository contains only the generic acquisition/conversion engine. An authorized installation may add:

```text
great_sage_voice/authorized_voice/authorization.accepted
great_sage_voice/authorized_voice/sources.json
```

The source manifest is local runtime data and is not part of the mod artifact.

## Pipeline

```text
localized Raphael text
 -> Piper ES/EN base WAV
 -> 22.05 kHz mono conversion
 -> source tone embedding
 -> authorized target embedding
 -> OpenVoice V2 ONNX converter
 -> conservative output normalization/fades
 -> bounded Forge WAV packet
```

Target embeddings are extracted once from the configured references and cached. Subsequent responses do not need to re-analyze the complete reference media.

## Resilience

Every optional stage is fail-soft. If authorization files are absent or reference acquisition/model inference fails, the original Piper WAV is used. Voice-clone failures never block the local analytical core, HUD or event handling.

## Distribution

ONNX Runtime and JLayer are bundled with Forge Jar-in-Jar. Piper/OpenVoice model assets and acquisition helpers are cached outside the mod JAR under the Minecraft/server game directory.
