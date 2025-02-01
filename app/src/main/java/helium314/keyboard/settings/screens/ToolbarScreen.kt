package helium314.keyboard.settings.screens

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.core.util.TypedValueCompat
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.defaultClipboardToolbarPref
import helium314.keyboard.latin.utils.defaultPinnedToolbarPref
import helium314.keyboard.latin.utils.defaultToolbarPref
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ReorderDialog
import helium314.keyboard.settings.dialogs.ToolbarKeysCustomizer
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun ToolbarScreen(
    onClickBack: () -> Unit,
) {
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
    ) {
        SettingsActivity2.allPrefs.map[Settings.PREF_TOOLBAR_KEYS]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_PINNED_TOOLBAR_KEYS]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_CLIPBOARD_TOOLBAR_KEYS]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.CUSTOM_KEY_CODES]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_QUICK_PIN_TOOLBAR_KEYS]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_AUTO_SHOW_TOOLBAR]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_AUTO_HIDE_TOOLBAR]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_VARIABLE_TOOLBAR_DIRECTION]!!.Preference()
    }
}

fun createToolbarPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_TOOLBAR_KEYS, R.string.toolbar_keys) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        )
        if (showDialog) {
            ToolbarKeyReorderDialog(
                def.key,
                defaultToolbarPref,
                def.title,
            ) { showDialog = false }
        }
    },
    PrefDef(context, Settings.PREF_PINNED_TOOLBAR_KEYS, R.string.pinned_toolbar_keys) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        )
        if (showDialog) {
            ToolbarKeyReorderDialog(
                def.key,
                defaultPinnedToolbarPref,
                def.title,
            ) { showDialog = false }
        }
    },
    PrefDef(context, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, R.string.clipboard_toolbar_keys) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        )
        if (showDialog) {
            ToolbarKeyReorderDialog(
                def.key,
                defaultClipboardToolbarPref,
                def.title,
            ) { showDialog = false }
        }
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_KEY_CODES, R.string.customize_toolbar_key_codes) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        )
        if (showDialog)
            // todo: CUSTOM_KEY_CODES vs the 2 actual prefs that are changed...
            ToolbarKeysCustomizer(
                onDismissRequest = { showDialog = false }
            )
    },
    PrefDef(context, Settings.PREF_QUICK_PIN_TOOLBAR_KEYS, R.string.quick_pin_toolbar_keys, R.string.quick_pin_toolbar_keys_summary) { def ->
        SwitchPreference(
            def,
            false,
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_AUTO_SHOW_TOOLBAR, R.string.auto_show_toolbar, R.string.auto_show_toolbar_summary) { def ->
        SwitchPreference(
            def,
            false,
        )
    },
    PrefDef(context, Settings.PREF_AUTO_HIDE_TOOLBAR, R.string.auto_hide_toolbar, R.string.auto_hide_toolbar_summary) { def ->
        SwitchPreference(
            def,
            false,
        )
    },
    PrefDef(context, Settings.PREF_VARIABLE_TOOLBAR_DIRECTION, R.string.var_toolbar_direction, R.string.var_toolbar_direction_summary) { def ->
        SwitchPreference(
            def,
            true,
        )
    }
)

@Composable
fun ToolbarKeyReorderDialog(
    prefKey: String,
    default: String,
    title: String,
    onDismiss: () -> Unit
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val items = prefs.getString(prefKey, default)!!.split(";").mapTo(ArrayList()) {
        val both = it.split(",")
        KeyAndState(both.first(), both.last().toBoolean())
    }
    ReorderDialog(
        onConfirmed = { reorderedItems ->
            val value = reorderedItems.joinToString(";") { it.name + "," + it.state }
            prefs.edit().putString(prefKey, value).apply()
            keyboardNeedsReload = true
        },
        onDismissRequest = onDismiss,
        items = items,
        title = { Text(title) },
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

@Composable
fun KeyboardIconsSet.GetIcon(name: String?) {
    val ctx = LocalContext.current
    val drawable = getNewDrawable(name, ctx)
    Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
        if (drawable is VectorDrawable)
            Icon(painterResource(iconIds[name?.lowercase()]!!), null, Modifier.fillMaxSize(0.8f))
        else if (drawable != null) {
            val px = TypedValueCompat.dpToPx(40f, ctx.resources.displayMetrics).toInt()
            Icon(drawable.toBitmap(px, px).asImageBitmap(), null, Modifier.fillMaxSize(0.8f))
        }
    }
}
