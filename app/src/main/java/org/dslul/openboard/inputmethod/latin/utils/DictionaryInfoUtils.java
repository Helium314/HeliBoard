/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.ContentValues;
import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.text.TextUtils;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.AssetFileAddress;
import org.dslul.openboard.inputmethod.latin.BinaryDictionaryGetter;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.define.DecoderSpecificConstants;
import org.dslul.openboard.inputmethod.latin.makedict.DictionaryHeader;
import org.dslul.openboard.inputmethod.latin.makedict.UnsupportedFormatException;
import org.dslul.openboard.inputmethod.latin.settings.SpacingAndPunctuations;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * This class encapsulates the logic for the Latin-IME side of dictionary information management.
 */
public class DictionaryInfoUtils {
    private static final String TAG = DictionaryInfoUtils.class.getSimpleName();
    public static final String RESOURCE_PACKAGE_NAME = R.class.getPackage().getName();
    public static final String DEFAULT_MAIN_DICT = "main";
    public static final String MAIN_DICT_PREFIX = DEFAULT_MAIN_DICT + "_";
    private static final String DICTIONARY_CATEGORY_SEPARATOR_EXPRESSION = "[" + BinaryDictionaryGetter.ID_CATEGORY_SEPARATOR + "_]";
    private static final String DECODER_DICT_SUFFIX = DecoderSpecificConstants.DECODER_DICT_SUFFIX;
    // 6 digits - unicode is limited to 21 bits
    private static final int MAX_HEX_DIGITS_FOR_CODEPOINT = 6;

    public static class DictionaryInfo {
        @NonNull public final String mId;
        @NonNull public final Locale mLocale;
        @Nullable public final String mDescription;
        @Nullable public final String mFilename;
        public final long mFilesize;
        public final long mModifiedTimeMillis;
        public final int mVersion;

        public DictionaryInfo(@NonNull String id, @NonNull Locale locale,
                @Nullable String description, @Nullable String filename,
                long filesize, long modifiedTimeMillis, int version) {
            mId = id;
            mLocale = locale;
            mDescription = description;
            mFilename = filename;
            mFilesize = filesize;
            mModifiedTimeMillis = modifiedTimeMillis;
            mVersion = version;
        }

        @Override
        public String toString() {
            return "DictionaryInfo : Id = '" + mId
                    + "' : Locale=" + mLocale
                    + " : Version=" + mVersion;
        }
    }

    private DictionaryInfoUtils() {
        // Private constructor to forbid instantation of this helper class.
    }

