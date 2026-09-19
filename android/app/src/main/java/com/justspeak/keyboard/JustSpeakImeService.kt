package com.justspeak.keyboard

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import com.justspeak.keyboard.asr.AsrEngine
import com.justspeak.keyboard.asr.AsrError
import com.justspeak.keyboard.asr.AsrListener
import com.justspeak.keyboard.asr.AsrProvider
import com.justspeak.keyboard.asr.AsrState
import com.justspeak.keyboard.audio.AudioCapture
import com.justspeak.keyboard.audio.MicPermission
import com.justspeak.keyboard.databinding.KeyboardViewBinding
import com.justspeak.keyboard.ime.EditorBridge

/**
 * System keyboard. Mic → (stub) transcript → Insert into the focused field.
 *
 * Not a full QWERTY. Space / backspace exist so you can fix the insertion point.
 */
class JustSpeakImeService : InputMethodService() {

    private var binding: KeyboardViewBinding? = null
    private lateinit var asr: AsrEngine
    private val capture = AudioCapture()
    private val editor = EditorBridge(
        connection = { currentInputConnection },
        editorInfo = { currentInputEditorInfo },
    )

    private var latestTranscript: String = ""
    private var listening: Boolean = false

    override fun onCreate() {
        super.onCreate()
        asr = AsrProvider.create(this)
    }

    override fun onCreateInputView(): View {
        val views = KeyboardViewBinding.inflate(layoutInflater)
        binding = views
        views.micButton.setOnClickListener { toggleMic() }
        views.insertButton.setOnClickListener { insertTranscript() }
        views.doneButton.setOnClickListener { finishEditor() }
        views.spaceButton.setOnClickListener { editor.space() }
        views.backspaceButton.setOnClickListener { editor.backspace() }
        views.setupButton.setOnClickListener { openOnboarding() }
        renderIdle()
        return views.root
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        renderPermissionState()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        stopListening()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        stopListening()
        if (::asr.isInitialized) asr.release()
        capture.release()
        binding = null
        super.onDestroy()
    }

    private fun toggleMic() {
        if (listening) {
            stopListening()
            return
        }
        if (!MicPermission.isGranted(this)) {
            renderPermissionState()
            return
        }
        startListening()
    }

    private fun startListening() {
        listening = true
        latestTranscript = ""
        binding?.transcriptView?.text = getString(R.string.listening)
        binding?.statusView?.text = getString(R.string.status_listening, asr.backendName)
        binding?.micButton?.isSelected = true
        binding?.micButton?.setImageResource(R.drawable.ic_mic)
        binding?.micLabel?.text = getString(R.string.mic_stop)

        val captureResult = capture.start { _, _ ->
            // D2: feed PCM into WhisperCppEngine. D1 records so the permission
            // and AudioRecord path are real; the stub ASR ignores samples.
        }
        if (captureResult.isFailure) {
            binding?.statusView?.text = getString(
                R.string.status_mic_error,
                captureResult.exceptionOrNull()?.message ?: "unknown",
            )
        }

        asr.start(object : AsrListener {
            override fun onPartialTranscript(text: String) {
                latestTranscript = text
                binding?.root?.post {
                    binding?.transcriptView?.text = text
                }
            }

            override fun onFinalTranscript(text: String) {
                latestTranscript = text
                binding?.root?.post {
                    binding?.transcriptView?.text = text
                    binding?.statusView?.text =
                        getString(R.string.status_ready_insert, asr.backendName)
                    setListeningUi(false)
                }
                listening = false
                capture.stop()
            }

            override fun onState(state: AsrState) {
                binding?.root?.post {
                    if (state == AsrState.Transcribing) {
                        binding?.statusView?.text = getString(R.string.status_transcribing)
                    }
                }
            }

            override fun onError(error: AsrError) {
                binding?.root?.post {
                    if (error.code != AsrError.Code.Cancelled) {
                        binding?.statusView?.text = error.message
                    }
                }
            }
        })
    }

    private fun stopListening() {
        if (!listening && !asr.isRunning && !capture.isRunning) {
            setListeningUi(false)
            return
        }
        listening = false
        asr.stop()
        capture.stop()
        setListeningUi(false)
        binding?.statusView?.text = getString(R.string.status_idle, asr.backendName)
    }

    private fun insertTranscript() {
        val text = latestTranscript.ifBlank {
            binding?.transcriptView?.text?.toString().orEmpty()
        }
        editor.insertTranscript(text)
    }

    private fun finishEditor() {
        editor.performEditorDone()
        requestHideSelf(0)
    }

    private fun openOnboarding() {
        val intent = Intent(this, OnboardingActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(intent)
    }

    private fun renderIdle() {
        latestTranscript = ""
        binding?.transcriptView?.text = getString(R.string.transcript_placeholder)
        renderPermissionState()
        setListeningUi(false)
    }

    private fun renderPermissionState() {
        val granted = MicPermission.isGranted(this)
        binding?.setupButton?.visibility = if (granted) View.GONE else View.VISIBLE
        binding?.micButton?.isEnabled = true
        binding?.statusView?.text = if (granted) {
            getString(R.string.status_idle, asr.backendName)
        } else {
            getString(R.string.status_need_mic)
        }
    }

    private fun setListeningUi(active: Boolean) {
        binding?.micButton?.isSelected = active
        binding?.micLabel?.text = getString(if (active) R.string.mic_stop else R.string.mic_start)
    }
}
