// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.ReorderSwitchPreference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun PreferencesScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val items = listOf(
        R.string.settings_category_input,
        Settings.PREF_SHOW_HINTS,
        if (prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS))
            Settings.PREF_POPUP_KEYS_LABELS_ORDER else null,
        Settings.PREF_POPUP_KEYS_ORDER,
        Settings.PREF_SHOW_POPUP_HINTS,
        Settings.PREF_POPUP_ON,
        if (AudioAndHapticFeedbackManager.getInstance().hasVibrator())
            Settings.PREF_VIBRATE_ON else null,
        if (prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON))
            Settings.PREF_VIBRATION_DURATION_SETTINGS else null,
        if (prefs.getBoolean(Settings.PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON))
            Settings.PREF_VIBRATE_IN_DND_MODE else null,
        Settings.PREF_SOUND_ON,
        if (prefs.getBoolean(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON))
            Settings.PREF_KEYPRESS_SOUND_VOLUME else null,
        R.string.settings_category_additional_keys,
        Settings.PREF_SHOW_NUMBER_ROW,
        if (SubtypeSettings.getEnabledSubtypes(prefs, true).any { it.locale().language in localesWithLocalizedNumberRow })
            Settings.PREF_LOCALIZED_NUMBER_ROW else null,
        if (prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS)
            && prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW))
            Settings.PREF_SHOW_NUMBER_ROW_HINTS else null,
        Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY,
        Settings.PREF_LANGUAGE_SWITCH_KEY,
        Settings.PREF_SHOW_EMOJI_KEY,
        Settings.PREF_REMOVE_REDUNDANT_POPUPS,
        R.string.settings_category_clipboard_history,
        Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
        if (prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY))
            Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME else null
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_preferences),
        settings = items
    )
}

fun createPreferencesSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_SHOW_HINTS, R.string.show_hints, R.string.show_hints_summary) {
        SwitchPreference(it, Defaults.PREF_SHOW_HINTS)
    },
    Setting(context, Settings.PREF_POPUP_KEYS_LABELS_ORDER, R.string.hint_source) {
        ReorderSwitchPreference(it, Defaults.PREF_POPUP_KEYS_LABELS_ORDER)
    },
    Setting(context, Settings.PREF_POPUP_KEYS_ORDER, R.string.popup_order) {
        ReorderSwitchPreference(it, Defaults.PREF_POPUP_KEYS_ORDER)
    },
    Setting(context, Settings.PREF_SHOW_POPUP_HINTS, R.string.show_popup_hints, R.string.show_popup_hints_summary) {
        SwitchPreference(it, Defaults.PREF_SHOW_POPUP_HINTS) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_POPUP_ON, R.string.popup_on_keypress) {
        SwitchPreference(it, Defaults.PREF_POPUP_ON)
    },
    Setting(context, Settings.PREF_VIBRATE_ON, R.string.vibrate_on_keypress) {
        SwitchPreference(it, Defaults.PREF_VIBRATE_ON)
    },
    Setting(context, Settings.PREF_VIBRATE_IN_DND_MODE, R.string.vibrate_in_dnd_mode) {
        SwitchPreference(it, Defaults.PREF_VIBRATE_IN_DND_MODE)
    },
    Setting(context, Settings.PREF_SOUND_ON, R.string.sound_on_keypress) {
        SwitchPreference(it, Defaults.PREF_SOUND_ON)
    },
    Setting(context, Settings.PREF_ENABLE_CLIPBOARD_HISTORY,
        R.string.enable_clipboard_history, R.string.enable_clipboard_history_summary)
    {
        SwitchPreference(it, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY)
    },
    Setting(context, Settings.PREF_SHOW_NUMBER_ROW, R.string.number_row, R.string.number_row_summary) {
        SwitchPreference(it, Defaults.PREF_SHOW_NUMBER_ROW) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_LOCALIZED_NUMBER_ROW, R.string.localized_number_row, R.string.localized_number_row_summary) {
        SwitchPreference(it, Defaults.PREF_LOCALIZED_NUMBER_ROW) { KeyboardLayoutSet.onSystemLocaleChanged() }
    },
    Setting(context, Settings.PREF_SHOW_NUMBER_ROW_HINTS, R.string.number_row_hints) {
        SwitchPreference(it, Defaults.PREF_SHOW_NUMBER_ROW_HINTS) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, R.string.show_language_switch_key) {
        SwitchPreference(it, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_LANGUAGE_SWITCH_KEY, R.string.language_switch_key_behavior) {
        ListPreference(
            it,
            listOf(
                "internal" to stringResource(R.string.switch_language),
                "input_method" to stringResource(R.string.language_switch_key_switch_input_method),
                "both" to stringResource(R.string.language_switch_key_switch_both)
            ),
            Defaults.PREF_LANGUAGE_SWITCH_KEY
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_SHOW_EMOJI_KEY, R.string.show_emoji_key) {
        SwitchPreference(it, Defaults.PREF_SHOW_EMOJI_KEY)
    },
    Setting(context, Settings.PREF_REMOVE_REDUNDANT_POPUPS,
        R.string.remove_redundant_popups, R.string.remove_redundant_popups_summary)
    {
        SwitchPreference(it, Defaults.PREF_REMOVE_REDUNDANT_POPUPS) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, R.string.clipboard_history_retention_time) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME,
            description = {
                if (it < 0) stringResource(R.string.settings_no_limit)
                else stringResource(R.string.abbreviation_unit_minutes, it.toString())
            },
            range = -1f..120f,
        )
    },
    Setting(context, Settings.PREF_VIBRATION_DURATION_SETTINGS, R.string.prefs_keypress_vibration_duration_settings) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_VIBRATION_DURATION_SETTINGS,
            description = {
                if (it < 0) stringResource(R.string.settings_system_default)
                else stringResource(R.string.abbreviation_unit_milliseconds, it.toString())
            },
            range = -1f..100f,
            onValueChanged = { AudioAndHapticFeedbackManager.getInstance().vibrate(it.toLong()) }
        )
    },
    Setting(context, Settings.PREF_KEYPRESS_SOUND_VOLUME, R.string.prefs_keypress_sound_volume_settings) { setting ->
        val audioManager = LocalContext.current.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEYPRESS_SOUND_VOLUME,
            description = {
                if (it < 0) stringResource(R.string.settings_system_default)
                else (it * 100).toInt().toString()
            },
            range = -0.01f..1f,
            onValueChanged = { audioManager.playSoundEffect(AudioManager.FX_KEYPRESS_STANDARD, it) }
        )
    },
)

// todo (later): not good to have it hardcoded, but reading a bunch of files may be noticeably slow
private val localesWithLocalizedNumberRow = listOf("ar", "bn", "fa", "gu", "hi", "kn", "mr", "ne", "ur")

@Preview
@Composable
private fun Preview() {
    SettingsActivity.settingsContainer = SettingsContainer(LocalContext.current)
    Theme(true) {
        Surface {
            PreferencesScreen { }
        }
    }
}
