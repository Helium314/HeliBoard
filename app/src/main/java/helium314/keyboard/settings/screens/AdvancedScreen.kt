// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.screens

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
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
import androidx.preference.PreferenceManager
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMBER
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMPAD
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMPAD_LANDSCAPE
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_PHONE
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_PHONE_SYMBOLS
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS_ARABIC
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.keyboard.internal.keyboard_parser.RawKeyboardParser
import helium314.keyboard.latin.BuildConfig
import helium314.keyboard.latin.R
import helium314.keyboard.latin.SystemBroadcastReceiver
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.DebugSettings
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_NORMAL
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS
import helium314.keyboard.latin.utils.CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.ChecksumCalculator
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.getCustomLayoutFiles
import helium314.keyboard.latin.utils.getStringResourceOrName
import helium314.keyboard.latin.utils.onCustomLayoutFileListChanged
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.reloadEnabledSubtypes
import helium314.keyboard.latin.utils.updateAdditionalSubtypes
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
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.dialogs.ListPickerDialog
import helium314.keyboard.settings.dialogs.TextInputDialog
import helium314.keyboard.settings.keyboardNeedsReload
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

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
        NonSettingsPrefs.CUSTOM_SYMBOLS_NUMBER_LAYOUTS,
        NonSettingsPrefs.CUSTOM_FUNCTIONAL_LAYOUTS,
        NonSettingsPrefs.BACKUP_RESTORE,
        if (BuildConfig.DEBUG || prefs.getBoolean(DebugSettings.PREF_SHOW_DEBUG_SETTINGS, false)) NonSettingsPrefs.DEBUG_SETTINGS else null,
        R.string.settings_category_experimental,
        Settings.PREF_EMOJI_MAX_SDK,
        Settings.PREF_URL_DETECTION,
        if (BuildConfig.BUILD_TYPE != "nouserlib") NonSettingsPrefs.LOAD_GESTURE_LIB else null
    )
    SearchPrefScreen(
        onClickBack = onClickBack,
        title = stringResource(R.string.settings_screen_advanced),
        prefs = items
    )
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
    PrefDef(context, NonSettingsPrefs.BACKUP_RESTORE, R.string.backup_restore_title) { def ->
        var showDialog by remember { mutableStateOf(false) }
        val ctx = LocalContext.current
        val prefs = ctx.prefs()
        var error: String? by remember { mutableStateOf(null) }
        val backupFilePatterns by lazy { listOf(
            "blacklists/.*\\.txt".toRegex(),
            "layouts/$CUSTOM_LAYOUT_PREFIX+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
            "dicts/.*/.*user\\.dict".toRegex(),
            "UserHistoryDictionary.*/UserHistoryDictionary.*\\.(body|header)".toRegex(),
            "custom_background_image.*".toRegex(),
            "custom_font".toRegex(),
        ) }
        val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: return@rememberLauncherForActivityResult
            // zip all files matching the backup patterns
            // essentially this is the typed words information, and user-added dictionaries
            val filesDir = ctx.filesDir ?: return@rememberLauncherForActivityResult
            val filesPath = filesDir.path + File.separator
            val files = mutableListOf<File>()
            filesDir.walk().forEach { file ->
                val path = file.path.replace(filesPath, "")
                if (backupFilePatterns.any { path.matches(it) })
                    files.add(file)
            }
            val protectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx)
            val protectedFilesPath = protectedFilesDir.path + File.separator
            val protectedFiles = mutableListOf<File>()
            protectedFilesDir.walk().forEach { file ->
                val path = file.path.replace(protectedFilesPath, "")
                if (backupFilePatterns.any { path.matches(it) })
                    protectedFiles.add(file)
            }
            val wait = CountDownLatch(1)
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                try {
                    ctx.getActivity()?.contentResolver?.openOutputStream(uri)?.use { os ->
                        // write files to zip
                        val zipStream = ZipOutputStream(os)
                        files.forEach {
                            val fileStream = FileInputStream(it).buffered()
                            zipStream.putNextEntry(ZipEntry(it.path.replace(filesPath, "")))
                            fileStream.copyTo(zipStream, 1024)
                            fileStream.close()
                            zipStream.closeEntry()
                        }
                        protectedFiles.forEach {
                            val fileStream = FileInputStream(it).buffered()
                            zipStream.putNextEntry(ZipEntry(it.path.replace(protectedFilesDir.path, "unprotected")))
                            fileStream.copyTo(zipStream, 1024)
                            fileStream.close()
                            zipStream.closeEntry()
                        }
                        zipStream.putNextEntry(ZipEntry(PREFS_FILE_NAME))
                        settingsToJsonStream(prefs.all, zipStream)
                        zipStream.closeEntry()
                        zipStream.putNextEntry(ZipEntry(PROTECTED_PREFS_FILE_NAME))
                        settingsToJsonStream(PreferenceManager.getDefaultSharedPreferences(ctx).all, zipStream)
                        zipStream.closeEntry()
                        zipStream.close()
                    }
                } catch (t: Throwable) {
                    error = "b" + t.message
                    Log.w("AdvancedScreen", "error during backup", t)
                } finally {
                    wait.countDown()
                }
            }
            wait.await()
        }
        val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
            val uri = it.data?.data ?: return@rememberLauncherForActivityResult
            val wait = CountDownLatch(1)
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute {
                try {
                    ctx.getActivity()?.contentResolver?.openInputStream(uri)?.use { inputStream ->
                        ZipInputStream(inputStream).use { zip ->
                            var entry: ZipEntry? = zip.nextEntry
                            val filesDir = ctx.filesDir?.path ?: return@execute
                            val deviceProtectedFilesDir = DeviceProtectedUtils.getFilesDir(ctx).path
                            Settings.getInstance().stopListener()
                            while (entry != null) {
                                if (entry.name.startsWith("unprotected${File.separator}")) {
                                    val adjustedName = entry.name.substringAfter("unprotected${File.separator}")
                                    if (backupFilePatterns.any { adjustedName.matches(it) }) {
                                        val targetFileName = upgradeFileNames(adjustedName)
                                        val file = File(deviceProtectedFilesDir, targetFileName)
                                        FileUtils.copyStreamToNewFile(zip, file)
                                    }
                                } else if (backupFilePatterns.any { entry!!.name.matches(it) }) {
                                    val targetFileName = upgradeFileNames(entry.name)
                                    val file = File(filesDir, targetFileName)
                                    FileUtils.copyStreamToNewFile(zip, file)
                                } else if (entry.name == PREFS_FILE_NAME) {
                                    val prefLines = String(zip.readBytes()).split("\n")
                                    prefs.edit().clear().apply()
                                    readJsonLinesToSettings(prefLines, prefs)
                                } else if (entry.name == PROTECTED_PREFS_FILE_NAME) {
                                    val prefLines = String(zip.readBytes()).split("\n")
                                    val protectedPrefs = PreferenceManager.getDefaultSharedPreferences(ctx)
                                    protectedPrefs.edit().clear().apply()
                                    readJsonLinesToSettings(prefLines, protectedPrefs)
                                }
                                zip.closeEntry()
                                entry = zip.nextEntry
                            }
                        }
                    }
                } catch (t: Throwable) {
                    error = "r" + t.message
                    Log.w("AdvancedScreen", "error during restore", t)
                } finally {
                    wait.countDown()
                }
            }
            wait.await()
            checkVersionUpgrade(ctx)
            Settings.getInstance().startListener()
            val additionalSubtypes = Settings.readPrefAdditionalSubtypes(prefs, ctx.resources)
            updateAdditionalSubtypes(AdditionalSubtypeUtils.createAdditionalSubtypesArray(additionalSubtypes))
            reloadEnabledSubtypes(ctx)
            val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
            ctx.getActivity()?.sendBroadcast(newDictBroadcast)
            onCustomLayoutFileListChanged()
            (ctx.getActivity() as? SettingsActivity2)?.prefChanged?.value = 210 // for settings reload
            keyboardNeedsReload = true
        }
        Preference(
            name = def.title,
            onClick = { showDialog = true }
        )
        if (showDialog) {
            ConfirmationDialog(
                onDismissRequest = { showDialog = false },
                title = { Text(stringResource(R.string.backup_restore_title)) },
                text = { Text(stringResource(R.string.backup_restore_message)) },
                confirmButtonText = stringResource(R.string.button_backup),
                neutralButtonText = stringResource(R.string.button_restore),
                onNeutral = {
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .setType("application/zip")
                    restoreLauncher.launch(intent)
                },
                onConfirmed = {
                    val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                        .addCategory(Intent.CATEGORY_OPENABLE)
                        .putExtra(
                            Intent.EXTRA_TITLE,
                            ctx.getString(R.string.english_ime_name)
                                .replace(" ", "_") + "_backup.zip"
                        )
                        .setType("application/zip")
                    backupLauncher.launch(intent)
                }
            )
        }
        if (error != null) {
            InfoDialog(
                if (error!!.startsWith("b"))
                    stringResource(R.string.backup_error, error!!.drop(1))
                else stringResource(R.string.restore_error, error!!.drop(1))
            ) { error = null }
        }
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

