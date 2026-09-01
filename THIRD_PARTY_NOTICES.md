# Third-party notices

Great Sage Voice itself is licensed under the repository license. Its self-managed offline voice path downloads third-party components from upstream at runtime; they are not authored by this mod.

## Piper TTS runtime

Source: `https://github.com/rhasspy/piper`

Great Sage Voice pins the archived `2023.11.14-2` Piper runtime as an external executable and communicates with it through standard process input/output/files. The runtime and its supporting components retain their upstream licenses/notices.

## Spanish built-in profile — Daniela High

Model: `es_AR-daniela-high`

Source: `https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_AR/daniela/high`

Upstream model-card information:

- language: Spanish (Argentina);
- one speaker;
- quality: high;
- sample rate: 22,050 Hz;
- dataset: OpenSLR 61;
- dataset license: Creative Commons Attribution-ShareAlike 4.0 International;
- trained by `larcanio/piper-voices` and fine-tuned from the high-quality U.S. English Lessac voice.

Pinned ONNX SHA-256:

```text
7ceb1fc0dab349418c5b54a639ae9ee595212d7c9ea422220d8419163d5cc985
```

## English built-in profile — Lessac High

Model: `en_US-lessac-high`

Source: `https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/high`

Upstream model-card information:

- language: English (United States);
- one speaker;
- quality: high;
- sample rate: 22,050 Hz;
- dataset: Lessac Blizzard 2013;
- dataset/license terms: see the upstream Lessac Blizzard 2013 project license linked by the Piper model card;
- trained from scratch.

Pinned ONNX SHA-256:

```text
4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09
```

## Character identity and custom models

The built-in profiles are used as original synthetic presentations inspired by the calm analytical character direction of Raphael / Gran Sabio. They are not represented as the recordings or cloned voices of real dub performers.

v1.3 can prefer user-supplied Piper-compatible `custom_voice/es.onnx` and `custom_voice/en.onnx` models. Those files are not supplied by this repository. Server owners are responsible for ensuring they have the necessary rights/consent/licenses for any custom voice model they install.
