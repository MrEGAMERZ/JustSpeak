# JustSpeak — 7-day plan

Privacy-first voice dictation keyboard. Week goal: **mic → transcript → insert into any app’s text field**.

This is **not** Wispr feature parity.

## Constraint (SIH)

**Do not expand into AEGIS / AeroMesh** (or any sibling SIH product). JustSpeak stays a standalone on-device dictation IME. No mesh networking, no multi-app “suite” branding, no shared auth, no extra product surfaces.

## Day map

| Day | Focus | Status |
| --- | --- | --- |
| **D1** | Android IME skeleton + MIT + README + ASR interface (stub Whisper path) | **Done** |
| **D2** | Mic + runtime permissions + audio pipeline (16 kHz mono PCM → engine) | **Done** |
| **D3** | On-device Whisper English (`whisper.cpp` / ggml, `base.en` or `small.en` quantized) | TODO |
| **D4** | `InputConnection` insert end-to-end (any focused text field) | TODO |
| **D5** | Harden + ship a debuggable APK | TODO |
| **D6** | iOS thin/clipboard **or** Android polish — pick one, do not do both | TODO |
| **D7** | Freeze scope + release notes | TODO |

## D1 — done

- Custom `InputMethodService` registered and enableable in system settings.
- Minimal keyboard UI: mic, transcript, Insert/Done, space, backspace (no QWERTY polish).
- `AsrEngine` interface + stub that returns placeholder text so insert can be tested.
- Onboarding Activity documents the enable path and requests `RECORD_AUDIO`.
- MIT license + README + this plan.

## D2 — done (audio pipeline harden)

- Audio focus request / abandon while recording (`AudioFocusController`).
- Capture race fixes: synchronized start/stop/cancel, no double `AudioRecord`, teardown on IME hide/destroy.
- int16 PCM → float `[-1, 1]` path (`PcmConverters`) fed to `AsrEngine.feedPcmFloat` (stub ignores; Whisper buffers for D3).
- IME permission recovery: missing `RECORD_AUDIO` on mic tap opens onboarding (no crash); setup button + app-settings helper remain available.
- Insert still works with stub transcript (`USE_STUB_ASR=true`).

## D3 — on-device Whisper EN

**Chosen stack:** [whisper.cpp](https://github.com/ggml-org/whisper.cpp) (ggml) via NDK/CMake + JNI, following `examples/whisper.android`.

- Vendor or git-submodule `whisper.cpp`.
- Ship or first-run-download a **quantized English** model, prefer `ggml-base.en-q5_1.bin` (fallback `tiny.en` if APK size / RAM hurts; `small.en` if quality is insufficient).
- `WhisperCppEngine` loads the model from `filesDir/models` or assets and replaces the D1 stub (consume buffered float PCM from D2).
- English only. No 100-language pack.

**Alternate if NDK packaging blocks D3:** ONNX Runtime Mobile + a Whisper ONNX English model. Do not add a cloud ASR “just to demo.”

## D4 — InputConnection insert E2E

- Insert at cursor, preserve composing region rules, handle empty/error transcripts.
- Space after insert, backspace, Done / editor action.
- Verify in several host apps (Messages, Chrome URL bar, Notes).
- No AI rewrite / tone layer.

## D5 — harden + debug APK

- Crash-safe IME (never ANR the keyboard).
- Logging that does **not** persist utterances.
- `assembleDebug` APK + short install notes.

## D6 — fork in the road

Pick **one**:

1. **iOS thin:** clipboard-paste helper (docs already state iOS is out of D1–D5). Not a full custom keyboard unless time is leftover.
2. **Android polish:** better listening indicator, rotation, landscape, a few host-app edge cases.

Do not start subscriptions, themes, or accounts.

## D7 — freeze + release notes

- Tag what works vs. what is stubbed.
- List known gaps (model size, EN-only, no QWERTY, no iOS keyboard).
- Stop. No scope creep.

## Explicitly out of scope (all seven days)

- AI rewrite tones / cleanup LLM
- Full QWERTY polish or theme packs
- 100 languages
- Accounts, paywalls, subscriptions
- iOS app as a peer to Android IME (D6 clipboard-only is the ceiling)
- AEGIS, AeroMesh, or any SIH sibling product
