package helium314.keyboard.latin.settings

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.Preference
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.defaultClipboardToolbarPref
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.reorderDialog

class ToolbarSettingsFragment : SubScreenFragment() {
    private var reloadKeyboard = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val iconsSet = KeyboardIconsSet.instance
        iconsSet.loadIcons(requireContext())
        addPreferencesFromResource(R.xml.prefs_screen_toolbar)

        findPreference<Preference>(Settings.PREF_TOOLBAR_KEYS)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                reorderDialog(
                    requireContext(), Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref,
                    R.string.toolbar_keys
                ) { iconsSet.getNewDrawable(it, requireContext()) }
                true
            }
        findPreference<Preference>(Settings.PREF_PINNED_TOOLBAR_KEYS)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                reorderDialog(
                    requireContext(), Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref,
                    R.string.pinned_toolbar_keys
                ) { iconsSet.getNewDrawable(it, requireContext()) }
                true
            }
        findPreference<Preference>(Settings.PREF_CLIPBOARD_TOOLBAR_KEYS)?.onPreferenceClickListener =
            Preference.OnPreferenceClickListener {
                reorderDialog(
                    requireContext(), Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref,
                    R.string.clipboard_toolbar_keys
                ) { iconsSet.getNewDrawable(it, requireContext()) }
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
