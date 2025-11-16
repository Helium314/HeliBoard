// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings.dialogs

import android.content.Intent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.compat.locale
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.dictionary.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.settings.DropDownField
import helium314.keyboard.settings.WithSmallTitle
import java.io.File
import java.util.Locale
import androidx.compose.ui.platform.LocalConfiguration
import helium314.keyboard.latin.dictionary.UserAddedDictionary
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NewDictionaryDialog(
    onDismissRequest: () -> Unit,
    cachedFile: File,
    mainLocale: Locale?,
    header: DictionaryHeader,
    isTextFile: Boolean
) {
    if (!isTextFile && !canLoadDictionary(cachedFile)) {
        InfoDialog(stringResource(R.string.dictionary_load_error), onDismissRequest)
        cachedFile.delete()
    } else {
        val ctx = LocalContext.current
        val dictLocale = header.mLocaleString.constructLocale()
        val enabledLanguages = SubtypeSettings.getEnabledSubtypes().map { it.locale().language }
        val comparer = compareBy<Locale>({ it != mainLocale }, { it != dictLocale }, { it.language !in enabledLanguages }, { it.script() != dictLocale.script() })
        val locales = SubtypeSettings.getAvailableSubtypeLocales()
            .filter { it.script() == dictLocale.script() || it.script() == mainLocale?.script() }
            .sortedWith(comparer)
        var locale by remember { mutableStateOf(mainLocale ?: dictLocale.takeIf { it in locales } ?: locales.first()) }
        val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, ctx)
        val dictFile = File(cacheDir, header.mIdString.substringBefore(":") + "_" + DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)
        val type = header.mIdString.substringBefore(":")
        val info = header.info(LocalConfiguration.current.locale())
        var showWait by rememberSaveable { mutableStateOf<String?>(null) }
        if (showWait != null)
            InfoDialog(showWait.toString()) { } // no way to cancel
        ThreeButtonAlertDialog(
            onDismissRequest = { onDismissRequest(); cachedFile.delete() },
            onConfirmed = {
                dictFile.parentFile?.mkdirs()
                dictFile.delete()
                fun finish() {
                    if (type == Dictionary.TYPE_MAIN) {
                        // replaced main dict, remove the one created from internal data
                        val internalMainDictFile = File(cacheDir, DictionaryInfoUtils.MAIN_DICT_FILE_NAME)
                        internalMainDictFile.delete()
                    }
                    val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
                    ctx.sendBroadcast(newDictBroadcast)
                    onDismissRequest()
                }
                if (isTextFile) {
                    showWait = "please wait"
                    val dict = UserAddedDictionary(ctx, locale, type)
                    dict.setContents(cachedFile.readLines())
                    // maybe this shit is not necessary, but if we don't do it the user doesn't know about failed imports
                    GlobalScope.launch {
                        var wait = 0
                        while (dict.content != null && wait < 3000) {
                            delay(100)
                            wait++
                            showWait = "please wait, ${dict.added} words added, failed for ${dict.failed} words"
                        }
                        dict.onFinishInput()
                        showWait = null
                        cachedFile.delete()
                        finish()
                    }
                } else {
                    cachedFile.renameTo(dictFile)
                    finish()
                }
            },
            confirmDismissesDialog = false,
            confirmButtonText = stringResource(if (dictFile.exists()) R.string.replace_dictionary else android.R.string.ok),
            title = { Text(stringResource(R.string.add_new_dictionary_title)) },
            content = {
                Column {
                    Text(info, Modifier.padding(bottom = 10.dp))
                    WithSmallTitle(stringResource(R.string.button_select_language)) {
                        DropDownField(
                            selectedItem = locale,
                            onSelected = { locale = it },
                            items = locales
                        ) { Text(it.localizedDisplayName(ctx.resources)) }
                    }
                    if (locale.script() != dictLocale.script()) {
                        // whatever, still allow it if the user wants
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.dictionary_file_wrong_script),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(bottom = 8.dp, top = 4.dp)
                        )
                    }
                    if (dictFile.exists()) {
                        val oldInfo = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dictFile, 0, dictFile.length())?.info(LocalConfiguration.current.locale())
                        HorizontalDivider()
                        Text(
                            stringResource(R.string.replace_dictionary_message, type, oldInfo ?: "(no info)", info),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        )
    }
}

private fun canLoadDictionary(file: File): Boolean {
    val newHeader = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file, 0, file.length()) ?: return false
    val locale = newHeader.mLocaleString.constructLocale()
    val dict = ReadOnlyBinaryDictionary(file.absolutePath, 0, file.length(), false, locale, "test")
    val isValid = dict.isValidDictionary
    dict.close()
    return isValid
}
