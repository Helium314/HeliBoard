package helium314.keyboard.settings.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
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
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.keyboard_parser.RawKeyboardParser
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SystemBroadcastReceiver
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_NORMAL
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.getCustomLayoutFiles
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.settings.AllPrefs
import helium314.keyboard.settings.ListPreference
import helium314.keyboard.settings.NonSettingsPrefs
import helium314.keyboard.settings.PrefDef
import helium314.keyboard.settings.Preference
import helium314.keyboard.settings.PreferenceCategory
import helium314.keyboard.settings.SearchPrefScreen
import helium314.keyboard.settings.SettingsActivity2
import helium314.keyboard.settings.SettingsDestination
import helium314.keyboard.settings.SliderPreference
import helium314.keyboard.settings.SwitchPreference
import helium314.keyboard.settings.Theme
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.CustomizeLayoutDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.keyboardNeedsReload
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException

@Composable
fun AdvancedSettingsScreen(
    onClickBack: () -> Unit,
) {
    val prefs = LocalContext.current.prefs()
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_advanced),
    ) {
        SettingsActivity2.allPrefs.map[Settings.PREF_ALWAYS_INCOGNITO_MODE]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_KEY_LONGPRESS_TIMEOUT]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_SPACE_HORIZONTAL_SWIPE]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_SPACE_VERTICAL_SWIPE]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_DELETE_SWIPE]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_SPACE_TO_CHANGE_LANG]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY]!!.Preference()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q)
            SettingsActivity2.allPrefs.map[Settings.PREF_SHOW_SETUP_WIZARD_ICON]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_ABC_AFTER_SYMBOL_SPACE]!!.Preference() // todo: this is ugly
        SettingsActivity2.allPrefs.map[Settings.PREF_ABC_AFTER_EMOJI]!!.Preference() // todo: this is ugly
        SettingsActivity2.allPrefs.map[Settings.PREF_ABC_AFTER_CLIP]!!.Preference() // todo: this is ugly
        SettingsActivity2.allPrefs.map[Settings.PREF_CUSTOM_CURRENCY_KEY]!!.Preference()
        SettingsActivity2.allPrefs.map[Settings.PREF_MORE_POPUP_KEYS]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.CUSTOM_SYMBOLS_NUMBER_LAYOUTS]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.CUSTOM_FUNCTIONAL_LAYOUTS]!!.Preference()
        SettingsActivity2.allPrefs.map[NonSettingsPrefs.BACKUP_RESTORE]!!.Preference()
        if (BuildConfig.DEBUG || prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false))
            SettingsActivity2.allPrefs.map[NonSettingsPrefs.DEBUG_SETTINGS]!!.Preference() // todo: maybe move to main screen?
        PreferenceCategory(
            stringResource(R.string.settings_category_experimental)
        ) {
            SettingsActivity2.allPrefs.map[Settings.PREF_EMOJI_MAX_SDK]!!.Preference()
            SettingsActivity2.allPrefs.map[Settings.PREF_URL_DETECTION]!!.Preference()
            if (BuildConfig.BUILD_TYPE != "nouserlib")
                SettingsActivity2.allPrefs.map[NonSettingsPrefs.LOAD_GESTURE_LIB]!!.Preference()
        }
    }
}

