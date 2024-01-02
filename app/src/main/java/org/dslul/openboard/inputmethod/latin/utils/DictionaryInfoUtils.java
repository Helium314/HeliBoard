/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter;
import org.dslul.openboard.inputmethod.latin.define.DecoderSpecificConstants;
import org.dslul.openboard.inputmethod.latin.makedict.DictionaryHeader;
import org.dslul.openboard.inputmethod.latin.makedict.UnsupportedFormatException;
import org.dslul.openboard.inputmethod.latin.settings.SpacingAndPunctuations;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

/**
 * This class encapsulates the logic for the Latin-IME side of dictionary information management.
 */
public class DictionaryInfoUtils {
    private static final String TAG = DictionaryInfoUtils.class.getSimpleName();
    public static final String DEFAULT_MAIN_DICT = "main";
    public static final String MAIN_DICT_PREFIX = DEFAULT_MAIN_DICT + "_";
    private static final String DICTIONARY_CATEGORY_SEPARATOR_EXPRESSION = "[" + BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + "_]";
    // 6 digits - unicode is limited to 21 bits
    private static final int MAX_HEX_DIGITS_FOR_CODEPOINT = 6;

    private DictionaryInfoUtils() {
        // Private constructor to forbid instantation of this helper class.
    }

    /**
     * Returns whether we may want to use this character as part of a file name.
     * <p>
     * This basically only accepts ascii letters and numbers, and rejects everything else.
     */
    private static boolean isFileNameCharacter(int codePoint) {
        if (codePoint >= 0x30 && codePoint <= 0x39) return true; // Digit
        if (codePoint >= 0x41 && codePoint <= 0x5A) return true; // Uppercase
        if (codePoint >= 0x61 && codePoint <= 0x7A) return true; // Lowercase
        return codePoint == '_'; // Underscore
    }

    /**
     * Escapes a string for any characters that may be suspicious for a file or directory name.
     * <p>
     * Concretely this does a sort of URL-encoding except it will encode everything that's not
     * alphanumeric or underscore. (true URL-encoding leaves alone characters like '*', which
     * we cannot allow here)
     */
    // TODO: create a unit test for this method
    public static String replaceFileNameDangerousCharacters(final String name) {
        // This assumes '%' is fully available as a non-separator, normal
        // character in a file name. This is probably true for all file systems.
        final StringBuilder sb = new StringBuilder();
        final int nameLength = name.length();
        for (int i = 0; i < nameLength; i = name.offsetByCodePoints(i, 1)) {
            final int codePoint = name.codePointAt(i);
            if (DictionaryInfoUtils.isFileNameCharacter(codePoint)) {
                sb.appendCodePoint(codePoint);
            } else {
                sb.append(String.format(Locale.US, "%%%1$0" + MAX_HEX_DIGITS_FOR_CODEPOINT + "x", codePoint));
            }
        }
        return sb.toString();
    }

    /**
     * Helper method to get the top level cache directory.
     */
    public static String getWordListCacheDirectory(final Context context) {
        return context.getFilesDir() + File.separator + "dicts";
    }

    /**
     * Helper method to get the top level temp directory.
     */
    public static String getWordListTempDirectory(final Context context) {
        return context.getFilesDir() + File.separator + "tmp";
    }

    /**
     * Reverse escaping done by {@link #replaceFileNameDangerousCharacters(String)}.
     */
    @NonNull
    public static String getWordListIdFromFileName(@NonNull final String fname) {
        final StringBuilder sb = new StringBuilder();
        final int fnameLength = fname.length();
        for (int i = 0; i < fnameLength; i = fname.offsetByCodePoints(i, 1)) {
            final int codePoint = fname.codePointAt(i);
            if ('%' != codePoint) {
                sb.appendCodePoint(codePoint);
            } else {
                // + 1 to pass the % sign
                final int encodedCodePoint =
                        Integer.parseInt(fname.substring(i + 1, i + 1 + MAX_HEX_DIGITS_FOR_CODEPOINT), 16);
                i += MAX_HEX_DIGITS_FOR_CODEPOINT;
                sb.appendCodePoint(encodedCodePoint);
            }
        }
        return sb.toString();
    }

    /**
     * Helper method to the list of cache directories, one for each distinct locale.
     */
    public static File[] getCachedDirectoryList(final Context context) {
        return new File(DictionaryInfoUtils.getWordListCacheDirectory(context)).listFiles();
    }

