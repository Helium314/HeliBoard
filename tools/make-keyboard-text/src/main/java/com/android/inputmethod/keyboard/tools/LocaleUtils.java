/*
 * Copyright (C) 2013 The Android Open Source Project
 * SPDX-License-Identifier: Apache-2.0
 */

package com.android.inputmethod.keyboard.tools;

import java.util.HashMap;
import java.util.Locale;

/**
 * A class to help with handling Locales in string form.
 *
 * This is a subset of com/android/inputmethod/latin/utils/LocaleUtils.java in order to use
 * for the make-keyboard-text tool.
 */
public final class LocaleUtils {
    public static final Locale DEFAULT_LOCALE = Locale.ROOT;
    private static final String DEFAULT_LOCALE_CODE = "DEFAULT";
    public static final String NO_LANGUAGE_LOCALE_CODE = "zz";
    public static final String NO_LANGUAGE_LOCALE_DISPLAY_NAME = "Alphabet";

    private LocaleUtils() {
        // Intentional empty constructor for utility class.
    }

    private static final HashMap<String, Locale> sLocaleCache = new HashMap<>();

    private static final int INDEX_LANGUAGE = 0;
    private static final int INDEX_SCRIPT = 1;
    private static final int INDEX_REGION = 2;
    private static final int ELEMENT_LIMIT = INDEX_REGION + 1;

    /**
     * Creates a locale from a string specification.
     *
     * Locale string is: language(_script)?(_region)?
     * where: language := [a-zA-Z]{2,3}
     *        script := [a-zA-Z]{4}
     *        region := [a-zA-Z]{2,3}|[0-9]{3}
     */
    public static Locale constructLocaleFromString(final String localeStr) {
        if (localeStr == null) {
            return null;
        }
        synchronized (sLocaleCache) {
            if (sLocaleCache.containsKey(localeStr)) {
                return sLocaleCache.get(localeStr);
            }
            boolean hasRegion = false;
            final Locale.Builder builder = new Locale.Builder();
            final String[] localeElements = localeStr.split("_", ELEMENT_LIMIT);
            if (localeElements.length > INDEX_LANGUAGE) {
                final String text = localeElements[INDEX_LANGUAGE];
                if (isValidLanguage(text)) {
                    builder.setLanguage(text);
                } else {
                    throw new RuntimeException("Unknown locale format: " + localeStr);
                }
            }
            if (localeElements.length > INDEX_SCRIPT) {
                final String text = localeElements[INDEX_SCRIPT];
                if (isValidScript(text)) {
                    builder.setScript(text);
                } else if (isValidRegion(text)) {
                    builder.setRegion(text);
                    hasRegion = true;
                } else {
                    throw new RuntimeException("Unknown locale format: " + localeStr);
                }
            }
            if (localeElements.length > INDEX_REGION) {
                final String text = localeElements[INDEX_REGION];
                if (!hasRegion && isValidRegion(text)) {
                    builder.setRegion(text);
                } else {
                    throw new RuntimeException("Unknown locale format: " + localeStr);
                }
            }
            final Locale locale = builder.build();
            sLocaleCache.put(localeStr, locale);
            return locale;
        }
    }

    private static final int MIN_LENGTH_OF_LANGUAGE = 2;
    private static final int MAX_LENGTH_OF_LANGUAGE = 2;
    private static final int LENGTH_OF_SCRIPT = 4;
    private static final int MIN_LENGTH_OF_REGION = 2;
    private static final int MAX_LENGTH_OF_REGION = 2;
    private static final int LENGTH_OF_AREA_CODE = 3;

    private static boolean isValidLanguage(final String text) {
        return isAlphabetSequence(text, MIN_LENGTH_OF_LANGUAGE, MAX_LENGTH_OF_LANGUAGE);
    }

    private static boolean isValidScript(final String text) {
        return isAlphabetSequence(text, LENGTH_OF_SCRIPT, LENGTH_OF_SCRIPT);
    }

    private static boolean isValidRegion(final String text) {
        return isAlphabetSequence(text, MIN_LENGTH_OF_REGION, MAX_LENGTH_OF_REGION)
                || isDigitSequence(text, LENGTH_OF_AREA_CODE, LENGTH_OF_AREA_CODE);
    }

    private static boolean isAlphabetSequence(final String text, final int lower, final int upper) {
        final int length = text.length();
        if (length < lower || length > upper) {
            return false;
        }
        for (int index = 0; index < length; index++) {
            if (!isAsciiAlphabet(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDigitSequence(final String text, final int lower, final int upper) {
        final int length = text.length();
        if (length < lower || length > upper) {
            return false;
        }
        for (int index = 0; index < length; ++index) {
            if (!isAsciiDigit(text.charAt(index))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isAsciiAlphabet(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
    }

    private static boolean isAsciiDigit(char c) {
        return c >= '0' && c <= '9';
    }

    public static String getLocaleCode(final Locale locale) {
        if (locale == DEFAULT_LOCALE) {
            return DEFAULT_LOCALE_CODE;
        }
        return locale.toString();
    }

    public static String getLocaleDisplayName(final Locale locale) {
        if (locale == DEFAULT_LOCALE) {
            return DEFAULT_LOCALE_CODE;
        }
        if (locale.getLanguage().equals(NO_LANGUAGE_LOCALE_CODE)) {
            return NO_LANGUAGE_LOCALE_DISPLAY_NAME;
        }
        return locale.getDisplayName(Locale.ENGLISH);
    }
}
