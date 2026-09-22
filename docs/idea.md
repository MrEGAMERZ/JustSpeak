# JustSpeak — system idea

Last updated: 2026-09-22

Privacy-first **on-device** speech system. Phone apps (Android IME, iOS keyboard / share extension) are **consumers** of a shared core engine — not two fully separate products. Inspired by the *shape* of SpeakType / Wispr-style flows (mic → local ASR → text in the focused field), not feature parity.

Related: [PLAN.md](./PLAN.md) (week-1 ship map). This file is the longer architecture brainstorm.

---

## 1. Vision

| | |
| --- | --- |
| **Job** | Speak → accurate text appears in whatever app you’re typing into |
| **Default** | Audio never leaves the device |
| **License / posture** | MIT / OSS; no account wall for core dictation |
| **Platforms** | Android + iOS as *shells*; one core STT (+ optional emotion / language) |

**Non-goals (near term):** AI rewrite tones, themes, subscriptions, full QWERTY polish, cloud ASR as the default path, 100-language day-one packs.

---

## 2. System shape (core vs shells)

```text
┌─────────────────────────────────────────────────────────┐
│                    Platform shells                       │
│  Android IME │ iOS custom keyboard / share │ clipboard   │
│  (InputConnection / insert / paste)                      │
└──────────────────────────▲──────────────────────────────┘
                           │ transcript (+ optional tags)
┌──────────────────────────┴──────────────────────────────┐
│                 JustSpeak Core Engine                    │
│  AudioIn → VAD/chunk → STT → (Emotion?) → (Lang ID?)     │
│  Model pack · runtime (whisper.cpp / WhisperKit / ORT)   │
└──────────────────────────▲──────────────────────────────┘
                           │ PCM 16 kHz mono
┌──────────────────────────┴──────────────────────────────┐
│              Capture (platform Audio APIs)               │
└─────────────────────────────────────────────────────────┘
```

### Core (shared responsibilities)

- Model loading / download / cache (quantized ggml or Core ML / ONNX)
- Inference API: `AudioBuffer → TranscriptResult { text, lang?, emotion?, confidence? }`
- English-first; multilingual models as a **pack**, not a rewrite of the shells
- No network permission required for the default path

### Shells (platform-only)

| Shell | Role |
| --- | --- |
| **Android IME** | Primary ship path today — `InputMethodService`, mic UI, insert via `InputConnection` |
| **iOS keyboard / extension** | Peer consumer of the same core (higher Apple keyboard constraints) |
| **Clipboard / share helper** | Thin fallback when a full keyboard isn’t ready |

Week-1 reality check: Android IME + stub→Whisper is the executable path in [PLAN.md](./PLAN.md). iOS is Day-6 thin/clipboard unless the core is proven first. Long-term, both platforms should call the **same** engine contract.

---

## 3. Phone-runnable model shortlist

