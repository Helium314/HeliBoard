// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.core.content.edit
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.R
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
    DictionaryInfoUtils.getCachedDirectoryList(context)?.forEach { directory ->
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

fun showMissingDictionaryDialog(context: Context, locale: Locale) {
    val prefs = context.prefs()
    if (prefs.getBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, Defaults.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG)
        || locale.toString() == SubtypeLocaleUtils.NO_LANGUAGE)
        return
    val repositoryLink = "<a href='$DICTIONARY_URL'>" + context.getString(R.string.dictionary_link_text) + "</a>"
    val dictionaryLink = "<a href='$DICTIONARY_URL/src/branch/main/dictionaries/main_$locale.dict'>" + context.getString(
        R.string.dictionary_link_text) + "</a>"
    val startMessage = context.getString( // todo: now with the available dicts csv, is the full text still necessary?
        R.string.no_dictionary_message,
        repositoryLink,
        locale.toString(), // toString because that's how default AOSP dictionaries are named
        dictionaryLink,
    )
    val message = createDictionaryTextHtml(startMessage, locale, context)

    val messageSpannable = SpannableStringUtils.fromHtml(message)
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.no_dictionaries_available)
        .setMessage(messageSpannable)
        .setNegativeButton(R.string.dialog_close, null)
        .setNeutralButton(R.string.no_dictionary_dont_show_again_button) { _, _ ->
            prefs.edit { putBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, true) }
        }
        .create()
    dialog.show()
    (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
}

/** returns the [message], and if dictionaries for [locale] or language are available, a links to them */
fun createDictionaryTextHtml(message: String, locale: Locale, context: Context): String {
    val knownDicts = mutableListOf<String>()
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
        val dictBaseUrl = DICTIONARY_URL + DICTIONARY_DOWNLOAD_SUFFIX +
                if (experimental.isEmpty()) DICTIONARY_NORMAL_SUFFIX else DICTIONARY_EXPERIMENTAL_SUFFIX
        val dictLink = dictBaseUrl + type + "_" + localeString.lowercase() + ".dict"
        val fullText = "<li><a href='$dictLink'>$dictString</a></li>"
        knownDicts.add(fullText)
    }
    if (knownDicts.isEmpty()) return message
    return """
            <p>$message</p>
            <b>${context.getString(R.string.dictionary_available)}</b>
            <ul>
            ${knownDicts.joinToString("\n")}
            </ul>
        """.trimIndent()
}

// why is this so horrible with annotated string?
@Composable
fun MissingDictionaryDialog(onDismissRequest: () -> Unit, locale: Locale) {
    val prefs = LocalContext.current.prefs()
    val availableDicts = createDictionaryTextAnnotated(locale)
    val dictLink = "$DICTIONARY_URL/src/branch/main/dictionaries/main_$locale.dict"
    val message = stringResource(R.string.no_dictionary_message, "§repl1§", locale.toString(), "§repl2§")
        .replace("<br>", "\n") // compose doesn't understand html... // todo: modify the string?

    // this relies on the order and thus is fragile, but so far it's fine with all translations
    val part1 = message.substringBefore("§repl1§")
    val part2 = message.substringBefore("§repl2§").substringAfter("§repl1§")
    val part3 = message.substringAfter("§repl2§")

    val annotatedString = buildAnnotatedString {
        append(part1)
        appendLink(stringResource(R.string.dictionary_link_text), DICTIONARY_URL)
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
        val dictBaseUrl = DICTIONARY_URL + DICTIONARY_DOWNLOAD_SUFFIX +
                if (experimental.isEmpty()) DICTIONARY_NORMAL_SUFFIX else DICTIONARY_EXPERIMENTAL_SUFFIX
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
    val prefs = context.prefs()
    val usedLocaleLanguageTags = hashSetOf<String>()
    SubtypeSettings.getEnabledSubtypes(prefs).forEach { subtype ->
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
    dir.listFiles()?.any { it.name != DictionaryInfoUtils.getExtractedMainDictFilename() } != false

const val DICTIONARY_URL = "https://codeberg.org/Helium314/aosp-dictionaries"
const val DICTIONARY_DOWNLOAD_SUFFIX = "/src/branch/main/"
const val DICTIONARY_NORMAL_SUFFIX = "dictionaries/"
const val DICTIONARY_EXPERIMENTAL_SUFFIX = "dictionaries_experimental/"
