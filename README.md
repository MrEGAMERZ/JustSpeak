# JustSpeak

Privacy-first **voice dictation keyboard**. Speak, get a local transcript, insert it into whatever text field is focused.

Week goal: **mic → transcript → insert**. This is **not** Wispr (no AI rewrite tones, no cloud accounts, no 100-language product).

Android is the only shipped platform in this repo today. iOS is documented only (possible D6 clipboard helper).

License: [MIT](LICENSE).

## What’s in Day 1

| Piece | Location |
| --- | --- |
| Custom IME (`InputMethodService`) | `android/app/src/main/java/com/justspeak/keyboard/JustSpeakImeService.kt` |
| IME registration + enable path | `AndroidManifest.xml` + `res/xml/method.xml` + onboarding |
| Minimal keyboard UI | mic, transcript, Insert / Done, space, backspace |
| ASR interface + stub | `asr/AsrEngine.kt`, `StubAsrEngine.kt`, `WhisperCppEngine.kt` |
| Mic permission + capture hooks | `OnboardingActivity`, `audio/MicPermission.kt`, `audio/AudioCapture.kt` |
| 7-day plan | [docs/PLAN.md](docs/PLAN.md) |

The stub ASR returns placeholder text so you can test **Insert** without a Whisper model. D3 replaces the stub with on-device English Whisper.

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│  Host app text field                                        │
│       ▲  InputConnection.commitText / delete / editorAction │
│  JustSpeakImeService  (system IME)                          │
│       │                                                     │
│       ├─ Keyboard UI (mic, transcript, Insert/Done, ⌫)      │
│       ├─ EditorBridge (thin InputConnection wrapper)        │
│       ├─ AudioCapture (16 kHz mono PCM hooks → D2)          │
│       └─ AsrEngine                                          │
│              ├─ StubAsrEngine          (D1 default)         │
│              └─ WhisperCppEngine       (D3: whisper.cpp)    │
└─────────────────────────────────────────────────────────────┘
```

**On-device ASR choice (D3):** [whisper.cpp](https://github.com/ggml-org/whisper.cpp) / ggml, JNI via the official `examples/whisper.android` pattern. Target model: quantized **`ggml-base.en`** (or `tiny.en` / `small.en` if size vs. quality forces it). Fallback if NDK packaging is too heavy: ONNX Runtime Mobile + Whisper ONNX. No cloud recognizer.

D1 does **not** vendor whisper.cpp or ship weights. `WhisperCppEngine` documents the JNI contract and falls back to the stub so the IME insert path stays testable.

## Enable the keyboard (device)

1. Install the debug APK.
2. Open **JustSpeak** (onboarding) → **Grant microphone** → **Open keyboard settings**.
3. System path: **Settings → System → Languages & input → On-screen keyboard → Manage keyboards → JustSpeak** (wording varies by OEM).
4. In any text field, switch input method to **JustSpeak**.
5. Tap the mic (D1: stub transcript appears) → **Insert**.

An IME cannot reliably show a runtime-permission dialog from the keyboard window on all API levels. Grant `RECORD_AUDIO` from the onboarding Activity.

## Build

Project lives under [`android/`](android/) so a later iOS tree can sit beside it.

### Toolchain (pin these)

| Tool | Version |
| --- | --- |
| Android Gradle Plugin | **8.7.2** |
| Gradle | **8.9** (wrapper) |
| Kotlin | **2.1.20** |
| compileSdk / targetSdk | **35** |
| minSdk | **26** |
| JDK | **17+** (JDK 21 works; AGP compiles with Java 17 bytecode) |
| Build Tools | **34.0.0+** |
| NDK | not required for D1; D3 will need NDK **27.x** (AGP 8.7 default) |

### Android Studio

1. Open the `android/` directory (not the repo root) as a Gradle project.
2. Sync. Install SDK Platform 35 if prompted.
3. Run the `app` configuration on a device or emulator (API 26+).
4. Enable the IME using the onboarding screen.

### CLI

```bash
cd android
# optional: echo "sdk.dir=$HOME/Android/Sdk" > local.properties
./gradlew :app:assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew :app:testDebugUnitTest
```

`local.properties` is gitignored. Point `sdk.dir` at your Android SDK.

### If this VM cannot assemble

This Cloud Agent image has **JDK 21** but **no Android SDK**. If `assembleDebug` was not run here, install:

```text
cmdline-tools, platforms;android-35, build-tools;35.0.0, platform-tools
```

then re-run `./gradlew :app:assembleDebug` from `android/`. Versions above are the source of truth.

## Project layout

```
android/
  settings.gradle.kts
  gradle/libs.versions.toml
  app/src/main/java/com/justspeak/keyboard/
    JustSpeakImeService.kt      # InputMethodService
    OnboardingActivity.kt       # enable-IME + mic permission
    asr/                        # AsrEngine + stub + Whisper facade
    audio/                      # AudioRecord + permission hooks
    ime/EditorBridge.kt         # InputConnection helpers
  app/src/main/res/xml/method.xml
docs/PLAN.md
```

## MVP cut list

**In (this week)**

- Android custom IME
- Mic → on-device English transcript → insert
- Setup screen to enable the keyboard
- MIT, this README, the 7-day plan

**Out (do not implement)**

- AI rewrite / tone presets
- Theme packs, full QWERTY polish
- 100 languages
- Accounts, paywalls, subscriptions
- iOS app (docs only until D6)
- AEGIS / AeroMesh or any SIH sibling product — see [docs/PLAN.md](docs/PLAN.md)

## Day 2+ TODOs

- **D2:** harden `AudioCapture`, audio focus, float conversion, IME permission recovery.
- **D3:** vendor whisper.cpp, CMake/JNI, load `ggml-base.en-q5_1.bin`, swap stub.
- **D4:** E2E insert in multiple host apps; composing / empty / error cases.
- **D5:** debug APK + no-ANR IME hardening.
- **D6:** iOS clipboard **or** Android polish — one track.
- **D7:** freeze + release notes.

## Libraries (D1)

- AndroidX Core / AppCompat / Activity / Material (keyboard + onboarding UI)
- Kotlin coroutines (ASR stub timing, capture thread handoff)

No Play services speech recognizer. No network permission. Models stay on device.
