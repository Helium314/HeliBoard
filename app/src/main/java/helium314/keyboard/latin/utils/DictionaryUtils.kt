// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.edit
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Links
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.settings.dialogs.ConfirmationDialog
import java.io.File
import java.util.Locale

fun getDictionaryLocales(context: Context): MutableSet<Locale> {
    val locales = HashSet<Locale>()

    // get cached dictionaries: extracted or user-added dictionaries
    DictionaryInfoUtils.getCachedDirectoryList(context).forEach { directory ->
        if (!directory.isDirectory) return@forEach
        if (!hasAnythingOtherThanExtractedMainDictionary(directory)) return@forEach
        val locale = DictionaryInfoUtils.getWordListIdFromFileName(directory.name).constructLocale()
        locales.add(locale)
    }
    // get assets dictionaries
    val assetsDictionaryList = DictionaryInfoUtils.getAssetsDictionaryList(context)
    if (assetsDictionaryList != null) {
        for (dictionary in assetsDictionaryList) {
            val locale = DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dictionary)?.constructLocale() ?: continue
            locales.add(locale)
        }
    }
    return locales
}

// why is this so horrible with annotated string?
@Composable
fun MissingDictionaryDialog(onDismissRequest: () -> Unit, locale: Locale) {
    val prefs = LocalContext.current.prefs()
    if (prefs.getBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, Defaults.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG)) {
        onDismissRequest()
        return
    }
    val availableDicts = createDictionaryTextAnnotated(locale)
    val dictLink = "${Links.DICTIONARY_URL}/src/branch/main/dictionaries/main_$locale.dict"
    val message = stringResource(R.string.no_dictionary_message, "§repl1§", locale.toString(), "§repl2§")
        .replace("<br>", "\n") // compose doesn't understand html... // todo: modify the string?

    // this relies on the order and thus is fragile, but so far it's fine with all translations
    val part1 = message.substringBefore("§repl1§")
    val part2 = message.substringBefore("§repl2§").substringAfter("§repl1§")
    val part3 = message.substringAfter("§repl2§")

    val annotatedString = buildAnnotatedString {
        append(part1)
        appendLink(stringResource(R.string.dictionary_link_text), Links.DICTIONARY_URL)
        append(part2)
        appendLink(stringResource(R.string.dictionary_link_text), dictLink)
        append(part3)
        if (availableDicts.isNotEmpty()) {
            appendLine()
            appendLine()
            append(availableDicts)
        }
    }

    ConfirmationDialog(
        onDismissRequest = onDismissRequest,
        cancelButtonText = stringResource(R.string.dialog_close),
        onConfirmed = { prefs.edit { putBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, true) } },
        confirmButtonText = stringResource(R.string.no_dictionary_dont_show_again_button),
        content = { Text(annotatedString) }
    )
}

/** if dictionaries for [locale] or language are available returns links to them */
@Composable
fun createDictionaryTextAnnotated(locale: Locale): AnnotatedString {
    val knownDicts = mutableListOf<Pair<String, String>>()
    val builder = AnnotatedString.Builder()
    builder.appendLine(stringResource(R.string.dictionary_available))
    val context = LocalContext.current
    context.assets.open("dictionaries_in_dict_repo.csv").reader().forEachLine {
        if (it.isBlank()) return@forEachLine
        val (type, localeString, experimental) = it.split(",")
        // we use a locale string here because that's in the dictionaries repo
        // ideally the repo would switch to language tag, but not sure how this is handled in the dictionary header
        // further, the dicts in the dictionaries repo should be compatible with other AOSP-based keyboards
        val dictLocale = localeString.constructLocale()
        if (LocaleUtils.getMatchLevel(locale, dictLocale) < LocaleUtils.LOCALE_GOOD_MATCH) return@forEachLine
        val rawDictString = "$type: ${dictLocale.getDisplayName(context.resources.configuration.locale())}"
        val dictString = if (experimental.isEmpty()) rawDictString
        else context.getString(R.string.available_dictionary_experimental, rawDictString)
        val dictBaseUrl = Links.DICTIONARY_URL + Links.DICTIONARY_DOWNLOAD_SUFFIX +
                if (experimental.isEmpty()) Links.DICTIONARY_NORMAL_SUFFIX else Links.DICTIONARY_EXPERIMENTAL_SUFFIX
        val dictLink = dictBaseUrl + type + "_" + localeString.lowercase() + ".dict"
        knownDicts.add(dictString to dictLink)
    }
    if (knownDicts.isEmpty()) return AnnotatedString("")
    knownDicts.forEach {
        builder.append("\u2022 ") // bullet point as replacement for <ul>
        builder.appendLink(it.first , it.second)
        builder.appendLine()
    }
    return builder.toAnnotatedString()
}

fun cleanUnusedMainDicts(context: Context) {
    val dictionaryDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context))
    val dirs = dictionaryDir.listFiles() ?: return
    val usedLocaleLanguageTags = hashSetOf<String>()
    SubtypeSettings.getEnabledSubtypes().forEach { subtype ->
        val locale = subtype.locale()
        usedLocaleLanguageTags.add(locale.toLanguageTag())
    }
    SubtypeSettings.getAdditionalSubtypes().forEach { subtype ->
        getSecondaryLocales(subtype.extraValue).forEach { usedLocaleLanguageTags.add(it.toLanguageTag()) }
    }
    for (dir in dirs) {
        if (!dir.isDirectory) continue
        if (dir.name in usedLocaleLanguageTags) continue
        if (hasAnythingOtherThanExtractedMainDictionary(dir))
            continue
        dir.deleteRecursively()
    }
}

private fun hasAnythingOtherThanExtractedMainDictionary(dir: File) =
    dir.listFiles()?.any { it.name != DictionaryInfoUtils.MAIN_DICT_FILE_NAME } != false