Sizes below are **approx disk** for common ggml quantizations ([ggerganov/whisper.cpp on Hugging Face](https://huggingface.co/ggerganov/whisper.cpp)). Latency/RAM vary by SoC; treat numbers as planning bounds, not guarantees. **TODO:** measure on target devices.

### 3.1 Speech-to-text (primary)

| Model | ~Disk | Role | Notes |
| --- | --- | --- | --- |
| `tiny.en-q5_1` | ~31 MiB | Smoke / low-end | Fastest EN; weaker accuracy |
| **`base.en-q5_1`** | **~57 MiB** | **Default MVP** | Matches PLAN.md; good EN quality/size tradeoff |
| `base.en-q8_0` | ~78 MiB | Quality bump | If q5_1 quality hurts |
| `small.en` (quantized) | ~hundreds MiB class | Optional upsell | Only if base isn’t enough on mid phones |
| `base` / `small` (multilingual) | larger | Phase 2 | EN-first MVP; swap pack, keep API |

**Runtime picks**

| Platform | Preferred stack | Alternate |
| --- | --- | --- |
| **Android** | [whisper.cpp](https://github.com/ggml-org/whisper.cpp) via NDK/JNI (`examples/whisper.android`) | ONNX Runtime Mobile + Whisper ONNX if NDK packaging blocks |
| **iOS** | WhisperKit (Core ML / ANE) **or** whisper.cpp XCFramework / Metal | Same ggml model family when possible for parity |

**License note:** OpenAI Whisper weights are MIT; whisper.cpp is MIT. Keep third-party notices in-repo.

### 3.2 Multilingual

- **MVP:** English-only (`*.en`) packs — smaller, sharper for EN dictation.
- **Phase 2:** Multilingual `base`/`small` ggml; detect or let user pick language; do **not** ship “100 languages” as a marketing checkbox without per-locale QA.
- Optional tiny **language-ID** head later (research TODO) so the core can auto-select a pack.

### 3.3 Emotion / paralinguistics (phase 2+)

Dictation MVP does **not** need emotion. When added, keep it **on-device**, optional, and behind the same core API (`emotion?: { arousal, valence, … }` or categorical labels).

| Candidate | Why it’s phone-interesting | Caveat |
| --- | --- | --- |
| **Wav2Small** (dimensional A/D/V; ~72K params; ~120 KB quantized ONNX reported in paper) | Tiny footprint for arousal/dominance/valence | Integration + quality TODO on real phones; cite [arXiv:2408.13920](https://arxiv.org/abs/2408.13920) |
| Categorical Wav2Vec2-SER ONNX (~tens–~90 MB INT8 class) | Familiar labels (happy/sad/…) | Heavier than Wav2Small; battery/thermal TODO |

Do **not** invent accuracy numbers. Prototype offline before promising UI.

---

## 4. Data flow (happy path)

1. Shell requests mic permission / audio focus.
2. Capture **16 kHz mono** PCM (int16 or float `[-1, 1]`).
3. Optional VAD / end-of-utterance (TODO: WebRTC VAD or energy gate).
4. Core runs STT → partials (if streaming) → final transcript.
5. Optional: emotion / lang tags on the same buffer (async, must not block insert).
6. Shell **inserts** text into the focused field (Android `InputConnection`, iOS text document proxy) or falls back to clipboard.
7. Buffers discarded; **no utterance persistence** in default builds (see SECURITY when that doc exists).

---

## 5. MVP vs later

Aligned with [PLAN.md](./PLAN.md):

| Now (week-shaped) | Later |
| --- | --- |
| Android IME + core interface | Shared core package consumed by iOS |
| Stub → `base.en-q5_1` Whisper | Multilingual packs |
| Insert into any text field | Streaming partials UX polish |
| MIT, no accounts | Emotion tags, lang ID |
| | Full iOS keyboard parity |

**Still cut unless explicitly reopened:** rewrite LLM, themes, subscriptions, cloud-default ASR, suite branding with sibling products.

---

## 6. Risks

| Risk | Mitigation |
| --- | --- |
| Model size / APK / IPA bloat | Default `base.en-q5_1`; first-run download; tiny.en fallback |
| NDK / JNI / Core ML packaging | Alternate ORT / WhisperKit paths documented |
| Battery & thermals | Short utterances; cancel on hide; no always-on cloud |
| iOS keyboard memory limits | Thin keyboard UI; heavy work in shared process / extension carefully |
| Scope creep vs other demos | Keep JustSpeak standalone; protect competing demo weeks |
| Cloud-agent / CI budget | Local Android Studio / Mac builds remain first-class |

---

## 7. Next experiments (prove on a phone)

1. **Latency:** hold-to-talk 3–5 s EN → `base.en-q5_1` on a mid Android + one recent iPhone (p50/p95 wall time). **TODO:** fill table after measurement.
2. **Insert E2E:** Messages / Chrome / Notes on Android IME (PLAN D4).
3. **Parity spike:** same ggml buffer through whisper.cpp Android + WhisperKit/iOS — transcript diff on a fixed sample set.
4. **Emotion spike (optional):** Wav2Small ONNX on 10 clips; decide if UI is worth it.
5. **Packaging:** APK size with embedded vs downloaded model.

---

## 8. Open questions

- Streaming partials in IME vs final-only for v1?
- Single shared C++ core vs thin platforms calling WhisperKit + whisper.cpp separately with a shared API contract?
- First-run model download UX vs shipping tiny.en in the binary?

---

## References

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (+ Android / iOS examples)
- [ggml Whisper models](https://huggingface.co/ggerganov/whisper.cpp)
- WhisperKit (Apple Silicon / Core ML) — evaluate for iOS shell
- Wav2Small / dimensional SER — [arXiv:2408.13920](https://arxiv.org/abs/2408.13920)
- In-repo week plan: [PLAN.md](./PLAN.md)
