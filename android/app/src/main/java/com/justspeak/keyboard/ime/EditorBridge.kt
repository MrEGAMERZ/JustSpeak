package com.justspeak.keyboard.ime

import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection

/**
 * Thin InputConnection helpers. D4 hardens composing-region and host-app edges;
 * D1 is enough to insert stub text into the focused field.
 */
class EditorBridge(
    private val connection: () -> InputConnection?,
    private val editorInfo: () -> EditorInfo?,
) {
    fun insert(text: String) {
        if (text.isEmpty()) return
        connection()?.commitText(text, 1)
    }

    fun insertTranscript(text: String, trailingSpace: Boolean = true) {
        val payload = text.trim()
        if (payload.isEmpty()) return
        insert(if (trailingSpace) "$payload " else payload)
    }

    fun space() {
        insert(" ")
    }

    fun backspace() {
        val ic = connection() ?: return
        if (ic.deleteSurroundingText(1, 0)) return
        ic.sendKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_DOWN,
                android.view.KeyEvent.KEYCODE_DEL,
            ),
        )
        ic.sendKeyEvent(
            android.view.KeyEvent(
                android.view.KeyEvent.ACTION_UP,
                android.view.KeyEvent.KEYCODE_DEL,
            ),
        )
    }

    fun performEditorDone() {
        val ic = connection() ?: return
        val info = editorInfo()
        val action = info?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            ?: EditorInfo.IME_ACTION_DONE
        if (action != EditorInfo.IME_ACTION_NONE) {
            ic.performEditorAction(action)
        } else {
            ic.performEditorAction(EditorInfo.IME_ACTION_DONE)
        }
    }
}