// stuff for backup / restore
private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"

@Suppress("UNCHECKED_CAST") // it is checked... but whatever (except string set, because can't check for that))
private fun settingsToJsonStream(settings: Map<String?, Any?>, out: OutputStream) {
    val booleans = settings.filter { it.key is String && it.value is Boolean } as Map<String, Boolean>
    val ints = settings.filter { it.key is String && it.value is Int } as Map<String, Int>
    val longs = settings.filter { it.key is String && it.value is Long } as Map<String, Long>
    val floats = settings.filter { it.key is String && it.value is Float } as Map<String, Float>
    val strings = settings.filter { it.key is String && it.value is String } as Map<String, String>
    val stringSets = settings.filter { it.key is String && it.value is Set<*> } as Map<String, Set<String>>
    // now write
    out.write("boolean settings\n".toByteArray())
    out.write(Json.encodeToString(booleans).toByteArray())
    out.write("\nint settings\n".toByteArray())
    out.write(Json.encodeToString(ints).toByteArray())
    out.write("\nlong settings\n".toByteArray())
    out.write(Json.encodeToString(longs).toByteArray())
    out.write("\nfloat settings\n".toByteArray())
    out.write(Json.encodeToString(floats).toByteArray())
    out.write("\nstring settings\n".toByteArray())
    out.write(Json.encodeToString(strings).toByteArray())
    out.write("\nstring set settings\n".toByteArray())
    out.write(Json.encodeToString(stringSets).toByteArray())
}

