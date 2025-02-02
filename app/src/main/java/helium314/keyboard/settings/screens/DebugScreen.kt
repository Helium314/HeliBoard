package helium314.keyboard.settings.screens

import android.content.Context
import android.content.Intent
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun DebugScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.debug_settings_title),
    ) {
        if (!BuildConfig.DEBUG)
            SettingsActivity2.allPrefs.map[DebugSettings.PREF_SHOW_DEBUG_SETTINGS]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_DEBUG_MODE]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_SHOW_SUGGESTION_INFOS]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW]!!.Preference()
        PreferenceCategory(stringResource(R.string.prefs_dump_dynamic_dicts)) {
            DictionaryFacilitator.DYNAMIC_DICTIONARY_TYPES.forEach {
                SettingsActivity2.allPrefs.map[DebugSettingsFragment.PREF_KEY_DUMP_DICT_PREFIX + it]!!.Preference()
            }
        }
    }
}

fun createDebugPrefs(context: Context) = listOf(
    PrefDef(context, DebugSettings.PREF_SHOW_DEBUG_SETTINGS, R.string.prefs_show_debug_settings) { def ->
        val prefs = LocalContext.current.prefs()
        SwitchPreference(
            name = def.title,
            pref = def.key,
            default = false,
        ) { if (!it) prefs.edit().putBoolean(DebugSettings.PREF_DEBUG_MODE, false).apply() }
    },
    PrefDef(context, DebugSettings.PREF_DEBUG_MODE, R.string.prefs_debug_mode) { def ->
        val prefs = LocalContext.current.prefs()
        var showConfirmDialog by remember { mutableStateOf(false) }
        SwitchPreference(
            name = def.title,
            pref = def.key,
            description = stringResource(R.string.version_text, BuildConfig.VERSION_NAME),
            default = false,
        ) {
            if (!it) prefs.edit().putBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, false).apply()
            showConfirmDialog = true
        }
        if (showConfirmDialog) { // todo: maybe do it differently?
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = { Runtime.getRuntime().exit(0) },
                text = { Text(stringResource(R.string.message_restart_required)) }
            )
        }
    },
    PrefDef(context, DebugSettings.PREF_SHOW_SUGGESTION_INFOS, R.string.prefs_show_suggestion_infos) { def ->
        SwitchPreference(def, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, R.string.prefs_force_non_distinct_multitouch) { def ->
        var showConfirmDialog by remember { mutableStateOf(false) }
        SwitchPreference(def, false) { showConfirmDialog = true }
        if (showConfirmDialog) { // todo: maybe do it differently?
            ConfirmationDialog(
                onDismissRequest = { showConfirmDialog = false },
                onConfirmed = { Runtime.getRuntime().exit(0) },
                text = { Text(stringResource(R.string.message_restart_required)) }
            )
        }
    },
    PrefDef(context, DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, R.string.sliding_key_input_preview, R.string.sliding_key_input_preview_summary) { def ->
        SwitchPreference(def, false)
    },
) + DictionaryFacilitator.DYNAMIC_DICTIONARY_TYPES.map {
    PrefDef(context, DebugSettingsFragment.PREF_KEY_DUMP_DICT_PREFIX + it, R.string.button_default) { def ->
        val ctx = LocalContext.current
        Preference(
            name = "Dump $it dictionary",
            onClick = {
                val intent = Intent(DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION)
                intent.putExtra(DictionaryDumpBroadcastReceiver.DICTIONARY_NAME_KEY, it)
                ctx.sendBroadcast(intent)
            }
        )
    }
}

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            DebugScreen { }
        }
    }
}
