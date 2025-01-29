package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme

@Composable
fun DebugScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
    ) {
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_SHOW_DEBUG_SETTINGS]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_DEBUG_MODE]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_SHOW_SUGGESTION_INFOS]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH]!!.Preference()
        SettingsActivity2.allPrefs.map[DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW]!!.Preference()
        PreferenceCategory(stringResource(R.string.prefs_dump_dynamic_dicts)) {
            // todo: fill it
        }
    }
}

fun createDebugPrefs(context: Context) = listOf(
    PrefDef(context, DebugSettings.PREF_SHOW_DEBUG_SETTINGS, R.string.prefs_show_debug_settings) { def ->
        SwitchPreference(def, false)
    },
    PrefDef(context, DebugSettings.PREF_DEBUG_MODE, R.string.prefs_debug_mode) { def ->
        SwitchPreference(def, false)
    },
    PrefDef(context, DebugSettings.PREF_SHOW_SUGGESTION_INFOS, R.string.prefs_show_suggestion_infos) { def ->
        SwitchPreference(def, false)
    },
    PrefDef(context, DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH, R.string.prefs_force_non_distinct_multitouch) { def ->
        SwitchPreference(def, false)
    },
    PrefDef(context, DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, R.string.sliding_key_input_preview, R.string.sliding_key_input_preview_summary) { def ->
        SwitchPreference(def, false)
    },
    // todo: what about "dump dictionaries"?
)

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
