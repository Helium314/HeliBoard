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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchSettingsScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ColorThemePickerDialog
import helium314.keyboard.settings.dialogs.CustomizeIconsDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.initPreview
import helium314.keyboard.settings.preferences.BackgroundImagePref
import helium314.keyboard.settings.preferences.CustomFontPreference
import helium314.keyboard.settings.previewDark

@Composable
fun AppearanceScreen(
    onClickBack: () -> Unit,
) {
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
    if ((b?.value ?: 0) < 0)
        Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
    val dayNightMode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT)
    val items = listOf(
        R.string.settings_screen_theme,
        Settings.PREF_THEME_STYLE,
        Settings.PREF_ICON_STYLE,
        Settings.PREF_CUSTOM_ICON_NAMES,
        Settings.PREF_THEME_COLORS,
        Settings.PREF_THEME_KEY_BORDERS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            Settings.PREF_THEME_DAY_NIGHT else null,
        if (dayNightMode) Settings.PREF_THEME_COLORS_NIGHT else null,
        Settings.PREF_NAVBAR_COLOR,
        SettingsWithoutKey.BACKGROUND_IMAGE,
        SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.settings_category_miscellaneous,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD,
        if (prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, Defaults.PREF_ENABLE_SPLIT_KEYBOARD))
            Settings.PREF_SPLIT_SPACER_SCALE else null,
        Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE,
        if (prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, Defaults.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE))
            Settings.PREF_SPLIT_SPACER_SCALE_LANDSCAPE else null,
        if (prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, Defaults.PREF_THEME_KEY_BORDERS))
            Settings.PREF_NARROW_KEY_GAPS else null,
        Settings.PREF_KEYBOARD_HEIGHT_SCALE,
        Settings.PREF_BOTTOM_PADDING_SCALE,
        Settings.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE,
        Settings.PREF_SIDE_PADDING_SCALE,
        Settings.PREF_SIDE_PADDING_SCALE_LANDSCAPE,
        Settings.PREF_SPACE_BAR_TEXT,
        SettingsWithoutKey.CUSTOM_FONT,
        Settings.PREF_FONT_SCALE,
        Settings.PREF_EMOJI_FONT_SCALE,
    )
    SearchSettingsScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        settings = items
    )
}

fun createAppearanceSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_THEME_STYLE, R.string.theme_style) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val items = KeyboardTheme.STYLES.map {
            it.getStringResourceOrName("style_name_", ctx) to it
        }
        ListPreference(
            setting,
            items,
            Defaults.PREF_ICON_STYLE
        ) {
            if (it != KeyboardTheme.STYLE_HOLO) {
                if (prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS) == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit().remove(Settings.PREF_THEME_COLORS).apply()
                if (prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT) == KeyboardTheme.THEME_HOLO_WHITE)
                    prefs.edit().remove(Settings.PREF_THEME_COLORS_NIGHT).apply()
            }
        }
    },
    Setting(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { setting ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to it }
        ListPreference(
            setting,
            items,
            Defaults.PREF_ICON_STYLE
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
/*            if (keyboardNeedsReload) {
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(LocalContext.current)
                keyboardNeedsReload = false
            }
*/            CustomizeIconsDialog(setting.key) { showDialog = false }
        }
    },
    Setting(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { setting ->
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = false,
                default = Defaults.PREF_THEME_COLORS
            )
    },
    Setting(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        val prefs = ctx.prefs()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        var showDialog by rememberSaveable { mutableStateOf(false) }
        Preference(
            name = setting.title,
            description = prefs.getString(setting.key, Defaults.PREF_THEME_COLORS_NIGHT)!!.getStringResourceOrName("theme_name_", ctx),
            onClick = { showDialog = true }
        )
        if (showDialog)
            ColorThemePickerDialog(
                onDismissRequest = { showDialog = false },
                setting = setting,
                isNight = true,
                default = Defaults.PREF_THEME_COLORS_NIGHT
            )
    },
    Setting(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, Defaults.PREF_THEME_KEY_BORDERS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_THEME_DAY_NIGHT) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Defaults.PREF_NAVBAR_COLOR)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE, R.string.customize_background_image) {
        BackgroundImagePref(it, false)
    },
    Setting(context, SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
        R.string.customize_background_image_landscape, R.string.summary_customize_background_image_landscape)
    {
        BackgroundImagePref(it, true)
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD, R.string.enable_split_keyboard) {
        SwitchPreference(it, Defaults.PREF_ENABLE_SPLIT_KEYBOARD) { KeyboardSwitcher.getInstance().reloadKeyboard() }
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE, R.string.split_spacer_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_SPLIT_SPACER_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE, R.string.enable_split_keyboard_landscape) {
        SwitchPreference(it, Defaults.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE) { KeyboardSwitcher.getInstance().reloadKeyboard() }
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE_LANDSCAPE, R.string.split_spacer_scale_landscape) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_SPLIT_SPACER_SCALE_LANDSCAPE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_NARROW_KEY_GAPS, R.string.prefs_narrow_key_gaps) {
        SwitchPreference(it, Defaults.PREF_NARROW_KEY_GAPS) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE, R.string.prefs_keyboard_height_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_KEYBOARD_HEIGHT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE, R.string.prefs_bottom_padding_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_BOTTOM_PADDING_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE, R.string.prefs_bottom_padding_scale_landscape) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE, R.string.prefs_side_padding_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_SIDE_PADDING_SCALE,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE_LANDSCAPE, R.string.prefs_side_padding_scale_landscape) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_SIDE_PADDING_SCALE_LANDSCAPE,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) { setting ->
        var showDialog by rememberSaveable { mutableStateOf(false) }
        val prefs = LocalContext.current.prefs()
        Preference(
            name = setting.title,
            onClick = { showDialog = true },
            description = prefs.getString(setting.key, Defaults.PREF_SPACE_BAR_TEXT)?.takeIf { it.isNotEmpty() }
        )
        if (showDialog) {
            TextInputDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = {
                    prefs.edit().putString(setting.key, it).apply()
                    KeyboardSwitcher.getInstance().setThemeNeedsReload()
                },
                initialText = prefs.getString(setting.key, Defaults.PREF_SPACE_BAR_TEXT) ?: "",
                title = { Text(setting.title) },
                checkTextValid = { true }
            )
        }
    },
    Setting(context, SettingsWithoutKey.CUSTOM_FONT, R.string.custom_font) {
        CustomFontPreference(it)
    },
    Setting(context, Settings.PREF_FONT_SCALE, R.string.prefs_font_scale) { def ->
        SliderPreference(
            name = def.title,
            key = def.key,
            default = Defaults.PREF_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
    Setting(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Defaults.PREF_EMOJI_FONT_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { KeyboardSwitcher.getInstance().setThemeNeedsReload() }
    },
)

@Preview
@Composable
private fun Preview() {
    initPreview(LocalContext.current)
    Theme(previewDark) {
        Surface {
            AppearanceScreen { }
        }
    }
}
