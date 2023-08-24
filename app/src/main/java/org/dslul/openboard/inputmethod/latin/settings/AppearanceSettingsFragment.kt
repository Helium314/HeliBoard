/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.dslul.openboard.inputmethod.latin.settings

import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.SharedPreferences.OnSharedPreferenceChangeListener
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.TwoStatePreference
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.define.ProductionFlags
import java.util.*

/**
 * "Appearance" settings sub screen.
 */
@Suppress("Deprecation") // yes everything here is deprecated, but only work on this if really necessary
// todo: simplify when removing old themes
class AppearanceSettingsFragment : SubScreenFragment(), Preference.OnPreferenceChangeListener, OnSharedPreferenceChangeListener {

    private var selectedThemeId = 0
    private var needsReload = false

    private lateinit var themeFamilyPref: ListPreference
    private lateinit var themeVariantPref: ListPreference
    private lateinit var customThemeVariantPref: ListPreference
    private lateinit var customThemeVariantNightPref: ListPreference
    private lateinit var keyBordersPref: TwoStatePreference
    private lateinit var amoledModePref: TwoStatePreference
    private var dayNightPref: TwoStatePreference? = null
    private lateinit var userColorsPref: Preference


    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)
        val keyboardTheme = KeyboardTheme.getKeyboardTheme(activity)
        selectedThemeId = keyboardTheme.mThemeId

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            removePreference(Settings.PREF_THEME_DAY_NIGHT)
            removePreference(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT)
        } else {
            // on P there is experimental support for night mode, exposed by some roms like LineageOS
            // try to detect this using UI_MODE_NIGHT_UNDEFINED, but actually the system could always report day too?
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                && (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_UNDEFINED
            ) {
                removePreference(Settings.PREF_THEME_DAY_NIGHT)
                removePreference(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT)
            }
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            // todo: consider removing the preference, and always set the navbar color
            removePreference(Settings.PREF_NAVBAR_COLOR)
        }
        setupTheme()

        if (!ProductionFlags.IS_SPLIT_KEYBOARD_SUPPORTED ||
                Constants.isPhone(Settings.readScreenMetrics(resources))) {
            removePreference(Settings.PREF_ENABLE_SPLIT_KEYBOARD)
        }
        setupKeyboardHeight(
                Settings.PREF_KEYBOARD_HEIGHT_SCALE, SettingsValues.DEFAULT_SIZE_SCALE)
    }

    override fun onResume() {
        super.onResume()
        updateThemePreferencesState()
        updateAfterPreferenceChanged()
    }

    override fun onPause() {
        super.onPause()
        if (needsReload)
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(activity)
        needsReload = false
    }

    override fun onPreferenceChange(preference: Preference, value: Any?): Boolean {
        (preference as? ListPreference)?.apply {
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        saveSelectedThemeId()
        return true
    }

    override fun onSharedPreferenceChanged(prefs: SharedPreferences, key: String) {
        super.onSharedPreferenceChanged(prefs, key)
        updateAfterPreferenceChanged()
    }

    // doing things on changing, but with the old values is not good, this is at least a little better
    private fun updateAfterPreferenceChanged() {
        customThemeVariantNightPref.apply {
            if (KeyboardTheme.getIsCustom(selectedThemeId)) {
                // show preference to allow choosing a night theme
                // can't hide a preference, at least not without category or maybe some androidx things
                // -> just disable it instead (for now...)
                isEnabled = sharedPreferences.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
            } else
                isEnabled = false

            val variant = sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT, KeyboardTheme.THEME_DARKER)
            val variants = KeyboardTheme.CUSTOM_THEME_VARIANTS_DARK
            entries = variants.map {
                // todo: this workaround get the same string as for "user" theme, maybe clarify that it's a separate theme
                val name = if (it == "user_dark") "theme_name_user" else "theme_name_$it"
                val resId = resources.getIdentifier(name, "string", activity.packageName)
                if (resId == 0) it else getString(resId)
            }.toTypedArray()
            entryValues = variants
            value = variant
            val name = if (variant == "user_dark") "theme_name_user" else "theme_name_$variant"
            val resId = resources.getIdentifier(name, "string", activity.packageName)
            summary = if (resId == 0) variant else getString(resId)
        }
        userColorsPref.apply {
            isEnabled = KeyboardTheme.getIsCustom(selectedThemeId)
                    && (sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT, KeyboardTheme.THEME_LIGHT) == KeyboardTheme.THEME_USER
                        || (sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT, KeyboardTheme.THEME_DARKER) == KeyboardTheme.THEME_USER_DARK
                            && sharedPreferences.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false)
                    ))
        }
    }

    private fun saveSelectedThemeId(
            family: String = themeFamilyPref.value,
            variant: String = themeVariantPref.value,
            keyBorders: Boolean = keyBordersPref.isChecked,
            dayNight: Boolean = dayNightPref?.isChecked ?: false,
            amoledMode: Boolean = amoledModePref.isChecked
    ) {
        selectedThemeId = KeyboardTheme.getThemeForParameters(family, variant, keyBorders, dayNight, amoledMode)
        KeyboardTheme.saveKeyboardThemeId(selectedThemeId, sharedPreferences)
    }

    private fun updateThemePreferencesState(skipThemeFamily: Boolean = false, skipThemeVariant: Boolean = false) {
        val themeFamily = KeyboardTheme.getThemeFamily(selectedThemeId)
        val isLegacyFamily = KeyboardTheme.THEME_FAMILY_HOLO == themeFamily
        if (!skipThemeFamily) {
            themeFamilyPref.apply {
                value = themeFamily
                summary = themeFamily
            }
        }
        val variants = KeyboardTheme.THEME_VARIANTS[themeFamily]!!
        val variant = KeyboardTheme.getThemeVariant(selectedThemeId)
        if (!skipThemeVariant) {
            themeVariantPref.apply {
                entries = variants
                entryValues = variants
                value = variant ?: variants[0]
                summary = variant ?: "Auto"
                isEnabled = isLegacyFamily || !KeyboardTheme.getIsDayNight(selectedThemeId)
            }
        }
        customThemeVariantPref.apply {
            val variant = sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT, KeyboardTheme.THEME_LIGHT)
            // todo (idea): re-work setting to actually see preview of theme colors... but that's a lot of work
            val variants = KeyboardTheme.CUSTOM_THEME_VARIANTS
            entries = variants.map {
                val resId = resources.getIdentifier("theme_name_$it", "string", activity.packageName)
                if (resId == 0) it else getString(resId)
            }.toTypedArray()
            entryValues = variants
            value = variant
            val resId = resources.getIdentifier("theme_name_$variant", "string", activity.packageName)
            summary = if (resId == 0) variant else getString(resId)
            isEnabled = true
        }
        keyBordersPref.apply {
            isEnabled = !isLegacyFamily && !KeyboardTheme.getIsAmoledMode(selectedThemeId)
            isChecked = isLegacyFamily || KeyboardTheme.getHasKeyBorders(selectedThemeId)
        }
        amoledModePref.apply {
            isEnabled = !isLegacyFamily && variant != KeyboardTheme.THEME_VARIANT_LIGHT
                    && !KeyboardTheme.getHasKeyBorders(selectedThemeId)
                    && !KeyboardTheme.getIsCustom(selectedThemeId)
            isChecked = !isLegacyFamily && KeyboardTheme.getIsAmoledMode(selectedThemeId)
        }
        dayNightPref?.apply {
            isEnabled = !isLegacyFamily
            isChecked = !isLegacyFamily && (KeyboardTheme.getIsDayNight(selectedThemeId)
                    || (KeyboardTheme.getIsCustom(selectedThemeId) && sharedPreferences.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false))
                    )
        }
    }

    private fun setupTheme() {
        themeFamilyPref = preferenceScreen.findPreference(Settings.PREF_THEME_FAMILY) as ListPreference
        themeFamilyPref.apply {
            entries = KeyboardTheme.THEME_FAMILIES
            entryValues = KeyboardTheme.THEME_FAMILIES
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                saveSelectedThemeId(family = value as String)
                updateThemePreferencesState(skipThemeFamily = true)
                true
            }
        }
        themeVariantPref = preferenceScreen.findPreference(Settings.PREF_THEME_VARIANT) as ListPreference
        themeVariantPref.apply {
            title = "$title old (to be removed)" // todo: remove, this is just a workaround while there are still 2 ways of selecting variant
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                summary = entries[entryValues.indexOfFirst { it == value }]
                saveSelectedThemeId(variant = value as String)
                updateThemePreferencesState(skipThemeFamily = true, skipThemeVariant = true)
                true
            }
        }
        keyBordersPref = preferenceScreen.findPreference(Settings.PREF_THEME_KEY_BORDERS) as TwoStatePreference
        keyBordersPref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            saveSelectedThemeId(keyBorders = value as Boolean)
            updateThemePreferencesState(skipThemeFamily = true)
            true
        }
        amoledModePref = preferenceScreen.findPreference(Settings.PREF_THEME_AMOLED_MODE) as TwoStatePreference
        amoledModePref.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            saveSelectedThemeId(amoledMode = value as Boolean)
            updateThemePreferencesState(skipThemeFamily = true)
            true
        }
        dayNightPref = preferenceScreen.findPreference(Settings.PREF_THEME_DAY_NIGHT) as? TwoStatePreference
        dayNightPref?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
            saveSelectedThemeId(dayNight = value as Boolean)
            updateThemePreferencesState(skipThemeFamily = true)
            true
        }
        customThemeVariantPref = preferenceScreen.findPreference(Settings.PREF_CUSTOM_THEME_VARIANT) as ListPreference
        customThemeVariantPref.apply {
            title = "$title new" // todo: remove, this is just a workaround while there are still 2 ways of selecting variant
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                // not so nice workaround, could be removed in the necessary re-work: new value seems
                // to be stored only after this method call, but we update the summary and user-defined color enablement in here -> store it now
                if (value == sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT, KeyboardTheme.THEME_LIGHT))
                    return@OnPreferenceChangeListener true // avoid infinite loop
                sharedPreferences.edit { putString(Settings.PREF_CUSTOM_THEME_VARIANT, value as String) }

                summary = entries[entryValues.indexOfFirst { it == value }]
                needsReload = true

                // always switch to user-defined theme variant
                val themeFamily = KeyboardTheme.getThemeFamily(selectedThemeId)
                val variants = KeyboardTheme.THEME_VARIANTS[themeFamily]!!
                val userVariant = variants.first { it.contains("user", true) }
                saveSelectedThemeId(variant = userVariant as String)
                updateThemePreferencesState(skipThemeFamily = true)

                true
            }
        }
        customThemeVariantNightPref = preferenceScreen.findPreference(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT) as ListPreference
        customThemeVariantNightPref.apply {
            title = "$title new (night)" // todo: remove, this is just a workaround while there are still 2 ways of selecting variant
            onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, value ->
                // not so nice workaround, could be removed in the necessary re-work: new value seems
                // to be stored only after this method call, but we update the summary and user-defined color enablement in here -> store it now
                if (value == sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT, KeyboardTheme.THEME_DARK))
                    return@OnPreferenceChangeListener true // avoid infinite loop
                sharedPreferences.edit { putString(Settings.PREF_CUSTOM_THEME_VARIANT_NIGHT, value as String) }

                summary = entries[entryValues.indexOfFirst { it == value }]
                needsReload = true

                true
            }
        }
        userColorsPref = preferenceScreen.findPreference(Settings.PREF_THEME_USER)
        userColorsPref.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
            if (sharedPreferences.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false) && sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT, KeyboardTheme.THEME_LIGHT) == KeyboardTheme.THEME_USER)
                AlertDialog.Builder(activity)
                    .setMessage(R.string.day_or_night_colors)
                    .setPositiveButton(R.string.day_or_night_night) { _, _ -> adjustColors(true)}
                    .setNegativeButton(R.string.day_or_night_day) { _, _ -> adjustColors(false)}
                    .show()
            else if (sharedPreferences.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false)) // only night theme custom
                adjustColors(true)
            else // customize day theme
                adjustColors(false)
            true
        }
        preferenceScreen.findPreference(Settings.PREF_NARROW_KEY_GAPS)?.onPreferenceChangeListener = Preference.OnPreferenceChangeListener { _, _ ->
            needsReload = true
            true
        }
    }

    private fun adjustColors(dark: Boolean) {
        val items = listOf(R.string.select_color_background, R.string.select_color_key, R.string.select_color_key_hint, R.string.select_color_accent, R.string.select_color_key_background)
            .map { activity.getString(it) }
        val itemsArray = if (keyBordersPref.isChecked) items.toTypedArray()
        else items.subList(0, 4).toTypedArray()
        AlertDialog.Builder(activity)
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
                val d = ColorPickerDialog(activity, items[i], sharedPreferences, pref, default) { needsReload = true}
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