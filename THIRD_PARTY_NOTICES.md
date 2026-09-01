# Third-party notices

Great Sage Voice itself is licensed under the repository license. Runtime engines, models and Java libraries retain their upstream licenses/notices.

## Piper TTS runtime

Source: `https://github.com/rhasspy/piper`

The mod pins the archived `2023.11.14-2` Piper runtime as an external executable. Its runtime/supporting components retain upstream licenses.

## Spanish base profile — Daniela High

Model: `es_AR-daniela-high`

Source: `https://huggingface.co/rhasspy/piper-voices/tree/main/es/es_AR/daniela/high`

- Spanish (Argentina), high quality, 22,050 Hz;
- dataset: OpenSLR 61;
- dataset license: CC BY-SA 4.0;
- pinned ONNX SHA-256: `7ceb1fc0dab349418c5b54a639ae9ee595212d7c9ea422220d8419163d5cc985`.

## English base profile — Lessac High

Model: `en_US-lessac-high`

Source: `https://huggingface.co/rhasspy/piper-voices/tree/main/en/en_US/lessac/high`

- English (US), high quality, 22,050 Hz;
- dataset: Lessac Blizzard 2013; see upstream model card for dataset terms;
- pinned ONNX SHA-256: `4cabf7c3a638017137f34a1516522032d4fe3f38228a843cc9b764ddcbcd9e09`.

## OpenVoice V2 / ONNX export

OpenVoice project: `https://github.com/myshell-ai/OpenVoice`

ONNX export/model source used by this implementation: `https://huggingface.co/TigreGotico/voiceclonnx-openvoice-v2`

The upstream OpenVoice V2 project/model materials identify the project as MIT-licensed. Great Sage Voice downloads only the quantized reference encoder and tone converter needed for local zero-shot tone transfer.

Pinned files:

```text
tone_ref_encoder_q8.onnx
SHA-256 8e46097e46a68a2137acf105b58bc67cf686ec0d811c1c45ada28557a608c0e3

tone_converter_q8.onnx
SHA-256 54ea73764c46cdbb74af2124e30cce42007045f1f5a60bd7520472f155eb6f4c
```

No actor reference recording or actor-specific source URL is distributed by this repository. Those are installation-local inputs controlled by the operator.

## Microsoft ONNX Runtime

Dependency: `com.microsoft.onnxruntime:onnxruntime:1.24.3`

Project: `https://onnxruntime.ai/`

License: MIT. It is bundled in the distributable mod using Forge Jar-in-Jar.

## JLayer

Dependency: `javazoom:jlayer:1.0.1`

Project: `http://www.javazoom.net/javalayer/javalayer.html`

License: GNU Lesser General Public License (LGPL). It is bundled through Forge Jar-in-Jar and used only to decode installation-local MP3 reference audio.

## yt-dlp

Project: `https://github.com/yt-dlp/yt-dlp`

When an installation-local authorized manifest uses a supported media source, the server may download a pinned upstream yt-dlp executable solely to acquire that reference. The executable exits immediately after acquisition and retains its upstream license/notices.

## Authorized voice inputs

v1.4 supports operator-authorized local reference sources and target embeddings. These inputs are not supplied by this repository. The operator is responsible for the rights/consent applicable to the reference audio and voice identity used in their installation.
