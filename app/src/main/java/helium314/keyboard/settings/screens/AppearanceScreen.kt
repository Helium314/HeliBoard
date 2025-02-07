// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.content.Context
import android.os.Build
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
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
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.keyboardNeedsReload
import helium314.keyboard.settings.preferences.BackgroundImagePref
import helium314.keyboard.settings.preferences.CustomFontPreference

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && Settings.readDayNightPref(prefs, ctx.resources)
    val items = listOfNotNull(
        R.string.settings_screen_theme,
        Settings.PREF_THEME_STYLE,
        Settings.PREF_ICON_STYLE,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_THEME_COLORS,
        if (prefs.getString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT) == KeyboardTheme.THEME_USER)
            NonSettingsPrefs.ADJUST_COLORS else null,
        Settings.PREF_THEME_KEY_BORDERS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            Settings.PREF_THEME_DAY_NIGHT else null,
        if (dayNightMode) Settings.PREF_THEME_COLORS_NIGHT else null,
        if (dayNightMode && prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK) == KeyboardTheme.THEME_USER_NIGHT)
            NonSettingsPrefs.ADJUST_COLORS_NIGHT else null,
        Settings.PREF_NAVBAR_COLOR,
        NonSettingsPrefs.BACKGROUND_IMAGE,
        NonSettingsPrefs.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.settings_category_miscellaneous,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD,
        Settings.PREF_SPLIT_SPACER_SCALE,
        Settings.PREF_NARROW_KEY_GAPS,
        Settings.PREF_KEYBOARD_HEIGHT_SCALE,
        Settings.PREF_BOTTOM_PADDING_SCALE,
        Settings.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE,
        Settings.PREF_SIDE_PADDING_SCALE,
        Settings.PREF_SIDE_PADDING_SCALE_LANDSCAPE,
        Settings.PREF_SPACE_BAR_TEXT,
        NonSettingsPrefs.CUSTOM_FONT,
        Settings.PREF_FONT_SCALE,
        Settings.PREF_EMOJI_FONT_SCALE,
    )
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        prefs = items
    )
}

fun createAppearancePrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_THEME_STYLE, R.string.theme_style) { def ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.STYLE_MATERIAL
        ) {
            if (it != KeyboardTheme.STYLE_HOLO) {
                // todo (later): use defaults once they exist
                if (prefs.getString(Settings.PREF_THEME_COLORS, "") == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit().putString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT).apply()
                if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, "") == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit().putString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK).apply()
            }
        }
    },
    PrefDef(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { def ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to it }
        ListPreference(
            def,
            items,
            KeyboardTheme.STYLE_MATERIAL
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            if (keyboardNeedsReload) {
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(LocalContext.current)
                keyboardNeedsReload = false
            }
            CustomizeIconsDialog(def.key) { showDialog = false }
        }
    },
    PrefDef(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { def ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle != KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.THEME_LIGHT
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { def ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS_DARK.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle == KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            def,
            items,
            KeyboardTheme.THEME_DARK
        ) { keyboardNeedsReload = true }
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
    PrefDef(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    },
    PrefDef(context, NonSettingsPrefs.BACKGROUND_IMAGE, R.string.customize_background_image) {
        BackgroundImagePref(it, false)
    },
    PrefDef(context, NonSettingsPrefs.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape)
    {
        BackgroundImagePref(it, true)
    },
    PrefDef(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        SwitchPreference(it, false)
    },
    PrefDef(context, Settings.PREF_SPLIT_SPACER_SCALE, R.string.split_spacer_scale) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_NARROW_KEY_GAPS, R.string.prefs_narrow_key_gaps) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE, R.string.prefs_keyboard_height_scale) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_BOTTOM_PADDING_SCALE, R.string.prefs_bottom_padding_scale) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE, R.string.prefs_bottom_padding_scale_landscape) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = 0f,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_SIDE_PADDING_SCALE, R.string.prefs_side_padding_scale) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = 0f,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_SIDE_PADDING_SCALE_LANDSCAPE, R.string.prefs_side_padding_scale_landscape) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = 0f,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) { def ->
        var showDialog by remember { mutableStateOf(false) }
        val prefs = LocalContext.current.prefs()
        Preference(
            name = def.title,
            onClick = { showDialog = true },
            description = prefs.getString(def.key, "")
        )
        if (showDialog) {
            TextInputDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = {
                    prefs.edit().putString(def.key, it).apply()
                    keyboardNeedsReload = true
                },
                initialText = prefs.getString(def.key, "") ?: "",
                title = { Text(def.title) },
                checkTextValid = { true }
            )
        }
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_FONT, R.string.custom_font) {
        CustomFontPreference(it)
    },
    PrefDef(context, Settings.PREF_FONT_SCALE, R.string.prefs_font_scale) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = 1f,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    PrefDef(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { def ->
        SliderPreference(
            name = def.title,
            pref = def.key,
            default = 1f,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
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
