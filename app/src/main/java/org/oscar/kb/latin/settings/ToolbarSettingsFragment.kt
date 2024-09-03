package org.oscar.kb.latin.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import org.oscar.kb.R
import org.oscar.kb.keyboard.KeyboardSwitcher
import org.oscar.kb.latin.utils.defaultClipboardToolbarPref
import org.oscar.kb.latin.utils.defaultPinnedToolbarPref
import org.oscar.kb.latin.utils.defaultToolbarPref
import org.oscar.kb.latin.utils.getToolbarIconByName
import org.oscar.kb.latin.utils.reorderDialog

class ToolbarSettingsFragment : SubScreenFragment() {
    private var reloadKeyboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_toolbar)

        findPreference<Preference>(Settings.PREF_TOOLBAR_KEYS)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                reorderDialog(
                    requireContext(), Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref,
                    R.string.toolbar_keys
                ) { getToolbarIconByName(it, requireContext()) }
                true
            }
        findPreference<Preference>(Settings.PREF_PINNED_TOOLBAR_KEYS)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                reorderDialog(
                    requireContext(), Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref,
                    R.string.pinned_toolbar_keys
                ) { getToolbarIconByName(it, requireContext()) }
                true
            }
        findPreference<Preference>(Settings.PREF_CLIPBOARD_TOOLBAR_KEYS)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                reorderDialog(
                    requireContext(),
                    Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref,
                    R.string.clipboard_toolbar_keys
                ) { getToolbarIconByName(it, requireContext()) }
                true
            }
    }

    override fun onPause() {
        super.onPause()
        if (reloadKeyboard)
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        reloadKeyboard = false
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String?) {
        if (key == null) return
        when (key) {
            Settings.PREF_TOOLBAR_KEYS, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, Settings.PREF_PINNED_TOOLBAR_KEYS,
            Settings.PREF_QUICK_PIN_TOOLBAR_KEYS -> reloadKeyboard = true
        }
    }
}
