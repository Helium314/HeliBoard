// SPDX-License-Identifier: GPL-3.0-only

package org.oscar.kb.latin.utils

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import org.oscar.kb.R
import org.oscar.kb.compat.locale
import org.oscar.kb.latin.common.LocaleUtils
import org.oscar.kb.latin.common.LocaleUtils.constructLocale
import org.oscar.kb.latin.settings.Settings
import org.oscar.kb.latin.utils.DeviceProtectedUtils
import org.oscar.kb.latin.utils.DictionaryInfoUtils
import org.oscar.kb.latin.utils.SpannableStringUtils
import java.io.File
import java.util.*
import kotlin.collections.HashSet

fun getDictionaryLocales(context: Context): MutableSet<Locale> {
    val locales = HashSet<Locale>()

    // get cached dictionaries: extracted or user-added dictionaries
    _root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.getCachedDirectoryList(context)?.forEach { directory ->
        if (!directory.isDirectory) return@forEach
        if (!hasAnythingOtherThanExtractedMainDictionary(directory)) return@forEach
        val locale = _root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.getWordListIdFromFileName(directory.name).constructLocale()
        locales.add(locale)
    }
    // get assets dictionaries
    val assetsDictionaryList = _root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.getAssetsDictionaryList(context)
    if (assetsDictionaryList != null) {
        for (dictionary in assetsDictionaryList) {
            val locale = _root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(dictionary)?.constructLocale() ?: continue
            locales.add(locale)
        }
    }
    return locales
}

fun showMissingDictionaryDialog(context: Context, locale: Locale) {
    val prefs = _root_ide_package_.org.oscar.kb.latin.utils.DeviceProtectedUtils.getSharedPreferences(context)
    if (prefs.getBoolean(_root_ide_package_.org.oscar.kb.latin.settings.Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, false) || locale.toString() == "zz")
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

    val messageSpannable = _root_ide_package_.org.oscar.kb.latin.utils.SpannableStringUtils.fromHtml(message)
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.no_dictionaries_available)
        .setMessage(messageSpannable)
        .setNegativeButton(R.string.dialog_close, null)
        .setNeutralButton(R.string.no_dictionary_dont_show_again_button) { _, _ ->
            prefs.edit { putBoolean(_root_ide_package_.org.oscar.kb.latin.settings.Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, true) }
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

fun cleanUnusedMainDicts(context: Context) {
    val dictionaryDir = File(_root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.getWordListCacheDirectory(context))
    val dirs = dictionaryDir.listFiles() ?: return
    val prefs = _root_ide_package_.org.oscar.kb.latin.utils.DeviceProtectedUtils.getSharedPreferences(context)
    val usedLocaleLanguageTags = hashSetOf<String>()
    getEnabledSubtypes(prefs).forEach { subtype ->
        val locale = subtype.locale()
        usedLocaleLanguageTags.add(locale.toLanguageTag())
        _root_ide_package_.org.oscar.kb.latin.settings.Settings.getSecondaryLocales(prefs, locale).forEach { usedLocaleLanguageTags.add(it.toLanguageTag()) }
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
    dir.listFiles()?.any { it.name != _root_ide_package_.org.oscar.kb.latin.utils.DictionaryInfoUtils.getExtractedMainDictFilename() } != false

const val DICTIONARY_URL = "https://codeberg.org/Helium314/aosp-dictionaries"
const val DICTIONARY_DOWNLOAD_SUFFIX = "/src/branch/main/"
const val DICTIONARY_NORMAL_SUFFIX = "dictionaries/"
const val DICTIONARY_EXPERIMENTAL_SUFFIX = "dictionaries_experimental/"
