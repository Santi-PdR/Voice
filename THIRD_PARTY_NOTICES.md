# Third-party notices

Great Sage Voice itself is licensed under the repository license. The optional/self-managed voice runtime downloads third-party components from their upstream projects at runtime; they are not authored by this mod.

## Piper TTS runtime

Source: https://github.com/rhasspy/piper

The archived Piper project identifies its code as MIT-licensed. Development later moved to a GPL-licensed successor; Great Sage Voice intentionally downloads the pinned 2023.11.14-2 archived runtime as an external executable and communicates with it through standard input/output/file process boundaries.

The Piper runtime also contains supporting components with their own licenses. Refer to the upstream release/project for their complete notices.

## Daniela high Spanish voice

Model: `es_AR-daniela-high`

Source: https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_AR/daniela/high

Model card information:

- Language: Spanish (Argentina)
- Quality: high
- Sample rate: 22,050 Hz
- Dataset: OpenSLR 61
- Dataset license: Creative Commons Attribution-ShareAlike 4.0 International
- Piper voice model trained by `larcanio/piper-voices`

Great Sage Voice downloads this model from the upstream Piper voices repository and verifies the ONNX file against a pinned SHA-256 before use.

The voice is used as an original synthetic presentation profile. It is not represented as the voice of, or a clone of, any real dub actor.
