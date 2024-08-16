/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin;

import android.content.Context;
import android.database.ContentObserver;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.UserDictionary.Words;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import com.android.inputmethod.latin.BinaryDictionary;

import com.oscar.aikeyboard.latin.utils.Log;
import com.oscar.aikeyboard.latin.utils.SubtypeLocaleUtils;

import java.io.File;
import java.util.Arrays;
import java.util.Locale;

/**
 * An expandable dictionary that stores the words in the user dictionary provider into a binary
 * dictionary file to use it from native code.
 */
public class UserBinaryDictionary extends ExpandableBinaryDictionary {
    private static final String TAG = ExpandableBinaryDictionary.class.getSimpleName();

    // The user dictionary provider uses an empty string to mean "all languages".
    private static final String USER_DICTIONARY_ALL_LANGUAGES = "";
    private static final int HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY = 250;
    private static final int LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY = 160;
    // Shortcut frequency is 0~15, with 15 = whitelist. We don't want user dictionary entries
    // to auto-correct, so we set this to the highest frequency that won't, i.e. 14.
    private static final int USER_DICT_SHORTCUT_FREQUENCY = 14;

    private static final String[] PROJECTION_QUERY_WITH_SHORTCUT = new String[] {
            Words.WORD,
            Words.SHORTCUT,
            Words.FREQUENCY,
    };
    private static final String[] PROJECTION_QUERY_WITHOUT_SHORTCUT = new String[] {
            Words.WORD,
            Words.FREQUENCY,
    };

    private static final String NAME = "userunigram";

    private ContentObserver mObserver;
    // this really needs to be the locale string, as it interacts with system
    final private String mLocaleString;
    final private boolean mAlsoUseMoreRestrictiveLocales;

    protected UserBinaryDictionary(final Context context, final Locale locale,
                                   final boolean alsoUseMoreRestrictiveLocales,
                                   final File dictFile, final String name) {
        super(context, getDictName(name, locale, dictFile), locale, Dictionary.TYPE_USER, dictFile);
        if (null == locale) throw new NullPointerException(); // Catch the error earlier
        final String localeStr = locale.toString();
        if (SubtypeLocaleUtils.NO_LANGUAGE.equals(localeStr)) {
            // If we don't have a locale, insert into the "all locales" user dictionary.
            mLocaleString = USER_DICTIONARY_ALL_LANGUAGES;
        } else {
            mLocaleString = localeStr;
        }
        mAlsoUseMoreRestrictiveLocales = alsoUseMoreRestrictiveLocales;

        mObserver = new ContentObserver(null) {
            @Override
            public void onChange(final boolean self, final Uri uri) {
                setNeedsToRecreate();
            }
        };
        context.getContentResolver().registerContentObserver(Words.CONTENT_URI, true, mObserver);
        reloadDictionaryIfRequired();
    }

    public static UserBinaryDictionary getDictionary(
            final Context context, final Locale locale, final File dictFile,
            final String dictNamePrefix, @Nullable final String account) {
        return new UserBinaryDictionary(context, locale, false, dictFile, dictNamePrefix + NAME);
    }

    @Override
    public synchronized void close() {
        if (mObserver != null) {
            mContext.getContentResolver().unregisterContentObserver(mObserver);
            mObserver = null;
        }
        super.close();
    }