    /**
     * Returns the category for a given file name.
     * <p>
     * This parses the file name, extracts the category, and returns it. See
     * {@link #getMainDictId(Locale)} and {@link #isMainWordListId(String)}.
     * @return The category as a string or null if it can't be found in the file name.
     */
    @Nullable
    public static String getCategoryFromFileName(@NonNull final String fileName) {
        final String id = getWordListIdFromFileName(fileName);
        final String[] idArray = id.split(DICTIONARY_CATEGORY_SEPARATOR_EXPRESSION);
        // An id is supposed to be in format category:locale, so splitting on the separator
        // should yield a 2-elements array
        // Also allow '_' as separator, this is ok for locales like pt_br because
        // we're interested in the part before first separator anyway
        if (1 == idArray.length) {
            return null;
        }
        return idArray[0];
    }

    /**
     * Find out the cache directory associated with a specific locale.
     */
    public static String getCacheDirectoryForLocale(final String locale, final Context context) {
        final String relativeDirectoryName = replaceFileNameDangerousCharacters(locale).toLowerCase(Locale.ENGLISH);
        final String absoluteDirectoryName = getWordListCacheDirectory(context) + File.separator + relativeDirectoryName;
        final File directory = new File(absoluteDirectoryName);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Could not create the directory for locale" + locale);
            }
        }
        return absoluteDirectoryName;
    }

    public static boolean isMainWordListId(final String id) {
        final String[] idArray = id.split(DICTIONARY_CATEGORY_SEPARATOR_EXPRESSION);
        // An id is supposed to be in format category:locale, so splitting on the separator
        // should yield a 2-elements array
        // Also allow '_' as separator, this is ok for locales like pt_br because
        // we're interested in the part before first separator anyway
        if (1 == idArray.length) {
            return false;
        }
        return BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY.equals(idArray[0]);
    }

    /**
     * Returns the id associated with the main word list for a specified locale.
     * <p>
     * Word lists stored in Android Keyboard's resources are referred to as the "main"
     * word lists. Since they can be updated like any other list, we need to assign a
     * unique ID to them. This ID is just the name of the language (locale-wise) they
     * are for, and this method returns this ID.
     */
    public static String getMainDictId(@NonNull final Locale locale) {
        // This works because we don't include by default different dictionaries for
        // different countries. This actually needs to return the id that we would
        // like to use for word lists included in resources, and the following is okay.
        return BinaryDictionaryGetter.MAIN_DICTIONARY_CATEGORY +
                BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + locale.toString().toLowerCase();
    }

    public static String getMainDictFilename(@NonNull final String locale) {
        return MAIN_DICT_PREFIX + locale.toLowerCase(Locale.ENGLISH) + ".dict";
    }

    public static File getMainDictFile(@NonNull final String locale, @NonNull final Context context) {
        return new File(DictionaryInfoUtils.getCacheDirectoryForLocale(locale, context) +
                File.separator + DictionaryInfoUtils.getMainDictFilename(locale));
    }

    @Nullable
    public static DictionaryHeader getDictionaryFileHeaderOrNull(final File file,
            final long offset, final long length) {
        try {
            return BinaryDictionaryUtils.getHeaderWithOffsetAndLength(file, offset, length);
        } catch (UnsupportedFormatException | IOException e) {
            return null;
        }
    }

    @UsedForTesting
    public static boolean looksValidForDictionaryInsertion(final CharSequence text,
            final SpacingAndPunctuations spacingAndPunctuations) {
        if (TextUtils.isEmpty(text)) {
            return false;
        }
        final int length = text.length();
        if (length > DecoderSpecificConstants.DICTIONARY_MAX_WORD_LENGTH) {
            return false;
        }
        int i = 0;
        int digitCount = 0;
        while (i < length) {
            final int codePoint = Character.codePointAt(text, i);
            final int charCount = Character.charCount(codePoint);
            i += charCount;
            if (Character.isDigit(codePoint)) {
                // Count digits: see below
                digitCount += charCount;
                continue;
            }
            if (!spacingAndPunctuations.isWordCodePoint(codePoint)) {
                return false;
            }
        }
        // We reject strings entirely comprised of digits to avoid using PIN codes or credit
        // card numbers. It would come in handy for word prediction though; a good example is
        // when writing one's address where the street number is usually quite discriminative,
        // as well as the postal code.
        return digitCount < length;
    }
}
