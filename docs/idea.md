# JustSpeak — Idea

**Working name:** JustSpeak  
**Tagline:** On-device voice → text you can type anywhere. SpeakType privacy, Wispr Flow reach.  
**Status:** Idea + architecture lock (2026-09-22)  
**Repo:** https://github.com/MrEGAMERZ/JustSpeak  

Related week ship map: [PLAN.md](./PLAN.md) (on the Android IME branch until merged).

---

## 1. What we’re building

A **system first**, apps second.

JustSpeak is an **on-device speech understanding engine** that turns microphone audio into text (STT), with optional emotion / language signals, then lets **any client** insert that text — keyboards, note apps, agents, IDE plugins.

Inspired by:

- **Wispr Flow** — voice keyboard UX in every text field (iOS/Android), polished output
- **SpeakType** — 100% offline Whisper on Mac, MIT, no subscription wall

**Promise:** mic → private on-device STT → text in any app. No account required for core dictation.

---

## 2. Product shape (system vs apps)

```text
┌─────────────────────────────────────────────────────────┐
│                 JustSpeak Core (SDK)                     │
│  Audio capture → VAD → ASR → Post-process                │
│  (+ language ID, emotion tags — phased)                  │
│  Shared C/C++ API + platform bindings                    │
│  (JNI / Swift / FFI)                                     │
└──────────────────────────┬──────────────────────────────┘
                           │
          ┌────────────────┼────────────────┐
          ▼                ▼                ▼
   Android IME        iOS Keyboard     Future hosts
   (type anywhere)    (type anywhere)  (clipboard,
                                        Share, agents)
```

| Layer | Role | v0 |
| --- | --- | --- |
| **Core** | Models, audio pipeline, transcript API | Must ship first |
| **Android app** | Custom IME + onboarding | Primary ship |
| **iOS app** | Custom keyboard + Full Access | After Android E2E |
| **Hosts later** | Clipboard, Shortcuts, desktop, agents | After both keyboards |

**Rule:** **no app-only logic in Core**. Apps only: UI, permissions, `InputConnection` / keyboard insert.

---

## 3. User journeys

1. **Dictate in WhatsApp / Messages / Notes** — switch to JustSpeak keyboard → mic → speak → Insert.
2. **Offline commute** — same path with no network.
3. **Multilingual** — speak Hindi/English (etc.); engine picks model or auto language (phase).
4. **Emotion-aware (later)** — transcript tagged with tone — **not required for v0**.

---

## 4. Model brainstorm (phone-runnable)

Sizes are **order-of-magnitude** for planning. **TODO:** measure latency/RAM on target phones. Do not invent benchmarks.

### 4.1 Speech-to-text (primary)

| Model | Why | Size (order) | Languages | Fit |
| --- | --- | --- | --- | --- |
| **Moonshine Tiny / Base** | Live on-device STT; streaming; MIT; mobile packages | ~125–290 MB | EN-first | Strong **EN streaming** candidate |
| **Whisper Tiny / Tiny.en** via **sherpa-onnx** or whisper.cpp | Ubiquitous multilingual tiny | ~30–100 MB | 99 (multi) | Strong **multilingual + fallback** |
| **Whisper `base.en-q5_1`** (ggml / whisper.cpp) | Matches current [PLAN.md](./PLAN.md) Android path | ~57 MB | EN | **Default week-1 Android** until experiments say otherwise |
| **Whisper Base** (quantized, multi) | Better accuracy opt-in | ~50–80 MB+ | EN / multi | Optional “Accurate” mode |
| **SenseVoice Small** (sherpa-onnx) | Strong Asian + EN; fast on mobile benches | ~240 MB | zh/en/ja/ko/yue | **India/Asia path** |
| **Paraformer / Zipformer** (sherpa) | Fast streaming | varies | mainly EN/EU | Later |
| **System SpeechRecognizer** | Zero model ship cost | System | many | Last-resort fallback (not privacy default) |

### 4.2 Engines

| Platform | Preferred runtime | Notes |
| --- | --- | --- |
| **Android** | whisper.cpp JNI for PLAN week-1; **sherpa-onnx** as parallel spike (often smoother packaging) | Prefer whichever wins latency + APK size on a mid phone |
| **iOS** | **WhisperKit / Core ML**; Moonshine native if available | Watch 4 GB device limits (Tiny-class only) |
| **Shared Core** | C API over ggml and/or sherpa | One `JustSpeakEngine` interface |

