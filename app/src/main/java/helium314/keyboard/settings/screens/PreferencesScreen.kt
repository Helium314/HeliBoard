// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.POPUP_KEYS_LABEL_DEFAULT
import helium314.keyboard.latin.utils.POPUP_KEYS_ORDER_DEFAULT
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getEnabledSubtypes
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.ListPreference
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.ReorderSwitchPreference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SliderPreference
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun PreferencesScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_preferences),
    ) {
        val prefs = LocalContext.current.prefs()
        val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        PreferenceCategory(stringResource(R.string.settings_category_input)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_HINTS]!!.Preference()
            if (prefs.getBoolean(Settings.PREF_SHOW_HINTS, true))
                SettingsActivity2.allPrefs.map[Settings.PREF_POPUP_KEYS_LABELS_ORDER]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_POPUP_KEYS_ORDER]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_POPUP_HINTS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_POPUP_ON]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_VIBRATE_ON]!!.Preference()
            if (prefs.getBoolean(Settings.PREF_VIBRATE_ON, true))
                SettingsActivity2.allPrefs.map[Settings.PREF_VIBRATION_DURATION_SETTINGS]!!.Preference()
            if (prefs.getBoolean(Settings.PREF_VIBRATE_ON, true))
                SettingsActivity2.allPrefs.map[Settings.PREF_VIBRATE_IN_DND_MODE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SOUND_ON]!!.Preference()
            if (prefs.getBoolean(Settings.PREF_SOUND_ON, true))
                SettingsActivity2.allPrefs.map[Settings.PREF_KEYPRESS_SOUND_VOLUME]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME]!!.Preference()
        }
        PreferenceCategory(stringResource(R.string.settings_category_additional_keys)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_NUMBER_ROW]!!.Preference()
            if (getEnabledSubtypes(prefs, true).any { it.locale().language in localesWithLocalizedNumberRow })
                SettingsActivity2.allPrefs.map[Settings.PREF_LOCALIZED_NUMBER_ROW]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_LANGUAGE_SWITCH_KEY]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_EMOJI_KEY]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_REMOVE_REDUNDANT_POPUPS]!!.Preference()
        }
        PreferenceCategory(stringResource(R.string.settings_category_clipboard_history)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_ENABLE_CLIPBOARD_HISTORY]!!.Preference()
            if (prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, true))
                SettingsActivity2.allPrefs.map[Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME]!!.Preference()
        }
    }
}

fun createPreferencesPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_SHOW_HINTS, R.string.show_hints, R.string.show_hints_summary) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_POPUP_KEYS_LABELS_ORDER, R.string.hint_source) {
        ReorderSwitchPreference(it, POPUP_KEYS_LABEL_DEFAULT)
    },
    PrefDef(context, Settings.PREF_POPUP_KEYS_ORDER, R.string.popup_order) {
        ReorderSwitchPreference(it, POPUP_KEYS_ORDER_DEFAULT)
    },
    PrefDef(context, Settings.PREF_SHOW_POPUP_HINTS, R.string.show_popup_hints, R.string.show_popup_hints_summary) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_POPUP_ON, R.string.popup_on_keypress) {
        val dm = LocalContext.current.resources.displayMetrics
        val px600 = with(LocalDensity.current) { 600.dp.toPx() }
        SwitchPreference(it, dm.widthPixels >= px600 || dm.heightPixels >= px600)
    },
    PrefDef(context, Settings.PREF_VIBRATE_ON, R.string.vibrate_on_keypress) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_VIBRATE_IN_DND_MODE, R.string.vibrate_in_dnd_mode) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_SOUND_ON, R.string.sound_on_keypress) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_ENABLE_CLIPBOARD_HISTORY, R.string.enable_clipboard_history, R.string.enable_clipboard_history_summary) {
        SwitchPreference(it, true)
    },
    PrefDef(context, Settings.PREF_SHOW_NUMBER_ROW, R.string.number_row, R.string.number_row_summary) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_LOCALIZED_NUMBER_ROW, R.string.localized_number_row, R.string.localized_number_row_summary) {
        SwitchPreference(it, true) { KeyboardLayoutSet.onSystemLocaleChanged() }
    },
    PrefDef(context, Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, R.string.show_language_switch_key) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_LANGUAGE_SWITCH_KEY, R.string.language_switch_key_behavior) {
        ListPreference(
            it,
            listOf(
                "internal" to stringResource(R.string.switch_language),
                "input_method" to stringResource(R.string.language_switch_key_switch_input_method),
                "both" to stringResource(R.string.language_switch_key_switch_both)
            ),
            "internal"
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_SHOW_EMOJI_KEY, R.string.show_emoji_key) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_REMOVE_REDUNDANT_POPUPS, R.string.remove_redundant_popups, R.string.remove_redundant_popups_summary) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, R.string.clipboard_history_retention_time) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = 10,
            description = {
                if (it < 0) stringResource(R.string.settings_no_limit)
                else stringResource(R.string.abbreviation_unit_minutes, it.toString())
            },
            range = -1f..120f,
        )
    },
    PrefDef(context, Settings.PREF_VIBRATION_DURATION_SETTINGS, R.string.prefs_keypress_vibration_duration_settings) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = -1,
            description = {
                if (it < 0) stringResource(R.string.settings_system_default)
                else stringResource(R.string.abbreviation_unit_milliseconds, it.toString())
            },
            range = -1f..100f,
            onValueChanged = { AudioAndHapticFeedbackManager.getInstance().vibrate(it.toLong()) }
        )
    },
    PrefDef(context, Settings.PREF_KEYPRESS_SOUND_VOLUME, R.string.prefs_keypress_sound_volume_settings) { def ->
        val audioManager = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = -0.01f,
            description = {
                if (it < 0) stringResource(R.string.settings_system_default)
                else (it * 100).toInt().toString()
            },
            range = -0.01f..1f,
            onValueChanged = { audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, it) }
        )
    },
)

// todo: not good to have it hardcoded, but reading a bunch of files may be noticeably slow
private val localesWithLocalizedNumberRow = listOf("ar", "bn", "fa", "gu", "hi", "kn", "mr", "ne", "ur")

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            PreferencesScreen { }
        }
    }
}
