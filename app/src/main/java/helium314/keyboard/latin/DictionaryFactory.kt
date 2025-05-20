/*
* Copyright (C) 2011 The Android Open Source Project
* modified
* SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
*/
package helium314.keyboard.latin

import android.content.Context
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.DictionaryInfoUtils
import helium314.keyboard.latin.utils.Log
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
// todo: this needs updating, and then we can expose the weight for custom dictionaries (useful for addons like emoji dict)
fun createMainDictionary(context: Context, locale: Locale): DictionaryCollection {
    val cacheDir = DictionaryInfoUtils.getAndCreateCacheDirectoryForLocale(locale, context)
    val dictList = LinkedList<Dictionary>()
    // get cached dict files
    val (userDicts, extractedDicts) = DictionaryInfoUtils.getCachedDictsForLocale(locale, context)
        .partition { it.name.endsWith(DictionaryInfoUtils.USER_DICTIONARY_SUFFIX) }
    // add user dicts to list
    userDicts.forEach { checkAndAddDictionaryToListIfNotExisting(it, dictList, locale) }
    // add extracted dicts to list (after userDicts, to skip extracted dicts of same type)
    extractedDicts.forEach { checkAndAddDictionaryToListIfNotExisting(it, dictList, locale) }
    if (dictList.any { it.mDictType == Dictionary.TYPE_MAIN })
        return DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList, FloatArray(dictList.size) { 1f })

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
    return DictionaryCollection(Dictionary.TYPE_MAIN, locale, dictList, FloatArray(dictList.size) { 1f })
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
