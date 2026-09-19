# JustSpeak D1 — nothing to shrink yet. Keep the IME service name stable
# if minify is turned on later (D5).
-keep class com.justspeak.keyboard.JustSpeakImeService { *; }

# D3: JNI symbols for whisper.cpp will land here.
# -keep class com.justspeak.keyboard.asr.whisper.** { *; }
