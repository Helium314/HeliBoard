/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dslul.openboard.inputmethod.latin.common;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.latin.R;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;

/**
 * A class to help with handling Locales in string form.
 *
 * This file has the same meaning and features (and shares all of its code) with the one with the
 * same name in Latin IME. They need to be kept synchronized; for any update/bugfix to
 * this file, consider also updating/fixing the version in Latin IME.
 */
public final class LocaleUtils {
    private LocaleUtils() {
        // Intentional empty constructor for utility class.
    }

    // Locale match level constants.
    // A higher level of match is guaranteed to have a higher numerical value.
    // Some room is left within constants to add match cases that may arise necessary
    // in the future, for example differentiating between the case where the countries
    // are both present and different, and the case where one of the locales does not
    // specify the countries. This difference is not needed now.

    // Nothing matches.
    public static final int LOCALE_NO_MATCH = 0;
    // The languages matches, but the country are different. Or, the reference locale requires a
    // country and the tested locale does not have one.
    public static final int LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER = 3;
    // The languages and country match, but the variants are different. Or, the reference locale
    // requires a variant and the tested locale does not have one.
    public static final int LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER = 6;
    // The required locale is null or empty so it will accept anything, and the tested locale
    // is non-null and non-empty.
    public static final int LOCALE_ANY_MATCH = 10;
    // The language matches, and the tested locale specifies a country but the reference locale
    // does not require one.
    public static final int LOCALE_LANGUAGE_MATCH = 15;
    // The language and the country match, and the tested locale specifies a variant but the
    // reference locale does not require one.
    public static final int LOCALE_LANGUAGE_AND_COUNTRY_MATCH = 20;
    // The compared locales are fully identical. This is the best match level.
    public static final int LOCALE_FULL_MATCH = 30;

    // The level at which a match is "normally" considered a locale match with standard algorithms.
    // Don't use this directly, use #isMatch to test.
    private static final int LOCALE_MATCH = LOCALE_ANY_MATCH;

    // Make this match the maximum match level. If this evolves to have more than 2 digits
    // when written in base 10, also adjust the getMatchLevelSortedString method.
    private static final int MATCH_LEVEL_MAX = 30;

    /**
     * Return how well a tested locale matches a reference locale.
     *
     * This will check the tested locale against the reference locale and return a measure of how
     * a well it matches the reference. The general idea is that the tested locale has to match
     * every specified part of the required locale. A full match occur when they are equal, a
     * partial match when the tested locale agrees with the reference locale but is more specific,
     * and a difference when the tested locale does not comply with all requirements from the
     * reference locale.
     * In more detail, if the reference locale specifies at least a language and the testedLocale
     * does not specify one, or specifies a different one, LOCALE_NO_MATCH is returned. If the
     * reference locale is empty or null, it will match anything - in the form of LOCALE_FULL_MATCH
     * if the tested locale is empty or null, and LOCALE_ANY_MATCH otherwise. If the reference and
     * tested locale agree on the language, but not on the country,
     * LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER is returned if the reference locale specifies a country,
     * and LOCALE_LANGUAGE_MATCH otherwise.
     * If they agree on both the language and the country, but not on the variant,
     * LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER is returned if the reference locale
     * specifies a variant, and LOCALE_LANGUAGE_AND_COUNTRY_MATCH otherwise. If everything matches,
     * LOCALE_FULL_MATCH is returned.
     * Examples:
     * en <=> en_US  => LOCALE_LANGUAGE_MATCH
     * en_US <=> en => LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER
     * en_US_POSIX <=> en_US_Android  =>  LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER
     * en_US <=> en_US_Android => LOCALE_LANGUAGE_AND_COUNTRY_MATCH
     * sp_US <=> en_US  =>  LOCALE_NO_MATCH
     * de <=> de  => LOCALE_FULL_MATCH
     * en_US <=> en_US => LOCALE_FULL_MATCH
     * "" <=> en_US => LOCALE_ANY_MATCH
     *
     * @param referenceLocale the reference locale to test against.
     * @param testedLocale the locale to test.
     * @return a constant that measures how well the tested locale matches the reference locale.
     */
    public static int getMatchLevel(@Nullable final String referenceLocale,
            @Nullable final String testedLocale) {
        if (StringUtils.isEmpty(referenceLocale)) {
            return StringUtils.isEmpty(testedLocale) ? LOCALE_FULL_MATCH : LOCALE_ANY_MATCH;
        }
        if (null == testedLocale) return LOCALE_NO_MATCH;
        final String[] referenceParams = referenceLocale.split("_", 3);
        final String[] testedParams = testedLocale.split("_", 3);
        // By spec of String#split, [0] cannot be null and length cannot be 0.
        if (!referenceParams[0].equals(testedParams[0])) return LOCALE_NO_MATCH;
        switch (referenceParams.length) {
        case 1:
            return 1 == testedParams.length ? LOCALE_FULL_MATCH : LOCALE_LANGUAGE_MATCH;
        case 2:
            if (1 == testedParams.length) return LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER;
            if (!referenceParams[1].equals(testedParams[1]))
                return LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER;
            if (3 == testedParams.length) return LOCALE_LANGUAGE_AND_COUNTRY_MATCH;
            return LOCALE_FULL_MATCH;
        case 3:
            if (1 == testedParams.length) return LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER;
            if (!referenceParams[1].equals(testedParams[1]))
                return LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER;
            if (2 == testedParams.length) return LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER;
            if (!referenceParams[2].equals(testedParams[2]))
                return LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER;
            return LOCALE_FULL_MATCH;
        }
        // It should be impossible to come here
        return LOCALE_NO_MATCH;
    }

