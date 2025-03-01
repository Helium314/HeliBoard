// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin.utils

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import helium314.keyboard.compat.locale
import helium314.keyboard.dictionarypack.DictionaryPackConstants
import helium314.keyboard.latin.Dictionary
import helium314.keyboard.latin.R
import helium314.keyboard.latin.ReadOnlyBinaryDictionary
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.settings.*
import helium314.keyboard.latin.utils.ScriptUtils.script
import java.io.File
import java.io.IOException
import java.util.*

class NewDictionaryAdder(private val context: Context, private val onAdded: ((Boolean, File) -> Unit)?) {
    private val cachedDictionaryFile = File(context.cacheDir.path + File.separator + "temp_dict")

    fun addDictionary(uri: Uri?, mainLocale: Locale?) {
        if (uri == null)
            return onDictionaryLoadingError(R.string.dictionary_load_error)

        cachedDictionaryFile.delete()
        try {
            FileUtils.copyContentUriToNewFile(uri, context, cachedDictionaryFile)
        } catch (e: IOException) {
            return onDictionaryLoadingError(R.string.dictionary_load_error)
        }

        val newHeader = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(cachedDictionaryFile, 0, cachedDictionaryFile.length())
            ?: return onDictionaryLoadingError(R.string.dictionary_file_error)
        val locale = newHeader.mLocaleString.constructLocale()

        val dict = ReadOnlyBinaryDictionary(cachedDictionaryFile.absolutePath, 0, cachedDictionaryFile.length(), false, locale, "test")
        if (!dict.isValidDictionary) {
            dict.close()
            return onDictionaryLoadingError(R.string.dictionary_load_error)
        }

        if (mainLocale == null) {
            val localeName = LocaleUtils.getLocaleDisplayNameInSystemLocale(locale, context)
            val message = context.getString(R.string.add_new_dictionary_ask_locale,
                newHeader.mIdString.substringBefore(":"),
                localeName
            )
            val b = AlertDialog.Builder(context)
                .setTitle(R.string.add_new_dictionary_title)
                .setMessage(message)
                .setNeutralButton(android.R.string.cancel) { _, _ -> cachedDictionaryFile.delete() }
                .setNegativeButton(R.string.button_select_language) { _, _ -> selectLocaleForDictionary(newHeader, locale) }
            if (SubtypeSettings.hasMatchingSubtypeForLocale(locale)) {
                val buttonText = context.getString(R.string.button_add_to_language, localeName)
                b.setPositiveButton(buttonText) { _, _ ->
                    addDictAndAskToReplace(newHeader, locale)
                }
            }
            b.show()
            return
        }

        // ScriptUtils.getScriptFromSpellCheckerLocale may return latin when it should not,
        // e.g. for Persian or Chinese. But at least fail when dictionary certainly is incompatible
        if (locale.script() != mainLocale.script())
            return onDictionaryLoadingError(R.string.dictionary_file_wrong_script)

        if (locale != mainLocale) {
            val message = context.resources.getString(
                R.string.dictionary_file_wrong_locale,
                LocaleUtils.getLocaleDisplayNameInSystemLocale(locale, context),
                LocaleUtils.getLocaleDisplayNameInSystemLocale(mainLocale, context)
            )
            AlertDialog.Builder(context)
                .setMessage(message)
                .setNegativeButton(android.R.string.cancel) { _, _ -> cachedDictionaryFile.delete() }
                .setPositiveButton(R.string.dictionary_file_wrong_locale_ok) { _, _ ->
                    addDictAndAskToReplace(newHeader, mainLocale)
                }
                .show()
            return
        }
        addDictAndAskToReplace(newHeader, mainLocale)
    }

    private fun selectLocaleForDictionary(newHeader: DictionaryHeader, dictLocale: Locale) {
        val locales = SubtypeSettings.getAvailableSubtypeLocales().sortedBy { it.language != dictLocale.language } // matching languages should show first
        val displayNamesArray = locales.map { LocaleUtils.getLocaleDisplayNameInSystemLocale(it, context) }.toTypedArray()
        AlertDialog.Builder(context)
            .setTitle(R.string.button_select_language)
            .setItems(displayNamesArray) { di, i ->
                di.dismiss()
                locales.forEachIndexed { index, locale ->
                    if (index == i)
                        addDictAndAskToReplace(newHeader, locale)
                }
            }
            .setNegativeButton(android.R.string.cancel) { _, _ -> cachedDictionaryFile.delete() }
            .show()
    }

    private fun addDictAndAskToReplace(header: DictionaryHeader, mainLocale: Locale) {
        val dictionaryType = header.mIdString.substringBefore(":")
        val cacheDir = DictionaryInfoUtils.getAndCreateCacheDirectoryForLocale(mainLocale, context)
        val dictFile = File(cacheDir, dictionaryType + "_" + DictionaryInfoUtils.USER_DICTIONARY_SUFFIX)

        fun moveDict(replaced: Boolean) {
            if (!cachedDictionaryFile.renameTo(dictFile)) {
                return onDictionaryLoadingError(R.string.dictionary_load_error)
            }
            if (dictionaryType == Dictionary.TYPE_MAIN) {
                // replaced main dict, remove the one created from internal data
                val internalMainDictFile = File(cacheDir, DictionaryInfoUtils.getExtractedMainDictFilename())
                internalMainDictFile.delete()
            }
            val newDictBroadcast = Intent(DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION)
            context.sendBroadcast(newDictBroadcast)
            onAdded?.let { it(replaced, dictFile) }
        }

        if (!dictFile.exists()) {
            return moveDict(false)
        }

        val systemLocale = context.resources.configuration.locale()
        val newInfo = header.info(systemLocale)
        val oldInfo = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(dictFile, 0, dictFile.length())?.info(systemLocale)
        confirmDialog(context,
            context.getString(R.string.replace_dictionary_message, dictionaryType, oldInfo, newInfo),
            context.getString(R.string.replace_dictionary)
        ) { moveDict(true) }
    }

    private fun onDictionaryLoadingError(messageId: Int) {
        cachedDictionaryFile.delete()
        infoDialog(context, messageId)
    }
}
