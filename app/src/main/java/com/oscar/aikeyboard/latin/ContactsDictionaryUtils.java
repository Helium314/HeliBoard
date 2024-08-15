/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin;


import com.oscar.aikeyboard.latin.common.Constants;

import java.util.Locale;

/**
 * Utility methods related contacts dictionary.
 */
public class ContactsDictionaryUtils {

    /**
     * Returns the index of the last letter in the word, starting from position startIndex.
     */
    public static int getWordEndPosition(final String string, final int len,
            final int startIndex) {
        int end;
        int cp = 0;
        for (end = startIndex + 1; end < len; end += Character.charCount(cp)) {
            cp = string.codePointAt(end);
            if (cp != Constants.CODE_DASH && cp != Constants.CODE_SINGLE_QUOTE
                   && !Character.isLetter(cp)) {
                break;
            }
        }
        return end;
    }

    /**
     * Returns true if the locale supports using first name and last name as bigrams.
     */
    public static boolean useFirstLastBigramsForLocale(final Locale locale) {
        // TODO: Add firstname/lastname bigram rules for other languages.
        return locale != null && locale.getLanguage().equals(Locale.ENGLISH.getLanguage());
    }
}