private fun readJsonLinesToSettings(list: List<String>, prefs: SharedPreferences): Boolean {
    val i = list.iterator()
    val e = prefs.edit()
    try {
        while (i.hasNext()) {
            when (i.next()) {
                "boolean settings" -> Json.decodeFromString<Map<String, Boolean>>(i.next()).forEach { e.putBoolean(it.key, it.value) }
                "int settings" -> Json.decodeFromString<Map<String, Int>>(i.next()).forEach { e.putInt(it.key, it.value) }
                "long settings" -> Json.decodeFromString<Map<String, Long>>(i.next()).forEach { e.putLong(it.key, it.value) }
                "float settings" -> Json.decodeFromString<Map<String, Float>>(i.next()).forEach { e.putFloat(it.key, it.value) }
                "string settings" -> Json.decodeFromString<Map<String, String>>(i.next()).forEach { e.putString(it.key, it.value) }
                "string set settings" -> Json.decodeFromString<Map<String, Set<String>>>(i.next()).forEach { e.putStringSet(it.key, it.value) }
            }
        }
        e.apply()
        return true
    } catch (e: Exception) {
        return false
    }
}

// todo (later): remove this when new package name has been in use for long enough, this is only for migrating from old openboard name
private fun upgradeFileNames(originalName: String): String {
    return when {
        originalName.endsWith(USER_DICTIONARY_SUFFIX) -> {
            // replace directory after switch to language tag
            val dirName = originalName.substringAfter(File.separator).substringBefore(File.separator)
            originalName.replace(dirName, dirName.constructLocale().toLanguageTag())
        }
        originalName.startsWith("blacklists") -> {
            // replace file name after switch to language tag
            val fileName = originalName.substringAfter("blacklists${File.separator}").substringBefore(".txt")
            originalName.replace(fileName, fileName.constructLocale().toLanguageTag())
        }
        originalName.startsWith("layouts") -> {
            // replace file name after switch to language tag, but only if it's not a layout
            val localeString = originalName.substringAfter(".").substringBefore(".")
            if (localeString in listOf(LAYOUT_SYMBOLS, LAYOUT_SYMBOLS_SHIFTED, LAYOUT_SYMBOLS_ARABIC, LAYOUT_NUMBER, LAYOUT_NUMPAD, LAYOUT_NUMPAD_LANDSCAPE, LAYOUT_PHONE, LAYOUT_PHONE_SYMBOLS))
                return originalName // it's a layout!
            val locale = localeString.constructLocale()
            if (locale.toLanguageTag() != "und")
                originalName.replace(localeString, locale.toLanguageTag())
            else
                originalName // no valid locale -> must be symbols layout, don't change
        }
        originalName.startsWith("UserHistoryDictionary") -> {
            val localeString = originalName.substringAfter(".").substringBefore(".")
            val locale = localeString.constructLocale()
            originalName.replace(localeString, locale.toLanguageTag())
        }
        else -> originalName
    }
}
