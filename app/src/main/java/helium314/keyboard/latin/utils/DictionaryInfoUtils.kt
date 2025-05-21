/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.text.TextUtils
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils
import helium314.keyboard.latin.common.loopOverCodePoints
import helium314.keyboard.latin.define.DecoderSpecificConstants
import helium314.keyboard.latin.makedict.DictionaryHeader
import helium314.keyboard.latin.makedict.UnsupportedFormatException
import helium314.keyboard.latin.settings.SpacingAndPunctuations
import java.io.File
import java.io.IOException
import java.util.Locale

/** encapsulates the logic for the Latin-IME side of dictionary information management */
object DictionaryInfoUtils {
    private val TAG = DictionaryInfoUtils::class.java.simpleName
    const val DEFAULT_MAIN_DICT = "main"
    const val USER_DICTIONARY_SUFFIX = "user.dict"
    const val MAIN_DICT_PREFIX = DEFAULT_MAIN_DICT + "_"
    const val ASSETS_DICTIONARY_FOLDER = "dicts"
    const val MAIN_DICT_FILE_NAME = DEFAULT_MAIN_DICT + ".dict"
    private const val MAX_HEX_DIGITS_FOR_CODEPOINT = 6 // unicode is limited to 21 bits

    /**
     * Returns whether we may want to use this character as part of a file name.
     * This basically only accepts ascii letters and numbers, and rejects everything else.
     */
    private fun isFileNameCharacter(codePoint: Int): Boolean {
        if (codePoint in 0x30..0x39) return true // Digit
        if (codePoint in 0x41..0x5A) return true // Uppercase
        if (codePoint in 0x61..0x7A) return true // Lowercase
        return codePoint == '_'.code || codePoint == '-'.code
    }

    /**
     * Escapes a string for any characters that may be suspicious for a file or directory name.
     *
     * Concretely this does a sort of URL-encoding except it will encode everything that's not
     * alphanumeric or underscore. (true URL-encoding leaves alone characters like '*', which
     * we cannot allow here)
     */
    private fun replaceFileNameDangerousCharacters(name: String): String {
        // This assumes '%' is fully available as a non-separator, normal
        // character in a file name. This is probably true for all file systems.
        val sb = StringBuilder()
        loopOverCodePoints(name) { codePoint, _ ->
            if (isFileNameCharacter(codePoint)) {
                sb.appendCodePoint(codePoint)
            } else {
                sb.append(String.format(Locale.US, "%%%1$0" + MAX_HEX_DIGITS_FOR_CODEPOINT + "x", codePoint))
            }
            false
        }
        return sb.toString()
    }

    fun getWordListCacheDirectory(context: Context): String = context.filesDir.toString() + File.separator + "dicts"

    /** Reverse escaping done by replaceFileNameDangerousCharacters. */
    fun getWordListIdFromFileName(fname: String): String {
        val sb = StringBuilder()
        val fnameLength = fname.length
        var i = 0
        while (i < fnameLength) {
            val codePoint = fname.codePointAt(i)
            if ('%'.code != codePoint) {
                sb.appendCodePoint(codePoint)
            } else {
                // + 1 to pass the % sign
                val encodedCodePoint = fname.substring(i + 1, i + 1 + MAX_HEX_DIGITS_FOR_CODEPOINT).toInt(16)
                i += MAX_HEX_DIGITS_FOR_CODEPOINT
                sb.appendCodePoint(encodedCodePoint)
            }
            i = fname.offsetByCodePoints(i, 1)
        }
        return sb.toString()
    }

    /** Helper method to the list of cache directories, one for each distinct locale. */
    fun getCachedDirectoryList(context: Context) = File(getWordListCacheDirectory(context)).listFiles().orEmpty()

    /** Find out the cache directory associated with a specific locale. */
    fun getAndCreateCacheDirectoryForLocale(locale: Locale, context: Context): String {
        val absoluteDirectoryName = getCacheDirectoryForLocale(locale, context)
        val directory = File(absoluteDirectoryName)
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Could not create the directory for locale $locale")
        }
        return absoluteDirectoryName
    }

    fun getCacheDirectoryForLocale(locale: Locale, context: Context): String {
        val relativeDirectoryName = replaceFileNameDangerousCharacters(locale.toLanguageTag())
        return getWordListCacheDirectory(context) + File.separator + relativeDirectoryName
    }

    fun getCachedDictsForLocale(locale: Locale, context: Context) =
        File(getAndCreateCacheDirectoryForLocale(locale, context)).listFiles().orEmpty()

    fun getDictionaryFileHeaderOrNull(file: File, offset: Long, length: Long): DictionaryHeader? {
        return try {
            BinaryDictionaryUtils.getHeaderWithOffsetAndLength(file, offset, length)
        } catch (e: UnsupportedFormatException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    fun getDictionaryFileHeaderOrNull(file: File): DictionaryHeader? {
        return try {
            BinaryDictionaryUtils.getHeader(file)
        } catch (e: UnsupportedFormatException) {
            null
        } catch (e: IOException) {
            null
        }
    }

    /**
     * Returns the locale for a dictionary file name stored in assets.
     *
     * Assumes file name main_[locale].dict
     * Returns the locale, or null if file name does not match the pattern
     */
    fun extractLocaleFromAssetsDictionaryFile(dictionaryFileName: String): String? {
        if (dictionaryFileName.startsWith(MAIN_DICT_PREFIX) && dictionaryFileName.endsWith(".dict")) {
            return dictionaryFileName.substring(MAIN_DICT_PREFIX.length, dictionaryFileName.lastIndexOf('.'))
        }
        return null
    }

    fun getAssetsDictionaryList(context: Context): Array<String>? = try {
        context.assets.list(ASSETS_DICTIONARY_FOLDER)
    } catch (e: IOException) {
        null
    }

    @JvmStatic
    fun looksValidForDictionaryInsertion(text: CharSequence, spacingAndPunctuations: SpacingAndPunctuations): Boolean {
        if (TextUtils.isEmpty(text)) {
            return false
        }
        if (text.length > DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH) {
            return false
        }
        var digitCount = 0
        loopOverCodePoints(text) { codePoint, charCount ->
            if (Character.isDigit(codePoint)) {
                // Count digits: see below
                digitCount += charCount
                return@loopOverCodePoints false
            }
            if (!spacingAndPunctuations.isWordCodePoint(codePoint)) {
                return false
            }
            false
        }
        // We reject strings entirely comprised of digits to avoid using PIN codes or credit
        // card numbers. It would come in handy for word prediction though; a good example is
        // when writing one's address where the street number is usually quite discriminative,
        // as well as the postal code.
        return digitCount < text.length
    }
}
