// SPDX-License-Identifier: GPL-3.0-only

package org.oscar.kb.latin.settings

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.util.TypedValueCompat
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.TwoStatePreference
import org.oscar.kb.R
import org.oscar.kb.keyboard.KeyboardSwitcher
import org.oscar.kb.keyboard.KeyboardTheme
import org.oscar.kb.latin.common.FileUtils
import org.oscar.kb.latin.settings.SeekBarDialogPreference
import org.oscar.kb.latin.settings.Settings
import org.oscar.kb.latin.settings.SettingsValues
import org.oscar.kb.latin.settings.SubScreenFragment
import org.oscar.kb.latin.utils.getStringResourceOrName
import org.oscar.kb.latin.utils.infoDialog
import java.lang.Float.max
import java.lang.Float.min
import java.util.*

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment() {
    private var needsReload = false

    private val stylePref: ListPreference by lazy { preferenceScreen.findPreference(
        Settings.PREF_THEME_STYLE)!! }
    private val colorsPref: ListPreference by lazy { preferenceScreen.findPreference(
        Settings.PREF_THEME_COLORS)!! }
    private val colorsNightPref: ListPreference? by lazy { preferenceScreen.findPreference(
        Settings.PREF_THEME_COLORS_NIGHT) }
    private val dayNightPref: TwoStatePreference? by lazy { preferenceScreen.findPreference(
        Settings.PREF_THEME_DAY_NIGHT) }
    private val userColorsPref: Preference by lazy { preferenceScreen.findPreference("theme_select_colors")!! }
    private val userColorsPrefNight: Preference? by lazy { preferenceScreen.findPreference("theme_select_colors_night") }
    private val splitPref: TwoStatePreference? by lazy { preferenceScreen.findPreference(
        Settings.PREF_ENABLE_SPLIT_KEYBOARD) }
    private val splitScalePref: Preference? by lazy { preferenceScreen.findPreference(
        Settings.PREF_SPLIT_SPACER_SCALE) }

    private val dayImageFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        loadImage(uri, false)
    }

    private val nightImageFilePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (it.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        val uri = it.data?.data ?: return@registerForActivityResult
        loadImage(uri, true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)

        removeUnsuitablePreferences()
        setupTheme()
        setColorPrefs(sharedPreferences.getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)!!)

        setupScalePrefs(Settings.PREF_KEYBOARD_HEIGHT_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
        setupScalePrefs(Settings.PREF_BOTTOM_PADDING_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
        if (splitScalePref != null) {
            setupScalePrefs(Settings.PREF_SPLIT_SPACER_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
            splitScalePref?.isVisible = splitPref?.isChecked == true
            splitPref?.setOnPreferenceChangeListener { _, value ->
                splitScalePref?.isVisible = value as Boolean
                true
            }
        }
        findPreference<Preference>("custom_background_image")?.setOnPreferenceClickListener { onClickLoadImage() }
    }

    override fun onPause() {
        super.onPause()
        if (needsReload)
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
        needsReload = false
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(prefs, key)
        needsReload = true // may not always necessary, but that's ok
    }

    private fun removeUnsuitablePreferences() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference(Settings.PREF_THEME_DAY_NIGHT)
            removePreference(Settings.PREF_THEME_COLORS_NIGHT)
        } else {
            // on P there is experimental support for night mode, exposed by some roms like LineageOS
            // try to detect this using UI_MODE_NIGHT_UNDEFINED, but actually the system could always report day too?
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_UNDEFINED
            ) {
                removePreference(Settings.PREF_THEME_DAY_NIGHT)
                removePreference(Settings.PREF_THEME_COLORS_NIGHT)
                removePreference("theme_select_colors_night")
            }
        }
        val metrics = requireContext().resources.displayMetrics
        val widthDp = TypedValueCompat.pxToDp(metrics.widthPixels.toFloat(), metrics)
        val heightDp = TypedValueCompat.pxToDp(metrics.heightPixels.toFloat(), metrics)
        if ((min(widthDp, heightDp) < 600 && max(widthDp, heightDp) < 720)) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD)
            removePreference(Settings.PREF_SPLIT_SPACER_SCALE)
        }
    }

    private fun setColorPrefs(style: String) {
        colorsPref.apply {
            entryValues = if (style == KeyboardTheme.STYLE_HOLO) KeyboardTheme.COLORS.toTypedArray()
                else KeyboardTheme.COLORS.filterNot { it == KeyboardTheme.THEME_HOLO_WHITE }.toTypedArray()
            entries = entryValues.getNamesFromResourcesIfAvailable("theme_name_")
            if (value !in entryValues)
                value = entryValues.first().toString()
            summary = entries[entryValues.indexOfFirst { it == value }]

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                userColorsPref.isVisible = value == KeyboardTheme.THEME_USER
                true
            }
        }
        colorsNightPref?.apply {
            entryValues = if (style == KeyboardTheme.STYLE_HOLO) KeyboardTheme.COLORS_DARK.toTypedArray()
                else KeyboardTheme.COLORS_DARK.filterNot { it == KeyboardTheme.THEME_HOLO_WHITE }.toTypedArray()
            entries = entryValues.getNamesFromResourcesIfAvailable("theme_name_")
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
        stylePref.apply {
            entryValues = KeyboardTheme.STYLES
            entries = entryValues.getNamesFromResourcesIfAvailable("style_name_")
            if (value !in entryValues)
                value = entryValues.first().toString()

            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                setColorPrefs(value.toString())
                true
            }
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        dayNightPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            val yesThisIsBoolean = value as Boolean // apparently kotlin smartcast got less smart with 2.0.0
            colorsNightPref?.isVisible = yesThisIsBoolean
            userColorsPrefNight?.isVisible = yesThisIsBoolean && colorsNightPref?.value == KeyboardTheme.THEME_USER_NIGHT
            true
        }
        colorsNightPref?.isVisible = dayNightPref?.isChecked == true
        userColorsPref.isVisible = colorsPref.value == KeyboardTheme.THEME_USER
        userColorsPrefNight?.isVisible = dayNightPref?.isChecked == true && colorsNightPref?.value == KeyboardTheme.THEME_USER_NIGHT
    }

    private fun onClickLoadImage(): Boolean {
        if (Settings.readDayNightPref(sharedPreferences, resources)) {
            AlertDialog.Builder(requireContext())
                .setMessage(R.string.day_or_night_image)
                .setPositiveButton(R.string.day_or_night_day) { _, _ -> customImageDialog(false) }
                .setNegativeButton(R.string.day_or_night_night) { _, _ -> customImageDialog(true) }
                .setNeutralButton(android.R.string.cancel, null)
                .show()
        } else {
            customImageDialog(false)
        }
        return true
    }

    private fun customImageDialog(night: Boolean) {
        val imageFile = Settings.getCustomBackgroundFile(requireContext(), night)
        val builder = AlertDialog.Builder(requireContext())
            .setMessage(R.string.customize_background_image)
            .setPositiveButton(R.string.button_load_custom) { _, _ ->
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("image/*")
                if (night) nightImageFilePicker.launch(intent)
                else dayImageFilePicker.launch(intent)
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (imageFile.exists()) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                imageFile.delete()
                Settings.clearCachedBackgroundImages()
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
            }
        }
        builder.show()
    }

    private fun loadImage(uri: Uri, night: Boolean) {
        val imageFile = Settings.getCustomBackgroundFile(requireContext(), night)
        FileUtils.copyContentUriToNewFile(uri, requireContext(), imageFile)
        try {
            BitmapFactory.decodeFile(imageFile.absolutePath)
        } catch (_: Exception) {
            infoDialog(requireContext(), R.string.file_read_error)
            imageFile.delete()
        }
        Settings.clearCachedBackgroundImages()
        KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext())
    }

    private fun setupScalePrefs(prefKey: String, defaultValue: Float) {
        val prefs = sharedPreferences
        val pref = findPreference(prefKey) as? SeekBarDialogPreference
        pref?.setInterface(object : SeekBarDialogPreference.ValueProxy {

            private fun getValueFromPercentage(percentage: Int) =  percentage / PERCENTAGE_FLOAT

            private fun getPercentageFromValue(floatValue: Float) = (floatValue * PERCENTAGE_FLOAT).toInt()

            override fun writeValue(value: Int, key: String) = prefs.edit().putFloat(key, getValueFromPercentage(value)).apply()

            override fun writeDefaultValue(key: String) = prefs.edit().remove(key).apply()

            override fun readValue(key: String) = getPercentageFromValue(prefs.getFloat(prefKey, defaultValue))

            override fun readDefaultValue(key: String) = getPercentageFromValue(defaultValue)

            override fun getValueText(value: Int) = String.format(Locale.ROOT, "%d%%", value)

            override fun feedbackValue(value: Int) = Unit
        })
    }

    private fun Array<CharSequence>.getNamesFromResourcesIfAvailable(prefix: String) =
        map { it.getStringResourceOrName(prefix, requireContext()) }.toTypedArray()

    companion object {
        private const val PERCENTAGE_FLOAT = 100.0f
    }
}
