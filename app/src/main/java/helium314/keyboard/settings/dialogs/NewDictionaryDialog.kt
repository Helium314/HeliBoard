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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import helium314.keyboard.compat.locale
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.settings.USER_DICTIONARY_SUFFIX
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.prefs
import java.io.File
import java.util.Locale

@Composable
fun NewDictionaryDialog(
    onDismissRequest: () -> Unit,
    cachedFile: File,
    mainLocale: Locale?
) {
    val (error, header) = checkDict(cachedFile)
    if (error != null) {
        InfoDialog(stringResource(error), onDismissRequest)
        cachedFile.delete()
    } else if (header != null) {
        val ctx = LocalContext.current
        val dictLocale = header.mLocaleString.constructLocale()
        var locale by remember { mutableStateOf(mainLocale ?: dictLocale) }
        val enabledLanguages = SubtypeSettings.getEnabledSubtypes(ctx.prefs()).map { it.locale().language }
        val comparer = compareBy<Locale>({ it != mainLocale }, { it != dictLocale }, { it.language !in enabledLanguages }, { it.script() != dictLocale.script() })
        val locales = SubtypeSettings.getAvailableSubtypeLocales()
            .filter { it.script() == dictLocale.script() || it.script() == mainLocale?.script() }
            .sortedWith(comparer)
        val cacheDir = DictionaryInfoUtils.getAndCreateCacheDirectoryForLocale(locale, ctx)
        val dictFile = File(cacheDir, header.mIdString.substringBefore(":") + "_" + USER_DICTIONARY_SUFFIX)
        val type = header.mIdString.substringBefore(":")
        val info = header.info(ctx.resources.configuration.locale())
        ThreeButtonAlertDialog(
            onDismissRequest = { onDismissRequest(); cachedFile.delete() },
            onConfirmed = {
                dictFile.parentFile?.mkdirs()
                dictFile.delete()
                cachedFile.renameTo(dictFile)
                if (type == Dictionary.TYPE_MAIN) {
                    // replaced main dict, remove the one created from internal data
                    val internalMainDictFile = File(cacheDir, DictionaryInfoUtils.getExtractedMainDictFilename())
                    internalMainDictFile.delete()
                }
                val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
                ctx.sendBroadcast(newDictBroadcast)
            },
            text = {
                Column {
                    Text(info)
                    DropDownField(
                        selectedItem = locale,
                        onSelected = { locale = it },
                        items = locales
                    ) { Text(it.localizedDisplayName(ctx)) }
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
                        val oldInfo = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dictFile, 0, dictFile.length())?.info(ctx.resources.configuration.locale())
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

private fun checkDict(file: File): Pair<Int?, DictionaryHeader?> {
    val newHeader = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file, 0, file.length())
        ?: return R.string.dictionary_file_error to null

    val locale = newHeader.mLocaleString.constructLocale()
    val dict = ReadOnlyBinaryDictionary(file.absolutePath, 0, file.length(), false, locale, "test")
    if (!dict.isValidDictionary) {
        dict.close()
        return R.string.dictionary_load_error to null
    }
    return null to newHeader
}
