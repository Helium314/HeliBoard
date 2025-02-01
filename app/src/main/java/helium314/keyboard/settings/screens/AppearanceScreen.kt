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
import helium314.keyboard.latin.settings.ColorsNightSettingsFragment
import helium314.keyboard.latin.settings.ColorsSettingsFragment
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsValues
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.switchTo
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.ListPreference
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SliderPreference
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if (b?.value ?: 0 < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Settings.readDayNightPref(prefs, ctx.resources)
    val lightTheme = prefs.getString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT)
    val darkTheme = prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK)
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
    ) {
        PreferenceCategory(stringResource(R.string.settings_screen_theme)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_THEME_STYLE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_ICON_STYLE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_CUSTOM_ICON_NAMES]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_THEME_COLORS]!!.Preference()
            if (lightTheme == KeyboardTheme.THEME_USER)
                SettingsActivity2.allPrefs.map[NonSettingsPrefs.ADJUST_COLORS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_THEME_KEY_BORDERS]!!.Preference()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
                SettingsActivity2.allPrefs.map[Settings.PREF_THEME_DAY_NIGHT]!!.Preference()
            if (dayNightMode)
                SettingsActivity2.allPrefs.map[Settings.PREF_THEME_COLORS_NIGHT]!!.Preference()
            if (dayNightMode && darkTheme == KeyboardTheme.THEME_USER_NIGHT)
                SettingsActivity2.allPrefs.map[NonSettingsPrefs.ADJUST_COLORS_NIGHT]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_NAVBAR_COLOR]!!.Preference()
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.BACKGROUND_IMAGE]!!.Preference()
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.BACKGROUND_IMAGE_LANDSCAPE]!!.Preference()
        }
        PreferenceCategory(stringResource(R.string.settings_category_miscellaneous)) {
            SettingsActivity2.allPrefs.map[Settings.PREF_ENABLE_SPLIT_KEYBOARD]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SPLIT_SPACER_SCALE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_NARROW_KEY_GAPS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_NARROW_KEY_GAPS]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_KEYBOARD_HEIGHT_SCALE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_BOTTOM_PADDING_SCALE]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_SPACE_BAR_TEXT]!!.Preference()
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.CUSTOM_FONT]!!.Preference()
        }
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
        )
        if (showDialog) {
            CustomizeIconsDialog(def.key) { showDialog = false }
        }
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
    PrefDef(context, NonSettingsPrefs.ADJUST_COLORS, R.string.select_user_colors, R.string.select_user_colors_summary) { def ->
        val ctx = LocalContext.current
        Preference(
            name = def.title,
            description = def.description,
            onClick = {
                ctx.getActivity()?.switchTo(ColorsSettingsFragment())
                //SettingsDestination.navigateTo(SettingsDestination.Colors) todo: later
            }
        )
    },
    PrefDef(context, NonSettingsPrefs.ADJUST_COLORS_NIGHT, R.string.select_user_colors_night, R.string.select_user_colors_summary) { def ->
        val ctx = LocalContext.current
        Preference(
            name = def.title,
            description = def.description,
            onClick = {
                ctx.getActivity()?.switchTo(ColorsNightSettingsFragment())
                //SettingsDestination.navigateTo(SettingsDestination.ColorsNight) todo: later
            }
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
    PrefDef(context, NonSettingsPrefs.BACKGROUND_IMAGE, R.string.customize_background_image) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        ) // todo: create and show the dialog
    },
    PrefDef(context, NonSettingsPrefs.BACKGROUND_IMAGE_LANDSCAPE, R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            description = def.description,
            onClick = { showDialog = true }
        ) // todo: create and show the dialog
    },
    PrefDef(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_SPLIT_SPACER_SCALE, R.string.split_spacer_scale) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        )
    },
    PrefDef(context, Settings.PREF_NARROW_KEY_GAPS, R.string.prefs_narrow_key_gaps) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE, R.string.prefs_keyboard_height_scale) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        )
    },
    PrefDef(context, Settings.PREF_BOTTOM_PADDING_SCALE, R.string.prefs_bottom_padding_scale) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        )
    },
    PrefDef(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) { def ->
        var showDialog by remember { mutableStateOf(false) }
        val prefs = LocalContext.current.prefs()
        Preference(
            name = def.title,
            onClick = { showDialog = true },
            description = prefs.getString(def.key, "")
        ) // todo: create and show the dialog
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_FONT, R.string.custom_font) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true },
        ) // todo: create and show the dialog
    },
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
