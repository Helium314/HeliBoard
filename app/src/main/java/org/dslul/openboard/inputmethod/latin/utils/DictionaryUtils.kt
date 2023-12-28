// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.text.method.LinkMovementMethod
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.settings.getEnabledSubtypes
import org.dslul.openboard.inputmethod.latin.settings.locale
import java.io.File
import java.util.*
import kotlin.collections.HashSet

fun getDictionaryLocales(context: Context): MutableSet<Locale> {
    val locales = HashSet<Locale>()

    // get cached dictionaries: extracted or user-added dictionaries
    val cachedDirectoryList = DictionaryInfoUtils.getCachedDirectoryList(context)
    if (cachedDirectoryList != null) {
        for (directory in cachedDirectoryList) {
            if (!directory.isDirectory) continue
            if (!hasAnythingOtherThanExtractedMainDictionary(directory)) continue
            val dirLocale = DictionaryInfoUtils.getWordListIdFromFileName(directory.name)
            val locale = dirLocale.toLocale()
            locales.add(locale)
        }
    }
    // get assets dictionaries
    val assetsDictionaryList = BinaryDictionaryGetter.getAssetsDictionaryList(context)
    if (assetsDictionaryList != null) {
        for (dictionary in assetsDictionaryList) {
            val dictLocale =
                BinaryDictionaryGetter.extractLocaleFromAssetsDictionaryFile(dictionary)
                    ?: continue
            val locale = dictLocale.toLocale()
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
    val usedLocales = hashSetOf<String>()
    getEnabledSubtypes(prefs).forEach {
        val locale = it.locale()
        usedLocales.add(locale)
        Settings.getSecondaryLocales(prefs, locale).forEach { usedLocales.add(it.toString().lowercase()) }
    }
    for (dir in dirs) {
        if (!dir.isDirectory) continue
        if (dir.name in usedLocales) continue
        if (hasAnythingOtherThanExtractedMainDictionary(dir))
            continue
        dir.deleteRecursively()
    }
}

private fun hasAnythingOtherThanExtractedMainDictionary(dir: File) =
    dir.listFiles()?.any { it.name != DictionaryInfoUtils.getMainDictFilename(dir.name) } != false

const val DICTIONARY_URL = "https://codeberg.org/Helium314/aosp-dictionaries"
