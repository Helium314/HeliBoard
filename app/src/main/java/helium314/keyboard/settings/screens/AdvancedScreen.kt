// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import helium314.keyboard.keyboard.KeyboardActionListener
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.keyboard_parser.RawKeyboardParser
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SystemBroadcastReceiver
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_NORMAL
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.SettingsContainer
import helium314.keyboard.settings.preferences.ListPreference
import helium314.keyboard.settings.SettingsWithoutKey
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.preferences.Preference
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.preferences.SliderPreference
import helium314.keyboard.settings.preferences.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.keyboardNeedsReload
import helium314.keyboard.settings.preferences.BackupRestorePreference
import helium314.keyboard.settings.preferences.LayoutEditPreference
import helium314.keyboard.settings.preferences.LoadGestureLibPreference

@Composable
fun AdvancedSettingsScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    val items = listOfNotNull(
        Settings.PREF_ALWAYS_INCOGNITO_MODE,
        Settings.PREF_KEY_LONGPRESS_TIMEOUT,
        Settings.PREF_SPACE_HORIZONTAL_SWIPE,
        Settings.PREF_SPACE_VERTICAL_SWIPE,
        if (Settings.readHorizontalSpaceSwipe(prefs) == KeyboardActionListener.SWIPE_SWITCH_LANGUAGE
            || Settings.readVerticalSpaceSwipe(prefs) == KeyboardActionListener.SWIPE_SWITCH_LANGUAGE)
            Settings.PREF_LANGUAGE_SWIPE_DISTANCE else null,
        Settings.PREF_DELETE_SWIPE,
        Settings.PREF_SPACE_TO_CHANGE_LANG,
        Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD,
        Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY,
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) Settings.PREF_SHOW_SETUP_WIZARD_ICON else null,
        Settings.PREF_ABC_AFTER_SYMBOL_SPACE,
        Settings.PREF_ABC_AFTER_EMOJI,
        Settings.PREF_ABC_AFTER_CLIP,
        Settings.PREF_CUSTOM_CURRENCY_KEY,
        Settings.PREF_MORE_POPUP_KEYS,
        SettingsWithoutKey.CUSTOM_SYMBOLS_NUMBER_LAYOUTS,
        SettingsWithoutKey.CUSTOM_FUNCTIONAL_LAYOUTS,
        SettingsWithoutKey.BACKUP_RESTORE,
        if (BuildConfig.DEBUG || prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false)) SettingsWithoutKey.DEBUG_SETTINGS else null,
        R.string.settings_category_experimental,
        Settings.PREF_EMOJI_MAX_SDK,
        Settings.PREF_URL_DETECTION,
        if (BuildConfig.BUILD_TYPE != "nouserlib") SettingsWithoutKey.LOAD_GESTURE_LIB else null
    )
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_advanced),
        prefs = items
    )
}

