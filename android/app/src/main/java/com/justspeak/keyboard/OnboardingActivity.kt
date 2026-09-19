package com.justspeak.keyboard

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.justspeak.keyboard.audio.MicPermission
import com.justspeak.keyboard.databinding.ActivityOnboardingBinding

/**
 * Explains how to enable the JustSpeak IME in system settings and asks for the
 * microphone. Launch this from the app icon or from the IME settings gear.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { refreshStatus() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        binding.grantMicButton.setOnClickListener {
            if (MicPermission.isGranted(this)) {
                Toast.makeText(this, R.string.mic_already_granted, Toast.LENGTH_SHORT).show()
            } else {
                micPermissionLauncher.launch(MicPermission.PERMISSION)
            }
        }
        binding.openImeSettingsButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        }
        binding.switchKeyboardButton.setOnClickListener {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
        refreshStatus()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    private fun refreshStatus() {
        val mic = MicPermission.isGranted(this)
        val enabled = isImeEnabled()
        binding.micStatus.text = getString(
            if (mic) R.string.onboarding_mic_ok else R.string.onboarding_mic_missing,
        )
        binding.imeStatus.text = getString(
            if (enabled) R.string.onboarding_ime_ok else R.string.onboarding_ime_missing,
        )
        binding.grantMicButton.isEnabled = !mic
    }

    private fun isImeEnabled(): Boolean {
        val imm = getSystemService(InputMethodManager::class.java) ?: return false
        val id = "$packageName/.JustSpeakImeService"
        return imm.enabledInputMethodList.any { it.id == id || it.serviceName.endsWith("JustSpeakImeService") }
    }
}
