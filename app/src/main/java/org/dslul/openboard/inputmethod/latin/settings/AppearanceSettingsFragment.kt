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
import android.os.Build
import android.os.Bundle
import android.preference.ListPreference
import android.preference.Preference
import android.preference.TwoStatePreference
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.define.ProductionFlags
import java.util.*

/**
 * "Appearance" settings sub screen.
 */
class AppearanceSettingsFragment : SubScreenFragment(), Preference.OnPreferenceChangeListener {

    private var selectedThemeId = 0

    private lateinit var themeFamilyPref: ListPreference
    private lateinit var themeVariantPref: ListPreference
    private lateinit var keyBordersPref: TwoStatePreference
    private lateinit var amoledModePref: TwoStatePreference
    private var dayNightPref: TwoStatePreference? = null
    private lateinit var userColorsPref: Preference


    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)
        addPreferencesFromResource(R.xml.prefs_screen_appearance)
        val keyboardTheme = KeyboardTheme.getKeyboardTheme(activity)
        selectedThemeId = keyboardTheme.mThemeId

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            removePreference(Settings.PREF_THEME_DAY_NIGHT)
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
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
        CustomInputStyleSettingsFragment.updateCustomInputStylesSummary(
                findPreference(Settings.PREF_CUSTOM_INPUT_STYLES))
    }

    override fun onPreferenceChange(preference: Preference, value: Any?): Boolean {
        (preference as? ListPreference)?.apply {
            summary = entries[entryValues.indexOfFirst { it == value }]
        }
        saveSelectedThemeId()
        return true
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
        keyBordersPref.apply {
            isEnabled = !isLegacyFamily && !KeyboardTheme.getIsAmoledMode(selectedThemeId)
            isChecked = isLegacyFamily || KeyboardTheme.getHasKeyBorders(selectedThemeId)
        }
        amoledModePref.apply {
            isEnabled = !isLegacyFamily && variant != KeyboardTheme.THEME_VARIANT_LIGHT
                    && !KeyboardTheme.getHasKeyBorders(selectedThemeId)
                    && !KeyboardTheme.getIsUser(selectedThemeId)
            isChecked = !isLegacyFamily && KeyboardTheme.getIsAmoledMode(selectedThemeId)
        }
        dayNightPref?.apply {
            isEnabled = !isLegacyFamily
            isChecked = !isLegacyFamily && KeyboardTheme.getIsDayNight(selectedThemeId)
        }
        userColorsPref.apply {
            isEnabled = KeyboardTheme.getIsUser(selectedThemeId)
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
        userColorsPref = preferenceScreen.findPreference(Settings.PREF_THEME_USER)
        userColorsPref.onPreferenceClickListener = Preference.OnPreferenceClickListener { _ ->
            val items = listOf(R.string.select_color_background, R.string.select_color_key, R.string.select_color_key_hint, R.string.select_color_accent, R.string.select_color_key_background)
                .map { activity.getString(it) }
            val itemsArray = if (keyBordersPref.isChecked) items.toTypedArray()
                else items.subList(0, 4).toTypedArray()
            AlertDialog.Builder(activity)
                .setPositiveButton(android.R.string.ok, null)
                .setTitle(R.string.select_color_to_adjust)
                .setItems(itemsArray) { _, i ->
                    val pref = when (i) {
                        0 -> Settings.PREF_THEME_USER_COLOR_BACKGROUND
                        1 -> Settings.PREF_THEME_USER_COLOR_TEXT
                        2 -> Settings.PREF_THEME_USER_COLOR_HINT_TEXT
                        3 -> Settings.PREF_THEME_USER_COLOR_ACCENT
                        else -> Settings.PREF_THEME_USER_COLOR_KEYS
                    }
                    val d = ColorPickerDialog(activity, items[i], sharedPreferences, pref)
                    d.show()
                }
                .show()
            true
        }
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