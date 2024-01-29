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
import org.dslul.openboard.inputmethod.latin.Dictionary;
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
    // 6 digits - unicode is limited to 21 bits
    private static final int MAX_HEX_DIGITS_FOR_CODEPOINT = 6;
    public static final String ASSETS_DICTIONARY_FOLDER = "dicts";
    public static final String ID_CATEGORY_SEPARATOR = ":";
    private static final String DICTIONARY_CATEGORY_SEPARATOR_EXPRESSION = "[" + ID_CATEGORY_SEPARATOR + "_]";

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
        return codePoint == '_' || codePoint == '-';
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
    @Nullable public static File[] getCachedDirectoryList(final Context context) {
        return new File(DictionaryInfoUtils.getWordListCacheDirectory(context)).listFiles();
    }

    /**
     * Find out the cache directory associated with a specific locale.
     */
    public static String getCacheDirectoryForLocale(final Locale locale, final Context context) {
        final String relativeDirectoryName = replaceFileNameDangerousCharacters(locale.toLanguageTag());
        final String absoluteDirectoryName = getWordListCacheDirectory(context) + File.separator + relativeDirectoryName;
        final File directory = new File(absoluteDirectoryName);
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                Log.e(TAG, "Could not create the directory for locale" + locale);
            }
        }
        return absoluteDirectoryName;
    }

    public static File[] getCachedDictsForLocale(final Locale locale, final Context context) {
        final File cachedDir = new File(getCacheDirectoryForLocale(locale, context));
        if (!cachedDir.isDirectory())
            return new File[]{};
        return cachedDir.listFiles();
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
        return Dictionary.TYPE_MAIN + ID_CATEGORY_SEPARATOR + locale.toString().toLowerCase();
    }

    public static String getExtractedMainDictFilename() {
        return DEFAULT_MAIN_DICT + ".dict";
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

    @Nullable
    public static DictionaryHeader getDictionaryFileHeaderOrNull(final File file) {
        try {
            return BinaryDictionaryUtils.getHeader(file);
        } catch (UnsupportedFormatException | IOException e) {
            return null;
        }
    }

    /**
     * Returns the locale for a dictionary file name stored in assets.
     * <p>
     * Assumes file name main_[locale].dict
     * <p>
     * Returns the locale, or null if file name does not match the pattern
     */
    @Nullable public static String extractLocaleFromAssetsDictionaryFile(final String dictionaryFileName) {
        if (dictionaryFileName.startsWith(DictionaryInfoUtils.MAIN_DICT_PREFIX)
                && dictionaryFileName.endsWith(".dict")) {
            return dictionaryFileName.substring(
                    DictionaryInfoUtils.MAIN_DICT_PREFIX.length(),
                    dictionaryFileName.lastIndexOf('.')
            );
        }
        return null;
    }

    @Nullable public static String[] getAssetsDictionaryList(final Context context) {
        final String[] dictionaryList;
        try {
            dictionaryList = context.getAssets().list(ASSETS_DICTIONARY_FOLDER);
        } catch (IOException e) {
            return null;
        }
        return dictionaryList;
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
