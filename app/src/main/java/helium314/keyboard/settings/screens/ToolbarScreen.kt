// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.graphics.drawable.VectorDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
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
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.ReorderSwitchPreference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ToolbarKeysCustomizer
import helium314.keyboard.settings.keyboardNeedsReload

@Composable
fun ToolbarScreen(
    onClickBack: () -> Unit,
) {
    val items = listOf(
        Settings.PREF_TOOLBAR_KEYS,
        Settings.PREF_PINNED_TOOLBAR_KEYS,
        Settings.PREF_CLIPBOARD_TOOLBAR_KEYS,
        NonSettingsPrefs.CUSTOM_KEY_CODES,
        Settings.PREF_QUICK_PIN_TOOLBAR_KEYS,
        Settings.PREF_AUTO_SHOW_TOOLBAR,
        Settings.PREF_AUTO_HIDE_TOOLBAR,
        Settings.PREF_VARIABLE_TOOLBAR_DIRECTION
    )
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_toolbar),
        prefs = items
    )
}

fun createToolbarPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_TOOLBAR_KEYS, R.string.toolbar_keys) {
        ReorderSwitchPreference(it, defaultToolbarPref)
    },
    PrefDef(context, Settings.PREF_PINNED_TOOLBAR_KEYS, R.string.pinned_toolbar_keys) {
        ReorderSwitchPreference(it, defaultPinnedToolbarPref)
    },
    PrefDef(context, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, R.string.clipboard_toolbar_keys) {
        ReorderSwitchPreference(it, defaultClipboardToolbarPref)
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_KEY_CODES, R.string.customize_toolbar_key_codes) {
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = it.title,
            onClick = { showDialog = true },
        )
        if (showDialog)
            // todo (later): CUSTOM_KEY_CODES vs the 2 actual prefs that are changed...
            ToolbarKeysCustomizer(
                onDismissRequest = { showDialog = false }
            )
    },
    PrefDef(context, Settings.PREF_QUICK_PIN_TOOLBAR_KEYS,
        R.string.quick_pin_toolbar_keys, R.string.quick_pin_toolbar_keys_summary)
    {
        SwitchPreference(it, false,) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_AUTO_SHOW_TOOLBAR, R.string.auto_show_toolbar, R.string.auto_show_toolbar_summary)
    {
        SwitchPreference(it, false,)
    },
    PrefDef(context, Settings.PREF_AUTO_HIDE_TOOLBAR, R.string.auto_hide_toolbar, R.string.auto_hide_toolbar_summary)
    {
        SwitchPreference(it, false,)
    },
    PrefDef(context, Settings.PREF_VARIABLE_TOOLBAR_DIRECTION,
        R.string.var_toolbar_direction, R.string.var_toolbar_direction_summary)
    {
        SwitchPreference(it, true,)
    }
)

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
