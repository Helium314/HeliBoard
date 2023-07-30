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
class AppearanceSettingsFragment : SubScreenFragment(), Preference.OnPreferenceChangeListener {

    private var selectedThemeId = 0
    private var needsReload = false

    private lateinit var themeFamilyPref: ListPreference
    private lateinit var themeVariantPref: ListPreference
    private lateinit var customThemeVariantPref: ListPreference
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
        CustomInputStyleSettingsFragment.updateCustomInputStylesSummary(
                findPreference(Settings.PREF_CUSTOM_INPUT_STYLES))
    }

    override fun onPause() {
        super.onPause()
        if (needsReload)
            // todo: is this the correct "displayContext? if not it may cause weird rare issues on some android versions
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
            // todo: some way of following system, with setting a dark and a light theme
            //  check whether night mode is active using context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK
            //  this is needed so the auto-theme can be replaced
            //  idea: add a "dark theme variant" preference when auto-switch is on
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
            isEnabled = !isLegacyFamily && !KeyboardTheme.getIsCustom(selectedThemeId)
            isChecked = !isLegacyFamily && !KeyboardTheme.getIsCustom(selectedThemeId) && KeyboardTheme.getIsDayNight(selectedThemeId)
        }
        userColorsPref.apply {
            isEnabled = KeyboardTheme.getIsCustom(selectedThemeId) && sharedPreferences.getString(Settings.PREF_CUSTOM_THEME_VARIANT, KeyboardTheme.THEME_LIGHT) == KeyboardTheme.THEME_USER
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
                    val (pref, default) = when (i) {
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