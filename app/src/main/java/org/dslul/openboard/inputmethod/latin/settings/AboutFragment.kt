/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.latin.settings

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.preference.Preference
import org.dslul.openboard.inputmethod.latin.BuildConfig
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.utils.Log
import org.dslul.openboard.inputmethod.latin.utils.SpannableStringUtils

/**
 * "About" sub screen.
 */
class AboutFragment : SubScreenFragment() {
    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_about)

        setupHiddenFeatures()
        setupVersionPref()
        findPreference<Preference>("log_reader")?.setOnPreferenceClickListener {
            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .putExtra(Intent.EXTRA_TITLE,
                    requireContext().getString(R.string.english_ime_name)
                        .replace(" ", "_") + "_log_${System.currentTimeMillis()}.txt"
                )
                .setType("text/plain")
            logFilePicker.launch(intent)
            true
        }
    }

    private val logFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = result.data?.data ?: return@registerForActivityResult
        activity?.contentResolver?.openOutputStream(uri)?.use { os ->
            os.bufferedWriter().use { it.write(Log.getLog().joinToString("\n")) }
        }
    }

    private fun setupHiddenFeatures() {
        findPreference<Preference>("hidden_features")?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                val link = ("<a href=\"https://developer.android.com/reference/android/content/Context#createDeviceProtectedStorageContext()\">"
                            + getString(R.string.hidden_features_text) + "</a>")
                val message = requireContext().getString(R.string.hidden_features_message, link)
                val dialogMessage = SpannableStringUtils.fromHtml(message)
                val builder = AlertDialog.Builder(requireContext())
                        .setIcon(R.drawable.ic_settings_about_hidden_features)
                        .setTitle(R.string.hidden_features_title)
                        .setMessage(dialogMessage)
                        .setPositiveButton(R.string.dialog_close, null)
                        .create()
                builder.show()
                (builder.findViewById<View>(android.R.id.message) as TextView).movementMethod = LinkMovementMethod.getInstance()
                true
            }
    }

    private fun setupVersionPref() {
        val versionPreference = findPreference<Preference>("pref_key_version") ?: return
        versionPreference.summary = BuildConfig.VERSION_NAME
        if (BuildConfig.DEBUG) return
        var count = 0
        versionPreference.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                if (sharedPreferences.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false))
                    return@OnPreferenceClickListener true
                count++
                if (count < 5) return@OnPreferenceClickListener true
                sharedPreferences.edit().putBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, true).apply()
                Toast.makeText(requireContext(), R.string.prefs_debug_settings_enabled, Toast.LENGTH_LONG).show()
                true
            }
    }
}