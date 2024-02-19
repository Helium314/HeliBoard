// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.settings.Settings
import java.io.File
import java.util.*
import kotlin.collections.HashSet

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
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    if (prefs.getBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, false) || locale.toString() == "zz")
        return
    val repositoryLink = "<a href='$DICTIONARY_URL'>" + context.getString(R.string.dictionary_link_text) + "</a>"
    val dictionaryLink = "<a href='$DICTIONARY_URL/src/branch/main/dictionaries/main_$locale.dict'>" + context.getString(
        R.string.dictionary_link_text) + "</a>"

    val message = SpannableStringUtils.fromHtml(context.getString(
        R.string.no_dictionary_message,
        repositoryLink,
        locale.toString(),
        dictionaryLink,
    ))
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.no_dictionaries_available)
        .setMessage(message)
        .setNegativeButton(R.string.dialog_close, null)
        .setNeutralButton(R.string.no_dictionary_dont_show_again_button) { _, _ ->
            prefs.edit { putBoolean(Settings.PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG, true) }
        }
        .create()
    dialog.show()
    (dialog.findViewById<View>(android.R.id.message) as? TextView)?.movementMethod = LinkMovementMethod.getInstance()
}

fun cleanUnusedMainDicts(context: Context) {
    val dictionaryDir = File(DictionaryInfoUtils.getWordListCacheDirectory(context))
    val dirs = dictionaryDir.listFiles() ?: return
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    val usedLocaleLanguageTags = hashSetOf<String>()
    getEnabledSubtypes(prefs).forEach { subtype ->
        val locale = subtype.locale()
        usedLocaleLanguageTags.add(locale.toLanguageTag())
        Settings.getSecondaryLocales(prefs, locale).forEach { usedLocaleLanguageTags.add(it.toLanguageTag()) }
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
