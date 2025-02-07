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
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity
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
    val b = (LocalContext.current.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
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
            SettingsWithoutKey.ADJUST_COLORS else null,
        Settings.PREF_THEME_KEY_BORDERS,
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            Settings.PREF_THEME_DAY_NIGHT else null,
        if (dayNightMode) Settings.PREF_THEME_COLORS_NIGHT else null,
        if (dayNightMode && prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK) == KeyboardTheme.THEME_USER_NIGHT)
            SettingsWithoutKey.ADJUST_COLORS_NIGHT else null,
        Settings.PREF_NAVBAR_COLOR,
        SettingsWithoutKey.BACKGROUND_IMAGE,
        SettingsWithoutKey.BACKGROUND_IMAGE_LANDSCAPE,
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
        SettingsWithoutKey.CUSTOM_FONT,
        Settings.PREF_FONT_SCALE,
        Settings.PREF_EMOJI_FONT_SCALE,
    )
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_appearance),
        prefs = items
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
    Setting(context, Settings.PREF_ICON_STYLE, R.string.icon_style) { setting ->
        val ctx = LocalContext.current
        val items = KeyboardTheme.STYLES.map { it.getStringResourceOrName("style_name_", ctx) to it }
        ListPreference(
            setting,
            items,
            KeyboardTheme.STYLE_MATERIAL
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_CUSTOM_ICON_NAMES, R.string.customize_icons) { setting ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            if (keyboardNeedsReload) {
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(LocalContext.current)
                keyboardNeedsReload = false
            }
            CustomizeIconsDialog(setting.key) { showDialog = false }
        }
    },
    Setting(context, Settings.PREF_THEME_COLORS, R.string.theme_colors) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle != KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            setting,
            items,
            KeyboardTheme.THEME_LIGHT
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_THEME_COLORS_NIGHT, R.string.theme_colors_night) { setting ->
        val ctx = LocalContext.current
        val b = (ctx.getActivity() as? SettingsActivity)?.prefChanged?.collectAsState()
        if ((b?.value ?: 0) < 0)
            Log.v("irrelevant", "stupid way to trigger recomposition on preference change")
        val currentStyle = ctx.prefs().getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val items = KeyboardTheme.COLORS_DARK.mapNotNull {
            if (it == KeyboardTheme.THEME_HOLO_WHITE && currentStyle == KeyboardTheme.STYLE_HOLO)
                return@mapNotNull null
            it.getStringResourceOrName("theme_name_", ctx) to it
        }
        ListPreference(
            setting,
            items,
            KeyboardTheme.THEME_DARK
        ) { keyboardNeedsReload = true }
    },
    Setting(context, SettingsWithoutKey.ADJUST_COLORS, R.string.select_user_colors, R.string.select_user_colors_summary) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                ctx.getActivity()?.switchTo(ColorsSettingsFragment())
                //SettingsDestination.navigateTo(SettingsDestination.Colors) todo: later
            }
        )
    },
    Setting(context, SettingsWithoutKey.ADJUST_COLORS_NIGHT, R.string.select_user_colors_night, R.string.select_user_colors_summary) {
        val ctx = LocalContext.current
        Preference(
            name = it.title,
            description = it.description,
            onClick = {
                ctx.getActivity()?.switchTo(ColorsNightSettingsFragment())
                //SettingsDestination.navigateTo(SettingsDestination.ColorsNight) todo: later
            }
        )
    },
    Setting(context, Settings.PREF_THEME_KEY_BORDERS, R.string.key_borders) {
        SwitchPreference(it, false)
    },
    Setting(context, Settings.PREF_THEME_DAY_NIGHT, R.string.day_night_mode, R.string.day_night_mode_summary) {
        SwitchPreference(it, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_NAVBAR_COLOR, R.string.theme_navbar, R.string.day_night_mode_summary) {
        SwitchPreference(it, Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
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
        SwitchPreference(it, false)
    },
    Setting(context, Settings.PREF_SPLIT_SPACER_SCALE, R.string.split_spacer_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..2f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_NARROW_KEY_GAPS, R.string.prefs_narrow_key_gaps) {
        SwitchPreference(it, false) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_KEYBOARD_HEIGHT_SCALE, R.string.prefs_keyboard_height_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE, R.string.prefs_bottom_padding_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = SettingsValues.DEFAULT_SIZE_SCALE,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE, R.string.prefs_bottom_padding_scale_landscape) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = 0f,
            range = 0f..5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE, R.string.prefs_side_padding_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = 0f,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_SIDE_PADDING_SCALE_LANDSCAPE, R.string.prefs_side_padding_scale_landscape) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = 0f,
            range = 0f..3f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_SPACE_BAR_TEXT, R.string.prefs_space_bar_text) { setting ->
        var showDialog by remember { mutableStateOf(false) }
        val prefs = LocalContext.current.prefs()
        Preference(
            name = setting.title,
            onClick = { showDialog = true },
            description = prefs.getString(setting.key, "")
        )
        if (showDialog) {
            TextInputDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = {
                    prefs.edit().putString(setting.key, it).apply()
                    keyboardNeedsReload = true
                },
                initialText = prefs.getString(setting.key, "") ?: "",
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
            default = 1f,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
    Setting(context, Settings.PREF_EMOJI_FONT_SCALE, R.string.prefs_emoji_font_scale) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = 1f,
            range = 0.5f..1.5f,
            description = { "${(100 * it).toInt()}%" }
        ) { keyboardNeedsReload = true }
    },
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity.settingsContainer = SettingsContainer(LocalContext.current)
    Theme(true) {
        Surface {
            AppearanceScreen { }
        }
    }
}
