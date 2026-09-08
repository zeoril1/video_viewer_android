package com.zeoril.videoviewertv

import android.app.Activity
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.EditText
import android.widget.TextView

/** Экран настроек: адрес сервера Video Viewer (например 192.168.1.10:8080). */
class SettingsActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        val input = findViewById<EditText>(R.id.serverInput)
        val hint = findViewById<TextView>(R.id.settingsHint)
        val save = findViewById<Button>(R.id.btnSave)

        val existing = getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
            .getString(Prefs.KEY_URL, null)
        if (!existing.isNullOrBlank()) {
            input.setText(existing)
            input.setSelection(input.text.length)
        }

        save.setOnClickListener {
            val v = input.text.toString().trim()
            if (v.isEmpty()) {
                hint.text = "Введите адрес сервера, например 192.168.1.10:8080"
                return@setOnClickListener
            }
            getSharedPreferences(Prefs.NAME, MODE_PRIVATE)
                .edit()
                .putString(Prefs.KEY_URL, v)
                .apply()
            finish()
        }

        input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                save.performClick()
                true
            } else {
                false
            }
        }
    }
}