@SuppressLint("ApplySharedPref")
fun createAdvancedSettings(context: Context) = listOf(
    Setting(context, Settings.PREF_ALWAYS_INCOGNITO_MODE,
        R.string.incognito, R.string.prefs_force_incognito_mode_summary)
    {
        SwitchPreference(it, false)
    },
    Setting(context, Settings.PREF_KEY_LONGPRESS_TIMEOUT, R.string.prefs_key_longpress_timeout_settings) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = 300,
            range = 100f..700f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, it.toString()) }
        )
    },
    Setting(context, Settings.PREF_SPACE_HORIZONTAL_SWIPE, R.string.show_horizontal_space_swipe) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(it, items, "move_cursor")
    },
    Setting(context, Settings.PREF_SPACE_VERTICAL_SWIPE, R.string.show_vertical_space_swipe) {
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(it, items, "none")
    },
    Setting(context, Settings.PREF_LANGUAGE_SWIPE_DISTANCE, R.string.prefs_language_swipe_distance) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = 5,
            range = 2f..18f,
            description = { it.toString() }
        )
    },
    Setting(context, Settings.PREF_DELETE_SWIPE, R.string.delete_swipe, R.string.delete_swipe_summary) {
        SwitchPreference(it, true)
    },
    Setting(context, Settings.PREF_SPACE_TO_CHANGE_LANG,
        R.string.prefs_long_press_keyboard_to_change_lang,
        R.string.prefs_long_press_keyboard_to_change_lang_summary)
    {
        SwitchPreference(it, true)
    },
    Setting(context, Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, R.string.prefs_long_press_symbol_for_numpad) {
        SwitchPreference(it, false)
    },
    Setting(context, Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, R.string.prefs_enable_emoji_alt_physical_key,
        R.string.prefs_enable_emoji_alt_physical_key_summary)
    {
        SwitchPreference(it, true)
    },
    Setting(context, Settings.PREF_SHOW_SETUP_WIZARD_ICON, R.string.prefs_enable_emoji_alt_physical_key_summary) {
        val ctx = LocalContext.current
        SwitchPreference(it, true) { SystemBroadcastReceiver.toggleAppIcon(ctx) }
    },
    Setting(context, Settings.PREF_ABC_AFTER_SYMBOL_SPACE,
        R.string.switch_keyboard_after, R.string.after_symbol_and_space)
    {
        SwitchPreference(it, true)
    },
    Setting(context, Settings.PREF_ABC_AFTER_EMOJI, R.string.switch_keyboard_after, R.string.after_emoji) {
        SwitchPreference(it, false)
    },
    Setting(context, Settings.PREF_ABC_AFTER_CLIP, R.string.switch_keyboard_after, R.string.after_clip) {
        SwitchPreference(it, false)
    },
    Setting(context, Settings.PREF_CUSTOM_CURRENCY_KEY, R.string.customize_currencies) { setting ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = setting.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            val prefs = LocalContext.current.prefs()
            TextInputDialog(
                onDismissRequest = { showDialog = false },
                textInputLabel = { Text(stringResource(R.string.customize_currencies_detail)) },
                initialText = prefs.getString(setting.key, "")!!,
                onConfirmed = { prefs.edit().putString(setting.key, it).apply(); KeyboardLayoutSet.onSystemLocaleChanged() },
                title = { Text(stringResource(R.string.customize_currencies)) },
                neutralButtonText = if (prefs.contains(setting.key)) stringResource(R.string.button_default) else null,
                onNeutral = { prefs.edit().remove(setting.key).apply(); KeyboardLayoutSet.onSystemLocaleChanged() },
                checkTextValid = { text -> text.splitOnWhitespace().none { it.length > 8 } }
            )
        }
    },
    Setting(context, Settings.PREF_MORE_POPUP_KEYS, R.string.show_popup_keys_title) {
        val items = listOf(
            stringResource(R.string.show_popup_keys_normal) to "normal",
            stringResource(R.string.show_popup_keys_main) to "main",
            stringResource(R.string.show_popup_keys_more) to "more",
            stringResource(R.string.show_popup_keys_all) to "all",
        )
        ListPreference(it, items, "main") { KeyboardLayoutSet.onSystemLocaleChanged() }
    },
    Setting(context, SettingsWithoutKey.CUSTOM_SYMBOLS_NUMBER_LAYOUTS, R.string.customize_symbols_number_layouts) { setting ->
        LayoutEditPreference(
            setting = setting,
            items = RawKeyboardParser.symbolAndNumberLayouts,
            getItemName = { it.getStringResourceOrName("layout_", LocalContext.current) },
            getDefaultLayout = { LocalContext.current.assets.list("layouts")?.firstOrNull { it.startsWith("$it.") } }
        )
    },
    Setting(context, SettingsWithoutKey.CUSTOM_FUNCTIONAL_LAYOUTS, R.string.customize_functional_key_layouts) { setting ->
        LayoutEditPreference(
            setting = setting,
            items = listOf(CUSTOM_FUNCTIONAL_LAYOUT_NORMAL, CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS, CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED)
                .map { it.substringBeforeLast(".") },
            getItemName = { it.substringAfter(CUSTOM_LAYOUT_PREFIX).getStringResourceOrName("layout_", LocalContext.current) },
            getDefaultLayout = { if (Settings.getInstance().isTablet) "functional_keys_tablet.json" else "functional_keys.json" }
        )
    },
    Setting(context, SettingsWithoutKey.BACKUP_RESTORE, R.string.backup_restore_title) {
        BackupRestorePreference(it)
    },
    Setting(context, SettingsWithoutKey.DEBUG_SETTINGS, R.string.debug_settings_title) {
        Preference(
            name = it.title,
            onClick = { SettingsDestination.navigateTo(SettingsDestination.Debug) }
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_arrow_left),
                modifier = Modifier.scale(-1f, 1f),
                contentDescription = null
            )
        }
    },
    Setting(context, Settings.PREF_EMOJI_MAX_SDK, R.string.prefs_key_emoji_max_sdk) { setting ->
        SliderPreference(
            name = setting.title,
            key = setting.key,
            default = Build.VERSION.SDK_INT,
            range = 21f..35f,
            description = {
                "Android " + when(it) {
                    21 -> "5.0"
                    22 -> "5.1"
                    23 -> "6"
                    24 -> "7.0"
                    25 -> "7.1"
                    26 -> "8.0"
                    27 -> "8.1"
                    28 -> "9"
                    29 -> "10"
                    30 -> "11"
                    31 -> "12"
                    32 -> "12L"
                    33 -> "13"
                    34 -> "14"
                    35 -> "15"
                    else -> "version unknown"
                }
            },
            onValueChanged =  { keyboardNeedsReload = true }
        )
    },
    Setting(context, Settings.PREF_URL_DETECTION, R.string.url_detection_title, R.string.url_detection_summary) {
        SwitchPreference(it, false)
    },
    Setting(context, SettingsWithoutKey.LOAD_GESTURE_LIB, R.string.load_gesture_library, R.string.load_gesture_library_summary) {
        LoadGestureLibPreference(it)
    },
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity.settingsContainer = SettingsContainer(LocalContext.current)
    Theme(true) {
        Surface {
            AdvancedSettingsScreen { }
        }
    }
}
