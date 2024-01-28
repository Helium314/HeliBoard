/*
* Copyright (C) 2011 The Android Open Source Project
* modified
* SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
*/
package org.dslul.openboard.inputmethod.latin

import android.content.Context
import org.dslul.openboard.inputmethod.latin.common.FileUtils
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils.constructLocale
import org.dslul.openboard.inputmethod.latin.settings.USER_DICTIONARY_SUFFIX
import org.dslul.openboard.inputmethod.latin.utils.DictionaryInfoUtils
import org.dslul.openboard.inputmethod.latin.utils.Log
import java.io.File
import java.util.LinkedList
import java.util.Locale

/**
 * Initializes a main dictionary collection from a dictionary pack, with explicit flags.
 *
 *
 * This searches for a content provider providing a dictionary pack for the specified
 * locale. If none is found, it falls back to the built-in dictionary - if any.
 * @param context application context for reading resources
 * @param locale the locale for which to create the dictionary
 * @return an initialized instance of DictionaryCollection
 */
fun createMainDictionary(context: Context, locale: Locale): DictionaryCollection {
    val cacheDir = DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context)
    val dictList = LinkedList<Dictionary>()
    // get cached dict files
    val (userDicts, extractedDicts) = DictionaryInfoUtils.getCachedDictsForLocale(locale, context)
        .partition { it.name.endsWith(USER_DICTIONARY_SUFFIX) }
    // add user dicts to list
    userDicts.forEach { checkAndAddDictionaryToListIfNotExisting(it, dictList, locale) }
    // add extracted dicts to list (after userDicts, to skip extracted dicts of same type)
    extractedDicts.forEach { checkAndAddDictionaryToListIfNotExisting(it, dictList, locale) }
    if (dictList.any { it.mDictType == Dictionary.TYPE_MAIN })
        return DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList)

    // no main dict found -> check assets
    val assetsDicts = DictionaryInfoUtils.getAssetsDictionaryList(context)
    // file name is <type>_<language tag>.dict
    val dictsByType = assetsDicts?.groupBy { it.substringBefore("_") }
    // for each type find the best match
    dictsByType?.forEach { (dictType, dicts) ->
        val bestMatch = LocaleUtils.getBestMatch(locale, dicts) { it.substringAfter("_")
            .substringBefore(".").constructLocale() } ?: return@forEach
        // extract dict and add extracted file
        val targetFile = File(cacheDir, "$dictType.dict")
        FileUtils.copyStreamToNewFile(
            context.assets.open(DictionaryInfoUtils.ASSETS_DICTIONARY_FOLDER + File.separator + bestMatch),
            targetFile
        )
        checkAndAddDictionaryToListIfNotExisting(targetFile, dictList, locale)
    }
    // If the list is empty, that means we should not use any dictionary (for example, the user
    // explicitly disabled the main dictionary), so the following is okay. dictList is never
    // null, but if for some reason it is, DictionaryCollection handles it gracefully.
    return DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList)
}

/**
 * add dictionary created from [file] to [dicts]
 * if [file] cannot be loaded it is deleted
 * if the dictionary type already exists in [dicts], the [file] is skipped
 */
private fun checkAndAddDictionaryToListIfNotExisting(file: File, dicts: MutableList<Dictionary>, locale: Locale) {
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
