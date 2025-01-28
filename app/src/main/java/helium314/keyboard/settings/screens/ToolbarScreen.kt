package helium314.keyboard.settings.screens

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ReorderDialog
import helium314.keyboard.settings.prefs
import helium314.keyboard.settings.themeChanged

@Composable
fun ToolbarScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
    ) {
        SettingsActivity2.allPrefs.map[Settings.PREF_PINNED_TOOLBAR_KEYS]!!.Preference()
    }
}

fun createToolbarPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_PINNED_TOOLBAR_KEYS, R.string.pinned_toolbar_keys) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        )
        if (showDialog) {
            val ctx = LocalContext.current
            val prefs = ctx.prefs()
            val items = prefs.getString(def.key, defaultPinnedToolbarPref)!!.split(";").mapTo(ArrayList()) {
                val both = it.split(",")
                KeyAndState(both.first(), both.last().toBoolean())
            }
            ReorderDialog(
                onConfirmed = { reorderedItems ->
                    val value = reorderedItems.joinToString(";") { it.name + "," + it.state }
                    prefs.edit().putString(def.key, value).apply()
                    themeChanged = true
                },
                onDismissRequest = { showDialog = false },
                items = items,
                title = { Text(def.title)},
                displayItem = { item ->
                    var checked by remember { mutableStateOf(item.state) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.width(40.dp)) {
                            val iconId = KeyboardIconsSet.instance.iconIds[item.name.lowercase()]
                            if (iconId != null) // using ids makes having user-provided icons more difficult...
                                Icon(painterResource(iconId), null)
                        }
                        val text = item.name.lowercase().getStringResourceOrName("", context).toString()
                        Text(text, Modifier.weight(1f))
                        Switch(
                            checked = checked,
                            onCheckedChange = { item.state = it; checked = it }
                        )
                    }
                },
                getKey = { it.hashCode() }
            )
        }
    },
)

private class KeyAndState(var name: String, var state: Boolean)

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    KeyboardIconsSet.instance.loadIcons(LocalContext.current)
    Theme(true) {
        Surface {
            ToolbarScreen { }
        }
    }
}
