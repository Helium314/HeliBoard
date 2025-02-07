package helium314.keyboard.settings.preferences

import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity

@Composable
fun SwitchPreference(
    setting: Setting,
    default: Boolean,
    allowCheckedChange: (Boolean) -> Boolean = { true },
    onCheckedChange: (Boolean) -> Unit = { }
) {
    SwitchPreference(
        name = setting.title,
        description = setting.description,
        key = setting.key,
        default = default,
        allowCheckedChange = allowCheckedChange,
        onCheckedChange = onCheckedChange
    )
}

@Composable
fun SwitchPreference(
    name: String,
    modifier: Modifier = Modifier,
    key: String,
    default: Boolean,
    description: String? = null,
    allowCheckedChange: (Boolean) -> Boolean = { true }, // true means ok, usually for showing some dialog
    onCheckedChange: (Boolean) -> Unit = { },
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    var value = prefs.getBoolean(key, default)
    fun switched(newValue: Boolean) {
        if (!allowCheckedChange(newValue)) {
            value = !newValue
            return
        }
        value = newValue
        prefs.edit().putBoolean(key, newValue).apply()
        onCheckedChange(newValue)
    }
    Preference(
        name = name,
        onClick = { switched(!value) },
        modifier = modifier,
        description = description
    ) {
        Switch(
            checked = value,
            onCheckedChange = { switched(it) },
        )
    }
}