### 4.3 v0 model policy

1. **Week-1 Android (aligned with PLAN):** ship / download **`ggml-base.en-q5_1`** via whisper.cpp; fallback `tiny.en-q5_1` if size/RAM hurts.
2. **Experiment track (do not block IME):** Moonshine Tiny vs Whisper Tiny.en for streaming EN; SenseVoice Small for Hinglish / India.
3. **Never** require cloud ASR for core.

### 4.4 Emotion (phase 2 — do not block v0)

SER is weaker than ASR on phones. Keep emotion as an optional module after keyboards work.

| Candidate | Why phone-interesting | Caveat |
| --- | --- | --- |
| **Wav2Small** (~72K params; ~120 KB quantized ONNX reported) | Dimensional A/D/V; tiny footprint ([arXiv:2408.13920](https://arxiv.org/abs/2408.13920)) | Integration + quality TODO |
| Categorical Wav2Vec2-SER ONNX (~tens–~90 MB INT8 class) | Familiar labels | Heavier; battery TODO |

### 4.5 TTS

Core product is **STT (speech → text)**. TTS read-back is phase 3 (system TTS / Piper), not v0.

---

## 5. Non-goals (v0)

- Subscription / account wall
- Cloud ASR by default
- Full QWERTY polish
- 100 languages day one
- AI rewrite tones
- Desktop Mac app as a peer to phone keyboards

---

## 6. Core API (conceptual)

```text
prepare(modelId)
startListening()  → partial transcripts
stopListening()   → final Transcript
cancel()
unload()
```

`Transcript` may later carry `lang?`, `emotion?`, `confidence?`. Emotion/lang must not block insert.

---

## 7. Phased roadmap

| Phase | Outcome |
| --- | --- |
| **P0 — Core** | `AsrEngine` + one on-device model + audio pipeline |
| **P1 — Android IME** | Type into Messages/Chrome |
| **P2 — iOS keyboard** | Same Core binding |
| **P3 — Multilingual packs** | Whisper multi + SenseVoice |
| **P4 — Emotion module** | Optional tags |
| **P5 — Hosts** | Clipboard / Share / agents |

Week execution detail stays in [PLAN.md](./PLAN.md) (D1–D7).

---

## 8. Risks

| Risk | Mitigation |
| --- | --- |
| Model size / APK bloat | Download on first use; Tiny / `base.en-q5_1` default |
| Android whisper.cpp slow / hard NDK | Spike sherpa-onnx in parallel |
| iOS Full Access trust | Clear privacy copy; open source |
| Emotion overpromise | Keep out of v0 pitch |
| Competing demo weeks | Keep JustSpeak standalone; no sibling-product scope creep |

---

## 9. Success metrics

- Offline dictation in a third-party text field (Android first)
- Interactive latency feelable as “live enough” on a mid phone for Tiny / base.en (**TODO:** fill RTF / wall-ms after measurement)
- Zero audio leaves device in default mode
- Second client reuses Core without rewriting ASR

---

## 10. Open decisions

1. Default EN long-term: Moonshine Tiny vs Whisper `base.en` / Tiny.en — **decide after on-device bake-off**
2. Core language: C/C++ (sherpa + ggml) vs KMP — prefer **C API + thin bindings**
3. Emotion: delay until P4
4. Streaming partials in IME vs final-only for v1?

---

## 11. Positioning

> **JustSpeak is an open on-device voice engine** (phone-first STT) with Android & iOS keyboards as the first apps — SpeakType’s privacy, Wispr’s “type anywhere,” without the subscription.

---

## References

- [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (+ Android / iOS examples)
- [ggml Whisper models](https://huggingface.co/ggerganov/whisper.cpp)
- sherpa-onnx / Moonshine / SenseVoice — evaluate on device before locking defaults
- WhisperKit — iOS Core ML path
- Wav2Small — [arXiv:2408.13920](https://arxiv.org/abs/2408.13920)
- In-repo: [PLAN.md](./PLAN.md)
