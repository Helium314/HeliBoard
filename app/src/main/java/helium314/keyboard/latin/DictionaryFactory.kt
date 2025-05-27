/*
* Copyright (C) 2011 The Android Open Source Project
* modified
* SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
*/
package helium314.keyboard.latin

import android.content.Context
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.LinkedList
import java.util.Locale

object DictionaryFactory {
    /**
     * Initializes a main dictionary collection for a locale.
     * Uses all dictionaries in cache folder for locale, and adds built-in
     * dictionaries of matching locales if type is not already in cache folder.
     *
     * @return an initialized instance of DictionaryCollection
     */
    // todo:
    //  expose the weight so users can adjust dictionary "importance" (useful for addons like emoji dict)
    //  allow users to block certain dictionaries (not sure how this should work exactly)
    fun createMainDictionaryCollection(context: Context, locale: Locale): DictionaryCollection {
        val dictList = LinkedList<Dictionary>()
        val (extracted, nonExtracted) = getAvailableDictsForLocale(locale, context)
        extracted.sortedBy { !it.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX) }.forEach {
            // we sort to have user dicts first, so they have priority over internal dicts of the same type
            checkAndAddDictionaryToListNewType(it, dictList, locale)
        }
        nonExtracted.forEach { filename ->
            val type = filename.substringBefore(".")
            if (dictList.any { it.mDictType == type }) return@forEach
            val extractedFile = DictionaryInfoUtils.extractAssetsDictionary(filename, locale, context)
            checkAndAddDictionaryToListNewType(extractedFile, dictList, locale)
        }
        return DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList, FloatArray(dictList.size) { 1f })
    }

    fun getAvailableDictsForLocale(locale: Locale, context: Context): Pair<Array<out File>, List<String>> {
        val cachedDicts = DictionaryInfoUtils.getCachedDictsForLocale(locale, context)

        val nonExtractedDicts = mutableListOf<String>()
        DictionaryInfoUtils.getAssetsDictionaryList(context)
            // file name is <type>_<language tag>.dict
            ?.groupBy { it.substringBefore("_") }
            ?.forEach { (dictType, dicts) ->
                if (cachedDicts.any { it.name == "$dictType.dict" })
                    return@forEach // dictionary is already extracted (can't be old because of cleanup on upgrade)
                val bestMatch = LocaleUtils.getBestMatch(locale, dicts) {
                    DictionaryInfoUtils.extractLocaleFromAssetsDictionaryFile(it)
                } ?: return@forEach
                nonExtractedDicts.add(bestMatch)
            }
        return cachedDicts to nonExtractedDicts
    }

    /**
     * add dictionary created from [file] to [dicts]
     * if [file] cannot be loaded it is deleted
     * if the dictionary type already exists in [dicts], the [file] is skipped
     */
    private fun checkAndAddDictionaryToListNewType(file: File, dicts: MutableList<Dictionary>, locale: Locale) {
        if (!file.isFile) return
        val header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(file) ?: return killDictionary(file)
        val dictType = header.mIdString.split(":").first()
        if (dicts.any { it.mDictType == dictType }) return
        val readOnlyBinaryDictionary = ReadOnlyBinaryDictionary(
            file.absolutePath, 0, file.length(), false, locale, dictType
        )

        if (readOnlyBinaryDictionary.isValidDictionary) {
            if (locale.language == "ko") {
                // Use KoreanDictionary for Korean locale
                dicts.add(KoreanDictionary(readOnlyBinaryDictionary))
            } else {
                dicts.add(readOnlyBinaryDictionary)
            }
        } else {
            readOnlyBinaryDictionary.close()
            killDictionary(file)
        }
    }

    private fun killDictionary(file: File) {
        Log.e("DictionaryFactory", "could not load dictionary ${file.parentFile?.name}/${file.name}, deleting")
        file.delete()
    }
}
