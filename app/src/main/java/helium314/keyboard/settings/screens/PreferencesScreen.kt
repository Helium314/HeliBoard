package helium314.keyboard.settings.screens

import android.content.Context
import android.media.AudioManager
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.latin.AudioAndHapticFeedbackManager
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SliderPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.SliderDialog

@Composable
fun PreferencesScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_preferences),
    ) {
        SettingsActivity2.allPrefs.map[Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_VIBRATION_DURATION_SETTINGS]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_KEYPRESS_SOUND_VOLUME]!!.Preference()
    }
}

fun createPreferencesPrefs(context: Context) = listOf(
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
