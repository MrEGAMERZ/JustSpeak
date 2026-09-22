# JustSpeak — Idea

**Working name:** JustSpeak  
**Tagline:** On-device voice → text you can type anywhere. SpeakType privacy, Wispr Flow reach.  
**Status:** Idea + architecture lock (2026-09-22)  
**Repo:** https://github.com/MrEGAMERZ/JustSpeak  

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

```
┌─────────────────────────────────────────────┐
│              JustSpeak Core (SDK)           │
│  Audio capture → VAD → ASR → Post-process   │
│  (+ language ID, emotion tags — phased)     │
│  Shared C/C++ API + platform bindings       │
│  (JNI / Swift / FFI)                        │
└───────────────┬─────────────────────────────┘
                │
     ┌──────────┼──────────┐
     ▼          ▼          ▼
 Android IME   iOS Keyboard   Future hosts
 (type anywhere) (type anywhere) (clipboard,
                                  Share, agents)
```

| Layer | Role | v0 |
|---|---|---|
| **Core** | Models, audio pipeline, transcript API | Must ship first |
| **Android app** | Custom IME + onboarding | Primary ship |
| **iOS app** | Custom keyboard + Full Access | After Android E2E |
| **Hosts later** | Clipboard, Shortcuts, desktop, agents | After both keyboards |

Rule: **no app-only logic in Core**. Apps only: UI, permissions, `InputConnection` / keyboard insert.

---

## 3. User journeys

1. **Dictate in WhatsApp / Messages / Notes** — switch to JustSpeak keyboard → mic → speak → Insert.  
2. **Offline commute** — same path with no network.  
3. **Multilingual** — speak Hindi/English (etc.); engine picks model or auto language.  
4. **Emotion-aware (later)** — transcript tagged with tone — **not required for v0**.

---

## 4. Model brainstorm (phone-runnable)

### 4.1 Speech-to-text (primary)

| Model | Why | Size (order) | Languages | Fit |
|---|---|---|---|---|
| **Moonshine Tiny / Base** | Live on-device STT; streaming; MIT; mobile packages | ~125–290 MB | EN-first | **Default EN streaming** |
| **Whisper Tiny / Tiny.en** via **sherpa-onnx** or whisper.cpp | Ubiquitous multilingual tiny | ~30–100 MB | 99 (multi) | **Default multilingual + fallback** |
| **Whisper Base (quantized)** | Better accuracy opt-in | ~50–80 MB+ | EN / multi | Optional “Accurate” mode |
| **SenseVoice Small** (sherpa-onnx) | Strong Asian + EN; fast on mobile benches | ~240 MB | zh/en/ja/ko/yue | **India/Asia path** |
| **Parakeet / Zipformer** (sherpa) | Fast streaming | varies | mainly EN/EU | Later |
| **System SpeechRecognizer** | Zero model ship cost | System | many | Last-resort fallback |

### 4.2 Engines

| Platform | Preferred runtime | Notes |
|---|---|---|
| **Android** | **sherpa-onnx** first; whisper.cpp secondary | sherpa often ≫ whisper.cpp on Android |
| **iOS** | **WhisperKit / Core ML**; Moonshine native if available | Watch 4 GB devices (Tiny only) |
| **Shared Core** | C API over sherpa-onnx + ggml | One `JustSpeakEngine` interface |

### 4.3 v0 model policy

1. Default: **Moonshine Tiny** *or* **Whisper Tiny.en** — whichever integrates cleanly first.  
2. Optional downloads: **Whisper Tiny (multi)** + **SenseVoice Small**.  
3. Never require cloud ASR for core.

### 4.4 Emotion (phase 2 — do not block v0)

SER is weaker than ASR on phones. Keep emotion as an optional module after keyboards work.

### 4.5 TTS

Core product is **STT (speech → text)**. TTS read-back is phase 3 (system TTS / Piper), not v0.

---

## 5. Non-goals (v0)

- Subscription / account wall  
- Cloud ASR by default  
- Full QWERTY polish  
- 100 languages day one  
- AI rewrite tones  
- Desktop Mac app  

---

## 6. Core API (conceptual)

```
prepare(modelId)
startListening()  → partial transcripts
stopListening()   → final Transcript
cancel()
unload()
```

---

## 7. Phased roadmap

| Phase | Outcome |
|---|---|
| **P0 — Core** | AsrEngine + one on-device model + audio pipeline |
| **P1 — Android IME** | Type into Messages/Chrome |
| **P2 — iOS keyboard** | Same Core binding |
| **P3 — Multilingual packs** | Whisper multi + SenseVoice |
| **P4 — Emotion module** | Optional tags |
| **P5 — Hosts** | Clipboard / Share / agents |

---

## 8. Risks

| Risk | Mitigation |
|---|---|
| Model size / APK bloat | Download on first use; Tiny default |
| Android whisper.cpp slow | Prefer sherpa-onnx |
| iOS Full Access trust | Clear privacy copy; open source |
| Emotion overpromise | Keep out of v0 pitch |

---

## 9. Success metrics

- Offline dictation in a third-party text field (Android first)  
- Interactive latency (RTF &lt; 1 on mid phone for Tiny)  
- Zero audio leaves device in default mode  
- Second client reuses Core without rewriting ASR  

---

## 10. Open decisions

1. Default EN: Moonshine Tiny vs Whisper Tiny.en  
2. Core language: C/C++ (sherpa) vs KMP — prefer C API + thin bindings  
3. Emotion: delay until P4  

---

## 11. Positioning

> **JustSpeak is an open on-device voice engine** (phone-first STT) with Android & iOS keyboards as the first apps — SpeakType’s privacy, Wispr’s “type anywhere,” without the subscription.