    /**
     * Return a string that represents this match level, with better matches first.
     *
     * The strings are sorted in lexicographic order: a better match will always be less than
     * a worse match when compared together.
     */
    public static String getMatchLevelSortedString(final int matchLevel) {
        // This works because the match levels are 0~99 (actually 0~30)
        // Ideally this should use a number of digits equals to the 1og10 of the greater matchLevel
        return String.format(Locale.ROOT, "%02d", MATCH_LEVEL_MAX - matchLevel);
    }

    /**
     * Find out whether a match level should be considered a match.
     *
     * This method takes a match level as returned by the #getMatchLevel method, and returns whether
     * it should be considered a match in the usual sense with standard Locale functions.
     *
     * @param level the match level, as returned by getMatchLevel.
     * @return whether this is a match or not.
     */
    public static boolean isMatch(final int level) {
        return LOCALE_MATCH <= level;
    }

    /** similar to isMatch, but returns true if there is anything matching (used for fallback) */
    public static boolean isMatchWeak(final int level) {
        return level > LOCALE_NO_MATCH;
    }

    private static final HashMap<String, Locale> sLocaleCache = new HashMap<>();

    /**
     * Creates a locale from a string specification.
     * @param localeString a string specification of a locale, in a format of "ll_cc_variant" where
     * "ll" is a language code, "cc" is a country code.
     */
    @NonNull
    public static Locale constructLocaleFromString(@NonNull final String localeString) {
        synchronized (sLocaleCache) {
            if (sLocaleCache.containsKey(localeString)) {
                return sLocaleCache.get(localeString);
            }
            final String[] elements = localeString.split("_", 3);
            final Locale locale;
            if (elements.length == 1) {
                locale = new Locale(elements[0] /* language */);
            } else if (elements.length == 2) {
                locale = new Locale(elements[0] /* language */, elements[1] /* country */);
            } else { // localeParams.length == 3
                locale = new Locale(elements[0] /* language */, elements[1] /* country */,
                        elements[2] /* variant */);
            }
            sLocaleCache.put(localeString, locale);
            return locale;
        }
    }

    // TODO: Get this information from the framework instead of maintaining here by ourselves.
    private static final HashSet<String> sRtlLanguageCodes = new HashSet<>();
    static {
        // List of known Right-To-Left language codes.
        sRtlLanguageCodes.add("ar"); // Arabic
        sRtlLanguageCodes.add("fa"); // Persian
        sRtlLanguageCodes.add("iw"); // Hebrew
        sRtlLanguageCodes.add("ku"); // Kurdish
        sRtlLanguageCodes.add("ps"); // Pashto
        sRtlLanguageCodes.add("sd"); // Sindhi
        sRtlLanguageCodes.add("ug"); // Uyghur
        sRtlLanguageCodes.add("ur"); // Urdu
        sRtlLanguageCodes.add("yi"); // Yiddish
    }

    public static boolean isRtlLanguage(@NonNull final Locale locale) {
        return sRtlLanguageCodes.contains(locale.getLanguage());
    }

    public static String getLocaleDisplayNameInSystemLocale(final Locale locale, final Context context) {
        final String localeString = locale.toString();
        if (localeString.equals("zz"))
            return context.getString(R.string.subtype_no_language);
        if (localeString.endsWith("_ZZ") || localeString.endsWith("_zz")) {
            final int resId = context.getResources()
                    .getIdentifier("subtype_"+localeString, "string", context.getPackageName());
            if (resId != 0)
                return context.getString(resId);
        }
        return locale.getDisplayName(context.getResources().getConfiguration().locale);
    }
}
