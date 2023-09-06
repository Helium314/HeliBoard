package org.dslul.openboard.inputmethod.latin.settings

import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.define.ProductionFlags
import java.lang.Float.max
import java.lang.Float.min
import java.util.*

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment() {

    private var needsReload = false

    private val themeFamilyPref: ListPreference by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_STYLE)!! }
    private val themeVariantPref: ListPreference by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_VARIANT)!! }
    private val themeVariantNightPref: ListPreference? by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_VARIANT_NIGHT) }
    private val dayNightPref: TwoStatePreference? by lazy { preferenceScreen.findPreference(Settings.PREF_THEME_DAY_NIGHT) }
    private val userColorsPref: Preference by lazy { preferenceScreen.findPreference("theme_select_colors")!! }
    private val userColorsPrefNight: Preference? by lazy { preferenceScreen.findPreference("theme_select_colors_night") }


    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)

        removeUnsuitablePreferences()
        setupTheme()
        setThemeVariantPrefs(sharedPreferences.getString(Settings.PREF_THEME_STYLE, KeyboardTheme.THEME_STYLE_MATERIAL)!!)

        setupKeyboardHeight(Settings.PREF_KEYBOARD_HEIGHT_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
    }

    override fun onPause() {
        super.onPause()
        if (needsReload)
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        needsReload = false
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        super.onSharedPreferenceChanged(prefs, key)
        needsReload = true // may not always be the necessary, but that's ok
    }

    private fun removeUnsuitablePreferences() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference(Settings.PREF_THEME_DAY_NIGHT)
            removePreference(Settings.PREF_THEME_VARIANT_NIGHT)
        } else {
            // on P there is experimental support for night mode, exposed by some roms like LineageOS
            // try to detect this using UI_MODE_NIGHT_UNDEFINED, but actually the system could always report day too?
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_UNDEFINED
            ) {
                removePreference(Settings.PREF_THEME_DAY_NIGHT)
                removePreference(Settings.PREF_THEME_VARIANT_NIGHT)
                removePreference("theme_select_colors_night")
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // todo: consider removing the preference, and always set the navbar color
            removePreference(Settings.PREF_NAVBAR_COLOR)
        }
        val metrics = requireContext().resources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        val heightDp = metrics.heightPixels / metrics.density
        if (!ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED || (min(widthDp, heightDp) < 600 && max(widthDp, heightDp) < 720)) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD)
        }
    }

    private fun setThemeVariantPrefs(themeFamily: String) {
        themeVariantPref.apply {
            entryValues = if (themeFamily == KeyboardTheme.THEME_STYLE_HOLO) KeyboardTheme.THEME_VARIANTS
                else KeyboardTheme.THEME_VARIANTS.filterNot { it == KeyboardTheme.THEME_HOLO_WHITE }.toTypedArray()
            entries = entryValues.map {
                val resId = resources.getIdentifier("theme_name_$it", "string", requireContext().packageName)
                if (resId == 0) it else getString(resId)
            }.toTypedArray()
            if (value !in entryValues)
                value = entryValues.first().toString()
            summary = entries[entryValues.indexOfFirst { it == value }]

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                userColorsPref.isVisible = value == KeyboardTheme.THEME_USER
                true
            }
        }
        themeVariantNightPref?.apply {
            entryValues = if (themeFamily == KeyboardTheme.THEME_STYLE_HOLO) KeyboardTheme.THEME_VARIANTS_DARK
                else KeyboardTheme.THEME_VARIANTS_DARK.filterNot { it == KeyboardTheme.THEME_HOLO_WHITE }.toTypedArray()
            entries = entryValues.map {
                val resId = resources.getIdentifier("theme_name_$it", "string", requireContext().packageName)
                if (resId == 0) it else getString(resId)
            }.toTypedArray()
            if (value !in entryValues)
                value = entryValues.first().toString()
            summary = entries[entryValues.indexOfFirst { it == value }]

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                userColorsPrefNight?.isVisible = value == KeyboardTheme.THEME_USER_NIGHT
                true
            }
        }
    }

    private fun setupTheme() {
        themeFamilyPref.apply {
            entries = KeyboardTheme.THEME_STYLES
            entryValues = KeyboardTheme.THEME_STYLES
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                setThemeVariantPrefs(value.toString())
                true
            }
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        dayNightPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            themeVariantNightPref?.isVisible = value as Boolean
            userColorsPrefNight?.isVisible = value && themeVariantNightPref?.value == KeyboardTheme.THEME_USER_NIGHT
            true
        }
        themeVariantNightPref?.isVisible = dayNightPref?.isChecked == true
        userColorsPref.isVisible = themeVariantPref.value == KeyboardTheme.THEME_USER
        userColorsPref.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            adjustColors(false)
            true
        }
        userColorsPrefNight?.isVisible = dayNightPref?.isChecked == true && themeVariantNightPref?.value == KeyboardTheme.THEME_USER_NIGHT
        userColorsPrefNight?.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            adjustColors(true)
            true
        }
    }

    // todo: improve color selection, should at very least show a preview of the color
    //  but maybe a separate fragment would be better
    // idea:
    //  left: which color (background, key, text,...)
    //  right: color preview (always the correct one, even if determined automatically)
    //   maybe copy parts from simple keyboard, see e.g. screenshot 4 in https://github.com/SimpleMobileTools/Simple-Keyboard/tree/main/fastlane/metadata/android/en-US/images/phoneScreenshots
    //  below (for some colors, with indent):
    //   enable user-defining (most colors, but definitely not background)
    //   use system accent (for accent and text colors)
    //  on click: color selector
    //   maybe copy parts from simple keyboard, see e.g. screenshot 4 in https://github.com/SimpleMobileTools/Simple-Keyboard/tree/main/fastlane/metadata/android/en-US/images/phoneScreenshots
    //    but full range would be preferable
    //   use some color picker library? would likely allow nicer tuning
    private fun adjustColors(dark: Boolean) {
        val items = listOf(
            R.string.select_color_background,
            R.string.select_color_key,
            R.string.select_color_key_hint,
            R.string.select_color_accent,
            R.string.select_color_key_background
        ).map { requireContext().getString(it) }
        val itemsArray = if (findPreference<TwoStatePreference>(Settings.PREF_THEME_KEY_BORDERS)!!.isChecked) items.toTypedArray()
            else items.subList(0, 4).toTypedArray()
        AlertDialog.Builder(requireContext())
            .setPositiveButton(android.R.string.ok, null)
            .setTitle(R.string.select_color_to_adjust)
            .setItems(itemsArray) { _, i ->
                val (pref, default) =
                    if (dark)
                        when (i) {
                            0 -> Settings.PREF_THEME_USER_DARK_COLOR_BACKGROUND to Color.DKGRAY
                            1 -> Settings.PREF_THEME_USER_DARK_COLOR_TEXT to Color.WHITE
                            2 -> Settings.PREF_THEME_USER_DARK_COLOR_HINT_TEXT to Color.WHITE
                            3 -> Settings.PREF_THEME_USER_DARK_COLOR_ACCENT to Color.BLUE
                            else -> Settings.PREF_THEME_USER_DARK_COLOR_KEYS to Color.LTGRAY
                        }
                    else
                        when (i) {
                            0 -> Settings.PREF_THEME_USER_COLOR_BACKGROUND to Color.DKGRAY
                            1 -> Settings.PREF_THEME_USER_COLOR_TEXT to Color.WHITE
                            2 -> Settings.PREF_THEME_USER_COLOR_HINT_TEXT to Color.WHITE
                            3 -> Settings.PREF_THEME_USER_COLOR_ACCENT to Color.BLUE
                            else -> Settings.PREF_THEME_USER_COLOR_KEYS to Color.LTGRAY
                        }
                val d = ColorPickerDialog(requireContext(), items[i], sharedPreferences, pref, default)
                d.show()
            }
            .show()
    }

    private fun setupKeyboardHeight(prefKey: String, defaultValue: Float) {
        val prefs = sharedPreferences
        val pref = findPreference(prefKey) as? SeekBarDialogPreference
        pref?.setInterface(object : SeekBarDialogPreference.ValueProxy {

            private fun getValueFromPercentage(percentage: Int) =  percentage / PERCENTAGE_FLOAT

            private fun getPercentageFromValue(floatValue: Float) = (floatValue * PERCENTAGE_FLOAT).toInt()

            override fun writeValue(value: Int, key: String) = prefs.edit()
                    .putFloat(key, getValueFromPercentage(value)).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = getPercentageFromValue(
                    Settings.readKeyboardHeight(prefs, defaultValue))

            override fun readDefaultValue(key: String) = getPercentageFromValue(defaultValue)

            override fun getValueText(value: Int) = String.format(Locale.ROOT, "%d%%", value)

            override fun feedbackValue(value: Int) = Unit
        })
    }

    companion object {
        private const val PERCENTAGE_FLOAT = 100.0f
    }
}