    /**
     * Returns whether we may want to use this character as part of a file name.
     *
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
     *
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
                sb.append(String.format((Locale)null, "%%%1$0" + MAX_HEX_DIGITS_FOR_CODEPOINT + "x",
                        codePoint));
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
     * Helper method to get the top level cache directory.
     */
    public static String getWordListStagingDirectory(final Context context) {
        return context.getFilesDir() + File.separator + "staging";
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
                final int encodedCodePoint = Integer.parseInt(
                        fname.substring(i + 1, i + 1 + MAX_HEX_DIGITS_FOR_CODEPOINT), 16);
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

    public static File[] getStagingDirectoryList(final Context context) {
        return new File(DictionaryInfoUtils.getWordListStagingDirectory(context)).listFiles();
    }

    /**
     * Returns the category for a given file name.
     *
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
        final String absoluteDirectoryName = getWordListCacheDirectory(context) + File.separator
                + relativeDirectoryName;
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
     * Find out whether a dictionary is available for this locale.
     * @param context the context on which to check resources.
     * @param locale the locale to check for.
     * @return whether a (non-placeholder) dictionary is available or not.
     */
    public static boolean isDictionaryAvailable(final Context context, final Locale locale) {
        final Resources res = context.getResources();
        return 0 != getMainDictionaryResourceIdIfAvailableForLocale(res, locale);
    }

    /**
     * Helper method to return a dictionary res id for a locale, or 0 if none.
     * @param res resources for the app
     * @param locale dictionary locale
     * @return main dictionary resource id
     */
    public static int getMainDictionaryResourceIdIfAvailableForLocale(final Resources res,
            final Locale locale) {
        int resId;
        // Try to find main_language_country dictionary.
        if (!locale.getCountry().isEmpty()) {
            final String dictLanguageCountry = MAIN_DICT_PREFIX
                    + locale.toString().toLowerCase(Locale.ROOT) + DECODER_DICT_SUFFIX;
            if ((resId = res.getIdentifier(
                    dictLanguageCountry, "raw", RESOURCE_PACKAGE_NAME)) != 0) {
                return resId;
            }
        }

        // Try to find main_language dictionary.
        final String dictLanguage = MAIN_DICT_PREFIX + locale.getLanguage() + DECODER_DICT_SUFFIX;
        if ((resId = res.getIdentifier(dictLanguage, "raw", RESOURCE_PACKAGE_NAME)) != 0) {
            return resId;
        }

        // Not found, return 0
        return 0;
    }

    /**
     * Returns a main dictionary resource id
     * @param res resources for the app
     * @param locale dictionary locale
     * @return main dictionary resource id
     */
    public static int getMainDictionaryResourceId(final Resources res, final Locale locale) {
        int resourceId = getMainDictionaryResourceIdIfAvailableForLocale(res, locale);
        if (0 != resourceId) {
            return resourceId;
        }
        return res.getIdentifier(DEFAULT_MAIN_DICT + DecoderSpecificConstants.DECODER_DICT_SUFFIX,
                "raw", RESOURCE_PACKAGE_NAME);
    }

    /**
     * Returns the id associated with the main word list for a specified locale.
     *
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

    @Nullable
    public static DictionaryHeader getDictionaryFileHeaderOrNull(final File file,
            final long offset, final long length) {
        try {
            final DictionaryHeader header =
                    BinaryDictionaryUtils.getHeaderWithOffsetAndLength(file, offset, length);
            return header;
        } catch (UnsupportedFormatException e) {
            return null;
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns information of the dictionary.
     *
     * @param fileAddress the asset dictionary file address.
     * @param locale Locale for this file.
     * @return information of the specified dictionary.
     */
    private static DictionaryInfo createDictionaryInfoFromFileAddress(
            @NonNull final AssetFileAddress fileAddress, final Locale locale) {
        final String id = getMainDictId(locale);
        final int version = DictionaryHeaderUtils.getContentVersion(fileAddress);
        final String description = SubtypeLocaleUtils
                .getSubtypeLocaleDisplayName(locale.toString());
        // Do not store the filename on db as it will try to move the filename from db to the
        // cached directory. If the filename is already in cached directory, this is not
        // necessary.
        final String filenameToStoreOnDb = null;
        return new DictionaryInfo(id, locale, description, filenameToStoreOnDb,
                fileAddress.mLength, new File(fileAddress.mFilename).lastModified(), version);
    }

    /**
     * Returns the information of the dictionary for the given {@link AssetFileAddress}.
     * If the file is corrupted or a pre-fava file, then the file gets deleted and the null
     * value is returned.
     */
    @Nullable
    private static DictionaryInfo createDictionaryInfoForUnCachedFile(
            @NonNull final AssetFileAddress fileAddress, final Locale locale) {
        final String id = getMainDictId(locale);
        final int version = DictionaryHeaderUtils.getContentVersion(fileAddress);

        if (version == -1) {
            // Purge the pre-fava/corrupted unused dictionaires.
            fileAddress.deleteUnderlyingFile();
            return null;
        }

        final String description = SubtypeLocaleUtils
                .getSubtypeLocaleDisplayName(locale.toString());

        final File unCachedFile = new File(fileAddress.mFilename);
        // Store just the filename and not the full path.
        final String filenameToStoreOnDb = unCachedFile.getName();
        return new DictionaryInfo(id, locale, description, filenameToStoreOnDb, fileAddress.mLength,
                unCachedFile.lastModified(), version);
    }

    /**
     * Returns dictionary information for the given locale.
     */
    private static DictionaryInfo createDictionaryInfoFromLocale(Locale locale) {
        final String id = getMainDictId(locale);
        final int version = -1;
        final String description = SubtypeLocaleUtils
                .getSubtypeLocaleDisplayName(locale.toString());
        return new DictionaryInfo(id, locale, description, null, 0L, 0L, version);
    }

    private static void addOrUpdateDictInfo(final ArrayList<DictionaryInfo> dictList,
            final DictionaryInfo newElement) {
        final Iterator<DictionaryInfo> iter = dictList.iterator();
        while (iter.hasNext()) {
            final DictionaryInfo thisDictInfo = iter.next();
            if (thisDictInfo.mLocale.equals(newElement.mLocale)) {
                if (newElement.mVersion <= thisDictInfo.mVersion) {
                    return;
                }
                iter.remove();
            }
        }
        dictList.add(newElement);
    }

    public static ArrayList<DictionaryInfo> getCurrentDictionaryFileNameAndVersionInfo(
            final Context context) {
        final ArrayList<DictionaryInfo> dictList = new ArrayList<>();

        // Retrieve downloaded dictionaries from cached directories
        final File[] directoryList = getCachedDirectoryList(context);
        if (null != directoryList) {
            for (final File directory : directoryList) {
                final String localeString = getWordListIdFromFileName(directory.getName());
                final File[] dicts = BinaryDictionaryGetter.getCachedWordLists(
                        localeString, context, false);
                for (final File dict : dicts) {
                    final String wordListId = getWordListIdFromFileName(dict.getName());
                    if (!DictionaryInfoUtils.isMainWordListId(wordListId)) {
                        continue;
                    }
                    final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
                    final AssetFileAddress fileAddress = AssetFileAddress.makeFromFile(dict);
                    final DictionaryInfo dictionaryInfo =
                            createDictionaryInfoFromFileAddress(fileAddress, locale);
                    // Protect against cases of a less-specific dictionary being found, like an
                    // en dictionary being used for an en_US locale. In this case, the en dictionary
                    // should be used for en_US but discounted for listing purposes.
                    if (dictionaryInfo == null || !dictionaryInfo.mLocale.equals(locale)) {
                        continue;
                    }
                    addOrUpdateDictInfo(dictList, dictionaryInfo);
                }
            }
        }

        // Retrieve files from assets
        final Resources resources = context.getResources();
        final AssetManager assets = resources.getAssets();
        for (final String localeString : assets.getLocales()) {
            final Locale locale = LocaleUtils.constructLocaleFromString(localeString);
            final int resourceId =
                    DictionaryInfoUtils.getMainDictionaryResourceIdIfAvailableForLocale(
                            context.getResources(), locale);
            if (0 == resourceId) {
                continue;
            }
            final AssetFileAddress fileAddress =
                    BinaryDictionaryGetter.loadFallbackResource(context, resourceId);
            final DictionaryInfo dictionaryInfo = createDictionaryInfoFromFileAddress(fileAddress,
                    locale);
            // Protect against cases of a less-specific dictionary being found, like an
            // en dictionary being used for an en_US locale. In this case, the en dictionary
            // should be used for en_US but discounted for listing purposes.
            // TODO: Remove dictionaryInfo == null when the static LMs have the headers.
            if (dictionaryInfo == null || !dictionaryInfo.mLocale.equals(locale)) {
                continue;
            }
            addOrUpdateDictInfo(dictList, dictionaryInfo);
        }

        // Generate the dictionary information from  the enabled subtypes. This will not
        // overwrite the real records.
        RichInputMethodManager.init(context);
        List<InputMethodSubtype> enabledSubtypes = RichInputMethodManager
                .getInstance().getMyEnabledInputMethodSubtypeList(true);
        for (InputMethodSubtype subtype : enabledSubtypes) {
            Locale locale = LocaleUtils.constructLocaleFromString(subtype.getLocale());
            DictionaryInfo dictionaryInfo = createDictionaryInfoFromLocale(locale);
            addOrUpdateDictInfo(dictList, dictionaryInfo);
        }

        return dictList;
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
