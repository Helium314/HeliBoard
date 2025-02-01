// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.ListPreference
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if (b?.value ?: 0 < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val gestureEnabled = prefs.getBoolean(Settings.PREF_GESTURE_INPUT, true)
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
    ) {
        PreferenceCategory(stringResource(R.string.settings_screen_theme)) {

        }
        PreferenceCategory(stringResource(R.string.settings_category_miscellaneous)) {

        }
        SettingsActivity2.allPrefs.map[Settings.PREF_GESTURE_INPUT]!!.Preference()
    }
}

fun createAppearancePrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_THEME_STYLE, R.string.theme_style) { def ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.STYLE_MATERIAL
        )
    },
    PrefDef(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { def ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.STYLE_MATERIAL
        )
    },
    PrefDef(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        ) // todo: create and show the dialog
    },
    PrefDef(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { def ->
        val ctx = LocalContext.current
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle == KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.THEME_LIGHT
        )
    },
    PrefDef(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { def ->
        val ctx = LocalContext.current
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle == KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.THEME_DARK
        )
    },
    // todo: non-settings pref
    PrefDef(context, "theme_select_colors", R.string.select_user_colors, R.string.select_user_colors_summary) { def ->
        Preference(
            name = def.title,
            description = def.description,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.Colors) }
        )
    },
    PrefDef(context, "theme_select_colors_night", R.string.select_user_colors_night, R.string.select_user_colors_summary) { def ->
        Preference(
            name = def.title,
            description = def.description,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.ColorsNight) }
        )
    },
    PrefDef(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) { def ->
        SwitchPreference(def, false)
    },
    PrefDef(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) { def ->
        SwitchPreference(def, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    },
    PrefDef(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) { def ->
        SwitchPreference(def, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    },
    // todo: non-settings pref
    PrefDef(context, "custom_background_image", R.string.customize_background_image) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        ) // todo: create and show the dialog
    },
    // todo: non-settings pref
    PrefDef(context, "custom_background_image_landscape", R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            description = def.description,
            onClick = { showDialog = true }
        ) // todo: create and show the dialog
    },
    // todo: add misc category, then add functionality, then add to the actual screen
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            AppearanceScreen { }
        }
    }
}
