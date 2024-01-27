/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.latin.common

import android.content.Context
import android.os.Build
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils.script
import java.util.Locale

/**
 * A class to help with handling Locales in string form.
 *
 *
 * This file has the same meaning and features (and shares all of its code) with the one with the
 * same name in Latin IME. They need to be kept synchronized; for any update/bugfix to
 * this file, consider also updating/fixing the version in Latin IME.
 */
object LocaleUtils {
    // Locale match level constants.
    // A higher level of match is guaranteed to have a higher numerical value.
    // Some room is left within constants to add match cases that may arise necessary
    // in the future, for example differentiating between the case where the countries
    // are both present and different, and the case where one of the locales does not
    // specify the countries. This difference is not needed now.
    // Nothing matches.
    private const val LOCALE_NO_MATCH = 0

    // The language (and maybe more) matches, but the script is different
    private const val LOCALE_MATCH_SCRIPT_DIFFER = 1

    // The languages matches, but the country are different. Or, the reference locale requires a
    // country and the tested locale does not have one.
    private const val LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER = 3

    // The languages and country match, but the variants are different. Or, the reference locale
    // requires a variant and the tested locale does not have one.
    private const val LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER = 6

    // The required locale is null or empty so it will accept anything, and the tested locale
    // is non-null and non-empty.
    private const val LOCALE_ANY_MATCH = 10

    // The language matches, and the tested locale specifies a country but the reference locale
    // does not require one.
    private const val LOCALE_LANGUAGE_MATCH = 15

    // The language and the country match, and the tested locale specifies a variant but the
    // reference locale does not require one.
    private const val LOCALE_LANGUAGE_AND_COUNTRY_MATCH = 20

    // The compared locales are fully identical. This is the best match level.
    private const val LOCALE_FULL_MATCH = 30

    /**
     * Return how well a tested locale matches a reference locale.
     *
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
     * @param reference the reference locale to test against.
     * @param tested the locale to test.
     * @return a constant that measures how well the tested locale matches the reference locale.
     */
    fun getMatchLevel(reference: Locale, tested: Locale): Int {
        if (reference == tested) return LOCALE_FULL_MATCH
        if (reference.toString().isEmpty()) return LOCALE_ANY_MATCH
        if (reference.language != tested.language) return LOCALE_NO_MATCH
        // language matches
        if (reference.script() != tested.script()) {
            return LOCALE_MATCH_SCRIPT_DIFFER
        }
        // script matches
        if (reference.country != tested.country) {
            return if (reference.country.isEmpty()) LOCALE_LANGUAGE_MATCH
                else LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER
        }
        // country matches
        return if (reference.variant == tested.variant) LOCALE_FULL_MATCH
            else if (reference.variant.isEmpty()) LOCALE_LANGUAGE_AND_COUNTRY_MATCH
            else LOCALE_LANGUAGE_AND_COUNTRY_MATCH_VARIANT_DIFFER
    }

    /**
     * Find out whether a match level should be considered a match.
     *
     *
     * This method takes a match level as returned by the #getMatchLevel method, and returns whether
     * it should be considered a match in the usual sense with standard Locale functions.
     *
     * @param level the match level, as returned by getMatchLevel.
     * @return whether this is a match or not.
     */
    fun isMatch(level: Int): Boolean {
        return level >= LOCALE_LANGUAGE_MATCH_COUNTRY_DIFFER
    }

    private val sLocaleCache = HashMap<String, Locale>()

    /**
     * Creates a locale from a string specification or language tag.
     * @param localeString a string specification of a locale, in a format of "ll_cc_variant" where
     * "ll" is a language code, "cc" is a country code.
     * Converts zz regions that used to signal latin script into actual latin script (using language tag).
     * If a localeString contains "-" it is interpreted as language tag.
     */
    @JvmStatic
    fun constructLocaleFromString(localeString: String): Locale {
        synchronized(sLocaleCache) {
            sLocaleCache[localeString]?.let { return it }
            if (localeString.contains("-")) {
                // looks like it's actually a language tag, and not a locale string
                val locale = Locale.forLanguageTag(localeString)
                sLocaleCache[localeString] = locale
                return locale
            }
            val elements = localeString.split("_", limit = 3)
            val locale = if (elements.size == 1) {
                Locale(elements[0]) // "zz" works both in constructor and forLanguageTag
            } else if (elements.size == 2) {
                if (elements[1].lowercase() == "zz") Locale.forLanguageTag(elements[0] + "-Latn")
                else Locale(elements[0], elements[1])
            } else if (elements[1].lowercase() == "zz") { // localeParams.length == 3
                Locale.Builder().setLanguage(elements[0]).setVariant(elements[2]).setScript("Latn").build()
            } else  {
                Locale(elements[0], elements[1], elements[2])
            }
            sLocaleCache[localeString] = locale
            return locale
        }
    }

    @JvmStatic
    fun isRtlLanguage(locale: Locale): Boolean =
        when (Character.getDirectionality(locale.displayName.codePointAt(0))) {
            Character.DIRECTIONALITY_RIGHT_TO_LEFT, Character.DIRECTIONALITY_RIGHT_TO_LEFT_ARABIC -> true
            else -> false
        }

    @JvmStatic
    fun getLocaleDisplayNameInSystemLocale(locale: Locale, context: Context): String {
        // todo:
        //  either need different toString method that considers non-standard scripts
        //  or recognize non-default script and rename subtype_ resources
        //  or add manual exceptions for some locales (plz no)
        val localeString = locale.toString()
        if (localeString == "zz") return context.getString(R.string.subtype_no_language)
        if (localeString.endsWith("_ZZ") || localeString.endsWith("_zz")) {
            val resId = context.resources.getIdentifier("subtype_$localeString", "string", context.packageName)
            if (resId != 0) return context.getString(resId)
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            locale.getDisplayName(context.resources.configuration.locales[0])
        } else {
            @Suppress("deprecation") locale.getDisplayName(context.resources.configuration.locale)
        }
    }
}