    @Override
    public void loadInitialContentsLocked() {
        // Split the locale. For example "en" => ["en"], "de_DE" => ["de", "DE"],
        // "en_US_foo_bar_qux" => ["en", "US", "foo_bar_qux"] because of the limit of 3.
        // This is correct for locale processing. (well, and it sucks e.g. for sr-Latn, resp. sr__#Latn as string)
        // For this example, we'll look at the "en_US_POSIX" case.
        final String[] localeElements =
                TextUtils.isEmpty(mLocaleString) ? new String[] {} : mLocaleString.split("_", 3);
        final int length = localeElements.length;

        final StringBuilder request = new StringBuilder("(locale is NULL)");
        String localeSoFar = "";
        // At start, localeElements = ["en", "US", "POSIX"] ; localeSoFar = "" ;
        // and request = "(locale is NULL)"
        for (int i = 0; i < length; ++i) {
            // i | localeSoFar    | localeElements
            // 0 | ""             | ["en", "US", "POSIX"]
            // 1 | "en_"          | ["en", "US", "POSIX"]
            // 2 | "en_US_"       | ["en", "en_US", "POSIX"]
            localeElements[i] = localeSoFar + localeElements[i];
            localeSoFar = localeElements[i] + "_";
            // i | request
            // 0 | "(locale is NULL)"
            // 1 | "(locale is NULL) or (locale=?)"
            // 2 | "(locale is NULL) or (locale=?) or (locale=?)"
            request.append(" or (locale=?)");
        }
        // At the end, localeElements = ["en", "en_US", "en_US_POSIX"]; localeSoFar = en_US_POSIX_"
        // and request = "(locale is NULL) or (locale=?) or (locale=?) or (locale=?)"

        final String[] requestArguments;
        // If length == 3, we already have all the arguments we need (common prefix is meaningless inside variants)
        if (mAlsoUseMoreRestrictiveLocales && length < 3) {
            request.append(" or (locale like ?)");
            // The following creates an array with one more (null) position
            final String[] localeElementsWithMoreRestrictiveLocalesIncluded =
                    Arrays.copyOf(localeElements, length + 1);
            localeElementsWithMoreRestrictiveLocalesIncluded[length] =
                    localeElements[length - 1] + "_%";
            requestArguments = localeElementsWithMoreRestrictiveLocalesIncluded;
            // If for example localeElements = ["en"]
            // then requestArguments = ["en", "en_%"]
            // and request = (locale is NULL) or (locale=?) or (locale like ?)
            // If localeElements = ["en", "en_US"]
            // then requestArguments = ["en", "en_US", "en_US_%"]
        } else {
            requestArguments = localeElements;
        }
        final String requestString = request.toString();
        try {
            addWordsFromProjectionLocked(PROJECTION_QUERY_WITH_SHORTCUT, requestString, requestArguments);
        } catch (IllegalArgumentException e) {
            // This may happen on some non-compliant devices where the declared API is JB+ but
            // the SHORTCUT column is not present for some reason.
            addWordsFromProjectionLocked(PROJECTION_QUERY_WITHOUT_SHORTCUT, requestString, requestArguments);
        }
    }

    private void addWordsFromProjectionLocked(final String[] query, String request, final String[] requestArguments)
            throws IllegalArgumentException {
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(
                    Words.CONTENT_URI, query, request, requestArguments, null);
            addWordsLocked(cursor);
        } catch (final SQLiteException e) {
            Log.e(TAG, "SQLiteException in the remote User dictionary process.", e);
        } finally {
            try {
                if (null != cursor) cursor.close();
            } catch (final SQLiteException e) {
                Log.e(TAG, "SQLiteException in the remote User dictionary process.", e);
            }
        }
    }

    private static int scaleFrequencyFromDefaultToLatinIme(final int defaultFrequency) {
        // The default frequency for the user dictionary is 250 for historical reasons.
        // Latin IME considers a good value for the default user dictionary frequency
        // is about 160 considering the scale we use. So we are scaling down the values.
        if (defaultFrequency > Integer.MAX_VALUE / LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY) {
            return (defaultFrequency / HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY)
                    * LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY;
        }
        return (defaultFrequency * LATINIME_DEFAULT_USER_DICTIONARY_FREQUENCY)
                / HISTORICAL_DEFAULT_USER_DICTIONARY_FREQUENCY;
    }

    private void addWordsLocked(final Cursor cursor) {
        final boolean hasShortcutColumn = true;
        if (cursor == null) return;
        if (cursor.moveToFirst()) {
            final int indexWord = cursor.getColumnIndex(Words.WORD);
            final int indexShortcut = hasShortcutColumn ? cursor.getColumnIndex(Words.SHORTCUT) : 0;
            final int indexFrequency = cursor.getColumnIndex(Words.FREQUENCY);
            while (!cursor.isAfterLast()) {
                final String word = cursor.getString(indexWord);
                final String shortcut = hasShortcutColumn ? cursor.getString(indexShortcut) : null;
                final int frequency = cursor.getInt(indexFrequency);
                final int adjustedFrequency = scaleFrequencyFromDefaultToLatinIme(frequency);
                // Safeguard against adding really long words.
                if (word.length() <= MAX_WORD_LENGTH) {
                    runGCIfRequiredLocked(true /* mindsBlockByGC */);
                    addUnigramLocked(word, adjustedFrequency, null /* shortcutTarget */,
                            0 /* shortcutFreq */, false /* isNotAWord */,
                            false /* isPossiblyOffensive */,
                            BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                    if (null != shortcut && shortcut.length() <= MAX_WORD_LENGTH) {
                        runGCIfRequiredLocked(true /* mindsBlockByGC */);
                        addUnigramLocked(shortcut, adjustedFrequency, word,
                                USER_DICT_SHORTCUT_FREQUENCY, true /* isNotAWord */,
                                false /* isPossiblyOffensive */,
                                BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                    }
                }
                cursor.moveToNext();
            }
        }
    }
}
