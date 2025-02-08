// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.DictionaryDumpBroadcastReceiver
import helium314.keyboard.latin.DictionaryFacilitator
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.DebugSettingsFragment
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun DebugScreen(
    onClickBack: () -> Unit,
) {
    val items = listOfNotNull(
        if (!BuildConfig.DEBUG) DebugSettings.PREF_SHOW_DEBUG_SETTINGS else null,
        DebugSettings.PREF_DEBUG_MODE,
        DebugSettings.PREF_SHOW_SUGGESTION_INFOS,
        DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH,
        DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW,
        R.string.prefs_dump_dynamic_dicts
    ) + DictionaryFacilitator.DYNAMIC_DICTIONARY_TYPES.map { DebugSettingsFragment.PREF_KEY_DUMP_DICT_PREFIX + it }
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.debug_settings_title),
        prefs = items
    )
}

fun createDebugSettings(context: Context) = listOf(
    Setting(context, DebugSettings.PREF_SHOW_DEBUG_SETTINGS, R.string.prefs_show_debug_settings) { setting ->
        val prefs = LocalContext.current.prefs()
        SwitchPreference(setting, false)
        { if (!it) prefs.edit().putBoolean(DebugSettings.PREF_DEBUG_MODE, false).apply() }
    },
    Setting(context, DebugSettings.PREF_DEBUG_MODE, R.string.prefs_debug_mode) { setting ->
        val prefs = LocalContext.current.prefs()
        var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
        SwitchPreference(
            name = setting.title,
            key = setting.key,
            description = stringResource(R.string.version_text, BuildConfig.VERSION_NAME),
            default = false,
        ) {
            if (!it) prefs.edit().putBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, false).apply()
            showConfirmDialog = true
        }
        if (showConfirmDialog) {
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = { Runtime.getRuntime().exit(0) },
                text = { Text(stringResource(R.string.message_restart_required)) }
            )
        }
    },
    Setting(context, DebugSettings.PREF_SHOW_SUGGESTION_INFOS, R.string.prefs_show_suggestion_infos) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    Setting(context, DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, R.string.prefs_force_non_distinct_multitouch) {
        var showConfirmDialog by rememberSaveable { mutableStateOf(false) }
        SwitchPreference(it, false) { showConfirmDialog = true }
        if (showConfirmDialog) {
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = { Runtime.getRuntime().exit(0) },
                text = { Text(stringResource(R.string.message_restart_required)) }
            )
        }
    },
    Setting(context, DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, R.string.sliding_key_input_preview, R.string.sliding_key_input_preview_summary) { def ->
        SwitchPreference(def, false)
    },
) + DictionaryFacilitator.DYNAMIC_DICTIONARY_TYPES.map { type ->
    Setting(context, DebugSettingsFragment.PREF_KEY_DUMP_DICT_PREFIX + type, R.string.button_default) {
        val ctx = LocalContext.current
        Preference(
            name = "Dump $type dictionary",
            onClick = {
                val intent = Intent(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION)
                intent.putExtra(DictionaryDumpBroadcastReceiver.DICTIONARY_NAME_KEY, type)
                ctx.sendBroadcast(intent)
            }
        )
    }
}

@Preview
@Composable
private fun Preview() {
    SettingsActivity.settingsContainer = SettingsContainer(LocalContext.current)
    Theme(true) {
        Surface {
            DebugScreen { }
        }
    }
}
