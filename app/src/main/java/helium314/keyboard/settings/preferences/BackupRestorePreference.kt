// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.preferences

import android.app.Activity
import android.content.Intent
import android.content.SharedPreferences
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMBER
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMPAD
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_NUMPAD_LANDSCAPE
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_PHONE
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_PHONE_SYMBOLS
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS_ARABIC
import helium314.keyboard.keyboard.internal.keyboard_parser.LAYOUT_SYMBOLS_SHIFTED
import helium314.keyboard.latin.R
import helium314.keyboard.latin.checkVersionUpgrade
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.ExecutorUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.getActivity
import helium314.keyboard.latin.utils.onCustomLayoutFileListChanged
import helium314.keyboard.latin.utils.prefs
import helium314.keyboard.latin.utils.protectedPrefs
import helium314.keyboard.latin.utils.reloadEnabledSubtypes
import helium314.keyboard.latin.utils.updateAdditionalSubtypes
import helium314.keyboard.settings.Setting
import helium314.keyboard.settings.SettingsActivity
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import helium314.keyboard.settings.dialogs.InfoDialog
import helium314.keyboard.settings.keyboardNeedsReload
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Composable
fun BackupRestorePreference(setting: Setting) {
    var showDialog by rememberSaveable { mutableStateOf(false) }
    val ctx = LocalContext.current
    val prefs = ctx.prefs()
    var error: String? by rememberSaveable { mutableStateOf(null) }
    val backupFilePatterns by lazy { listOf(
        "blacklists/.*\\.txt".toRegex(),
        "layouts/$CUSTOM_LAYOUT_PREFIX+\\..{0,4}".toRegex(), // can't expect a period at the end, as this would break restoring older backups
        "dicts/.*/.*user\\.dict".toRegex(),
        "UserHistoryDictionary.*/UserHistoryDictionary.*\\.(body|header)".toRegex(),
        "custom_background_image.*".toRegex(),
        "custom_font".toRegex(),
    ) }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
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
                    settingsToJsonStream(ctx.protectedPrefs().all, zipStream)
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
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
        val uri = result.data?.data ?: return@rememberLauncherForActivityResult
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
                                val protectedPrefs = ctx.protectedPrefs()
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
        val additionalSubtypes = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)
        updateAdditionalSubtypes(AdditionalSubtypeUtils.createAdditionalSubtypesArray(additionalSubtypes))
        reloadEnabledSubtypes(ctx)
        val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
        ctx.getActivity()?.sendBroadcast(newDictBroadcast)
        onCustomLayoutFileListChanged()
        (ctx.getActivity() as? SettingsActivity)?.prefChanged?.value = 210 // for settings reload
        keyboardNeedsReload = true
    }
    Preference(name = setting.title, onClick = { showDialog = true })
    if (showDialog) {
        ConfirmationDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.backup_restore_title)) },
            text = { Text(stringResource(R.string.backup_restore_message)) },
            confirmButtonText = stringResource(R.string.button_backup),
            neutralButtonText = stringResource(R.string.button_restore),
            onNeutral = {
                showDialog = false
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/zip")
                restoreLauncher.launch(intent)
            },
            onConfirmed = {
                val currentDate = SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Calendar.getInstance().time)
                val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .putExtra(
                        Intent.EXTRA_TITLE,
                        ctx.getString(R.string.english_ime_name)
                            .replace(" ", "_") + "_backup_$currentDate.zip"
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
}

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

private const val PREFS_FILE_NAME = "preferences.json"
private const val PROTECTED_PREFS_FILE_NAME = "protected_preferences.json"
