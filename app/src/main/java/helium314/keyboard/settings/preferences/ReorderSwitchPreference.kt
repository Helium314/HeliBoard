package helium314.keyboard.settings.preferences

import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.res.stringResource
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.dialogs.ReorderDialog
import helium314.keyboard.settings.keyboardNeedsReload
import helium314.keyboard.settings.screens.GetIcon

@Composable
fun ReorderSwitchPreference(def: PrefDef, default: String) {
    var showDialog by remember { mutableStateOf(false) }
    Preference(
        name = def.title,
        description = def.description,
        onClick = { showDialog = true },
    )
    if (showDialog) {
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val items = prefs.getString(def.key, default)!!.split(";").mapTo(ArrayList()) {
            val both = it.split(",")
            KeyAndState(both.first(), both.last().toBoolean())
        }
        ReorderDialog(
            onConfirmed = { reorderedItems ->
                val value = reorderedItems.joinToString(";") { it.name + "," + it.state }
                prefs.edit().putString(def.key, value).apply()
                keyboardNeedsReload = true
            },
            onDismissRequest = { showDialog = false },
            onNeutral = { prefs.edit().remove(def.key).apply() },
            neutralButtonText = if (prefs.contains(def.key)) stringResource(R.string.button_default) else null,
            items = items,
            title = { Text(def.title) },
            displayItem = { item ->
                var checked by remember { mutableStateOf(item.state) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    KeyboardIconsSet.instance.GetIcon(item.name)
                    val text = item.name.lowercase().getStringResourceOrName("", ctx)
                    Text(text, Modifier.weight(1f))
                    Switch(
                        checked = checked,
                        onCheckedChange = { item.state = it; checked = it }
                    )
                }
            },
            getKey = { it.name }
        )
    }
}

private class KeyAndState(var name: String, var state: Boolean)