@SuppressLint("ApplySharedPref")
fun createAdvancedPrefs(context: Context) = listOf(
    PrefDef(context, Settings.PREF_ALWAYS_INCOGNITO_MODE, R.string.incognito, R.string.prefs_force_incognito_mode_summary) {
        SwitchPreference(
            def = it,
            default = false
        )
    },
    PrefDef(context, Settings.PREF_KEY_LONGPRESS_TIMEOUT, R.string.prefs_key_longpress_timeout_settings) {
        SliderPreference(
            name = it.title,
            pref = it.key,
            default = 300,
            range = 100f..700f,
            description = { stringResource(R.string.abbreviation_unit_milliseconds, it.toString()) }
        )
    },
    PrefDef(context, Settings.PREF_SPACE_HORIZONTAL_SWIPE, R.string.show_horizontal_space_swipe) { def ->
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(def, items, "move_cursor")
    },
    PrefDef(context, Settings.PREF_SPACE_VERTICAL_SWIPE, R.string.show_vertical_space_swipe) { def ->
        val items = listOf(
            stringResource(R.string.space_swipe_move_cursor_entry) to "move_cursor",
            stringResource(R.string.switch_language) to "switch_language",
            stringResource(R.string.space_swipe_toggle_numpad_entry) to "toggle_numpad",
            stringResource(R.string.action_none) to "none",
        )
        ListPreference(def, items, "none")
    },
    PrefDef(context, Settings.PREF_DELETE_SWIPE, R.string.delete_swipe, R.string.delete_swipe_summary) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREF_SPACE_TO_CHANGE_LANG, R.string.prefs_long_press_keyboard_to_change_lang, R.string.prefs_long_press_keyboard_to_change_lang_summary) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, R.string.prefs_long_press_symbol_for_numpad) {
        SwitchPreference(
            def = it,
            default = false
        )
    },
    PrefDef(context, Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, R.string.prefs_enable_emoji_alt_physical_key, R.string.prefs_enable_emoji_alt_physical_key_summary) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREF_SHOW_SETUP_WIZARD_ICON, R.string.prefs_enable_emoji_alt_physical_key_summary) {
        val ctx = LocalContext.current
        SwitchPreference(
            def = it,
            default = true
        ) { SystemBroadcastReceiver.toggleAppIcon(ctx) }
    },
    PrefDef(context, Settings.PREF_ABC_AFTER_SYMBOL_SPACE, R.string.switch_keyboard_after, R.string.after_symbol_and_space) {
        SwitchPreference(
            def = it,
            default = true
        )
    },
    PrefDef(context, Settings.PREF_ABC_AFTER_EMOJI, R.string.switch_keyboard_after, R.string.after_emoji) {
        SwitchPreference(
            def = it,
            default = false
        )
    },
    PrefDef(context, Settings.PREF_ABC_AFTER_CLIP, R.string.switch_keyboard_after, R.string.after_clip) {
        SwitchPreference(
            def = it,
            default = false
        )
    },
    PrefDef(context, Settings.PREF_CUSTOM_CURRENCY_KEY, R.string.customize_currencies) { def ->
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            val prefs = LocalContext.current.prefs()
            TextInputDialog(
                onDismissRequest = { showDialog = false },
                textInputLabel = { Text(stringResource(R.string.customize_currencies_detail)) },
                initialText = prefs.getString(def.key, "")!!,
                onConfirmed = { prefs.edit().putString(def.key, it).apply(); KeyboardLayoutSet.onSystemLocaleChanged() },
                title = { Text(stringResource(R.string.customize_currencies)) },
                neutralButtonText = if (prefs.contains(def.key)) stringResource(R.string.button_default) else null,
                onNeutral = { prefs.edit().remove(def.key).apply(); KeyboardLayoutSet.onSystemLocaleChanged() },
                checkTextValid = { it.splitOnWhitespace().none { it.length > 8 } }
            )
        }
    },
    PrefDef(context, Settings.PREF_MORE_POPUP_KEYS, R.string.show_popup_keys_title) { def ->
        val items = listOf(
            stringResource(R.string.show_popup_keys_normal) to "normal",
            stringResource(R.string.show_popup_keys_main) to "main",
            stringResource(R.string.show_popup_keys_more) to "more",
            stringResource(R.string.show_popup_keys_all) to "all",
        )
        ListPreference(def, items, "main") { KeyboardLayoutSet.onSystemLocaleChanged() }
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_SYMBOLS_NUMBER_LAYOUTS, R.string.customize_symbols_number_layouts) { def ->
        var showDialog by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        var layout: String? by remember { mutableStateOf(null) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            ListPickerDialog(
                onDismissRequest = { showDialog = false },
                showRadioButtons = false,
                confirmImmediately = true,
                items = RawKeyboardParser.symbolAndNumberLayouts,
                getItemName = { it.getStringResourceOrName("layout_", ctx) },
                onItemSelected = { layout = it },
                title = { Text(def.title) }
            )
        }
        if (layout != null) {
            val customLayoutName = getCustomLayoutFiles(ctx).firstOrNull { it.name.startsWith("$CUSTOM_LAYOUT_PREFIX$layout.")}?.name
            val originalLayout = if (customLayoutName != null) null
            else {
                ctx.assets.list("layouts")?.firstOrNull { it.startsWith("$layout.") }
                    ?.let { ctx.assets.open("layouts" + File.separator + it).reader().readText() }
            }
            CustomizeLayoutDialog(
                layoutName = customLayoutName ?: "$CUSTOM_LAYOUT_PREFIX$layout.",
                startContent = originalLayout,
                displayName = layout?.getStringResourceOrName("layout_", ctx),
                onDismissRequest = { layout = null}
            )
        }
    },
    PrefDef(context, NonSettingsPrefs.CUSTOM_FUNCTIONAL_LAYOUTS, R.string.customize_functional_key_layouts) { def ->
        var showDialog by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        var layout: String? by remember { mutableStateOf(null) }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            ListPickerDialog(
                onDismissRequest = { showDialog = false },
                showRadioButtons = false,
                confirmImmediately = true,
                items = listOf(CUSTOM_FUNCTIONAL_LAYOUT_NORMAL, CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS, CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED)
                    .map { it.substringBeforeLast(".") },
                getItemName = { it.substringAfter(CUSTOM_LAYOUT_PREFIX).getStringResourceOrName("layout_", ctx) },
                onItemSelected = { layout = it },
                title = { Text(def.title) }
            )
        }
        if (layout != null) {
            val customLayoutName = getCustomLayoutFiles(ctx).map { it.name }
                .firstOrNull { it.startsWith("$layout.") }
            val originalLayout = if (customLayoutName != null) null
            else {
                val defaultLayoutName = if (Settings.getInstance().isTablet) "functional_keys_tablet.json" else "functional_keys.json"
                ctx.assets.open("layouts" + File.separator + defaultLayoutName).reader().readText()
            }
            CustomizeLayoutDialog(
                layoutName = customLayoutName ?: "$CUSTOM_LAYOUT_PREFIX$layout.",
                startContent = originalLayout,
                displayName = layout?.substringAfter(CUSTOM_LAYOUT_PREFIX)?.getStringResourceOrName("layout_", ctx),
                onDismissRequest = { layout = null}
            )
        }
    },
    PrefDef(context, NonSettingsPrefs.BACKUP_RESTORE, R.string.backup_restore_title) {
        var showDialog by remember { mutableStateOf(false) }
        Preference(
            name = it.title,
            onClick = { showDialog = true }
        )
//        if (showDialog) todo: show the dialog
    },
    PrefDef(context, NonSettingsPrefs.DEBUG_SETTINGS, R.string.debug_settings_title) {
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
    PrefDef(context, Settings.PREF_EMOJI_MAX_SDK, R.string.prefs_key_emoji_max_sdk) {
        SliderPreference(
            name = it.title,
            pref = it.key,
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
    PrefDef(context, Settings.PREF_URL_DETECTION, R.string.url_detection_title, R.string.url_detection_summary) {
        SwitchPreference(
            def = it,
            default = false
        )
    },
    PrefDef(context, NonSettingsPrefs.LOAD_GESTURE_LIB, R.string.load_gesture_library, R.string.load_gesture_library_summary) {
        var showDialog by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        val abi = Build.SUPPORTED_ABIS[0]
        val libFile = File(ctx.filesDir.absolutePath + File.separator + JniUtils.JNI_LIB_IMPORT_FILE_NAME)
        fun renameToLibfileAndRestart(file: File, checksum: String) {
            libFile.delete()
            // store checksum in default preferences (soo JniUtils)
            prefs.edit().putString(Settings.PREF_LIBRARY_CHECKSUM, checksum).commit()
            file.renameTo(libFile)
            Runtime.getRuntime().exit(0) // exit will restart the app, so library will be loaded
        }
        var tempFilePath: String? by remember { mutableStateOf(null) }
        val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: return@rememberLauncherForActivityResult
            val tmpfile = File(ctx.filesDir.absolutePath + File.separator + "tmplib")
            try {
                val otherTemporaryFile = File(ctx.filesDir.absolutePath + File.separator + "tmpfile")
                FileUtils.copyContentUriToNewFile(uri, ctx, otherTemporaryFile)
                val inputStream = FileInputStream(otherTemporaryFile)
                val outputStream = FileOutputStream(tmpfile)
                outputStream.use {
                    tmpfile.setReadOnly() // as per recommendations in https://developer.android.com/about/versions/14/behavior-changes-14#safer-dynamic-code-loading
                    FileUtils.copyStreamToOtherStream(inputStream, it)
                }
                otherTemporaryFile.delete()

                val checksum = ChecksumCalculator.checksum(tmpfile.inputStream()) ?: ""
                if (checksum == JniUtils.expectedDefaultChecksum()) {
                    renameToLibfileAndRestart(tmpfile, checksum)
                } else {
                    tempFilePath = tmpfile.absolutePath
                    AlertDialog.Builder(ctx)
                        .setMessage(ctx.getString(R.string.checksum_mismatch_message, abi))
                        .setPositiveButton(android.R.string.ok) { _, _ -> renameToLibfileAndRestart(tmpfile, checksum) }
                        .setNegativeButton(android.R.string.cancel) { _, _ -> tmpfile.delete() }
                        .show()
                }
            } catch (e: IOException) {
                tmpfile.delete()
                // should inform user, but probably the issues will only come when reading the library
            }
        }
        Preference(
            name = it.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            ConfirmationDialog(
                onDismissRequest = { showDialog = false },
                onConfirmed = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/octet-stream")
                    launcher.launch(intent)
                },
                title = { Text(stringResource(R.string.load_gesture_library)) },
                text = { Text(stringResource(R.string.load_gesture_library_message, abi)) },
                neutralButtonText = if (libFile.exists()) stringResource(R.string.load_gesture_library_button_delete) else null,
                onNeutral = {
                    libFile.delete()
                    prefs.edit().remove(Settings.PREF_LIBRARY_CHECKSUM).commit()
                    Runtime.getRuntime().exit(0)
                }
            )
        }
        if (tempFilePath != null)
            ConfirmationDialog(
                onDismissRequest = {
                    File(tempFilePath).delete()
                    tempFilePath = null
                },
                text = { Text(stringResource(R.string.checksum_mismatch_message, abi))},
                onConfirmed = {
                    val tempFile = File(tempFilePath)
                    renameToLibfileAndRestart(tempFile, ChecksumCalculator.checksum(tempFile.inputStream()) ?: "")
                }
            )
    },
)

@Preview
@Composable
private fun Preview() {
    SettingsActivity2.allPrefs = AllPrefs(LocalContext.current)
    Theme(true) {
        Surface {
            AdvancedSettingsScreen { }
        }
    }
}
