# Whisper models (D3)

Do **not** commit ggml weights. Place a quantized English model here at build time
or copy it into the app `filesDir/models/` on first run.

Preferred (D3):

```
ggml-base.en-q5_1.bin
```

Fallbacks: `ggml-tiny.en-q5_1.bin` (smaller) or `ggml-small.en-q5_1.bin` (better).

Download via the scripts in [whisper.cpp/models](https://github.com/ggml-org/whisper.cpp/tree/master/models).

D1 does not load a model. `WhisperCppEngine` looks for `WHISPER_MODEL_NAME`
(`BuildConfig`) under `filesDir/models/` and otherwise uses `StubAsrEngine`.
