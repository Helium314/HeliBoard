/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.res.Resources
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.compat.locale
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.LocaleUtils.localizedDisplayName
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.utils.LayoutType.Companion.getMainLayoutFromExtraValue
import java.util.Locale
import kotlin.concurrent.Volatile

/**
 * A helper class to deal with subtype locales.
 */
object SubtypeLocaleUtils {
    @Volatile
    private var initialized = false
    private lateinit var resources: Resources

    // Keyboard layout to its display name map.
    private val keyboardLayoutToDisplayName = HashMap<String, String>()

    // Exceptional locale whose name should be displayed in Locale.ROOT.
    private val exceptionalLocaleDisplayedInRootLocale = HashMap<String, String>()

    private val resourceSubtypeDisplayNameCache = HashMap<Int, String>()

    // Note that this initialization method can be called multiple times.
    @JvmStatic
    fun init(context: Context) {
        synchronized(this) {
            if (!initialized) {
                initLocked(context)
                initialized = true
            }
        }
    }

    private fun initLocked(context: Context) {
        resources = context.resources

        // todo: layout names are currently translatable in subtype_no_language_* but not the default names
        //  just remove the separate "alphabet (<layout>)" strings and have a single "alphabet (%s)"?
        //  or rather use the same style as for languages and only have "alphabet"
        val predefinedLayouts = resources.getStringArray(R.array.predefined_layouts)
        val layoutDisplayNames = resources.getStringArray(R.array.predefined_layout_display_names)
        for (i in predefinedLayouts.indices) {
            val layoutName = predefinedLayouts[i]
            keyboardLayoutToDisplayName[layoutName] = layoutDisplayNames[i]
        }

        val exceptionalLocaleInRootLocale = resources.getStringArray(R.array.subtype_locale_displayed_in_root_locale)
        val exceptionalLocaleInRootLocaleDisplayNames = resources.getStringArray(R.array.subtype_locale_displayed_in_root_locale_display_names)
        for (i in exceptionalLocaleInRootLocale.indices) {
            exceptionalLocaleDisplayedInRootLocale[exceptionalLocaleInRootLocale[i]] = exceptionalLocaleInRootLocaleDisplayNames[i]
        }
    }

    // see SubtypeUtilsAdditional.getAdditionalExtraValues, currently not needed
    //fun isExceptionalLocale(locale: Locale) = exceptionalLocaleDisplayedInRootLocale.containsKey(locale.toLanguageTag())

    /** Usually the [locale], but Locale.ROOT for exceptionalLocaleDisplayedInRootLocale, and system locale for NO_LANGUAGE */
    private fun getDisplayLocaleOfSubtypeLocale(locale: Locale): Locale {
        val languageTag = locale.toLanguageTag()
        if (languageTag == NO_LANGUAGE)
            return resources.configuration.locale()
        if (exceptionalLocaleDisplayedInRootLocale.containsKey(languageTag))
            return Locale.ROOT
        return locale
    }

    /** Returns the full locale display name for use on space bar (considers exceptionalLocaleDisplayedInRootLocale) */
    fun getSubtypeLocaleDisplayName(locale: Locale): String {
        val displayLocale = getDisplayLocaleOfSubtypeLocale(locale)
        return getSubtypeLocaleDisplayNameInternal(locale, displayLocale)
    }

    /** Returns the language display name for use on space bar (considers exceptionalLocaleDisplayedInRootLocale) */
    fun getSubtypeLanguageDisplayName(locale: Locale): String {
        val languageLocale = if (exceptionalLocaleDisplayedInRootLocale.containsKey(locale.toLanguageTag()))
            locale
        else
            locale.language.constructLocale()
        return getSubtypeLocaleDisplayNameInternal(languageLocale, getDisplayLocaleOfSubtypeLocale(locale))
    }

    /**
     *  Display name of subtype [locale] in [displayLocale].
     *  Considers exceptionalLocaleDisplayedInRootLocale and exceptionalLocaleToNameIds, defaults to Locale.localizedDisplayName.
     */
    private fun getSubtypeLocaleDisplayNameInternal(locale: Locale, displayLocale: Locale): String {
        val languageTag = locale.toLanguageTag()
        if (languageTag == NO_LANGUAGE) {
            // "No language" subtype should be displayed in system locale.
            return resources.getString(R.string.subtype_no_language)
        }
        val displayName = if (displayLocale == Locale.ROOT && exceptionalLocaleDisplayedInRootLocale.containsKey(languageTag)) {
            exceptionalLocaleDisplayedInRootLocale[languageTag]!!
        } else {
            locale.localizedDisplayName(resources, displayLocale)
        }
        return StringUtils.capitalizeFirstCodePoint(displayName, displayLocale)
    }

    @JvmStatic
    fun clearSubtypeDisplayNameCache() {
        resourceSubtypeDisplayNameCache.clear()
    }

    @JvmStatic
    fun getSubtypeNameForLogging(subtype: InputMethodSubtype?): String {
        if (subtype == null) {
            return "<null subtype>"
        }
        return subtype.locale().toString() + "/" + subtype.mainLayoutNameOrQwerty()
    }

    /** Subtype display name is <Locale> (<Layout>), defaults to system locale */
    fun InputMethodSubtype.displayName(displayLocale: Locale? = null): String {
        if (displayLocale == null) resourceSubtypeDisplayNameCache[hashCode()]?.let { return it }

        val layoutName = mainLayoutName()
        if (layoutName != null && LayoutUtilsCustom.isCustomLayout(layoutName)) {
            return resources.getString(
                R.string.subtype_with_layout_generic,
                locale().localizedDisplayName(resources, displayLocale),
                LayoutUtilsCustom.getDisplayName(layoutName)
            )
        }
        if (keyboardLayoutToDisplayName.containsKey(layoutName)) {
            return resources.getString(
                R.string.subtype_with_layout_generic,
                locale().localizedDisplayName(resources, displayLocale),
                keyboardLayoutToDisplayName[layoutName]
            )
        }

        val actualDisplayLocale = displayLocale ?: resources.configuration.locale()
        // replacement for %s in nameResId, which now always is the locale
        // not read from ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME any more
        val replacementString = getSubtypeLocaleDisplayNameInternal(locale(), actualDisplayLocale)

        val name = runCatching {
            if (displayLocale == null) resources.getString(nameResId, replacementString)
            else runInLocale(resources, displayLocale) { resources.getString(nameResId, replacementString) }
        }.getOrNull() ?: locale().localizedDisplayName(resources, displayLocale)
        val displayName = StringUtils.capitalizeFirstCodePoint(name, actualDisplayLocale)
        if (displayLocale == null && !containsExtraValueKey(ExtraValue.IS_ADDITIONAL_SUBTYPE))
            resourceSubtypeDisplayNameCache[hashCode()] = displayName
        return displayName
    }

    fun getMainLayoutDisplayName(layoutName: String): String? =
        if (LayoutUtilsCustom.isCustomLayout(layoutName)) LayoutUtilsCustom.getDisplayName(layoutName)
        else keyboardLayoutToDisplayName[layoutName]

    fun getLayoutDisplayNameInSystemLocale(mainLayoutName: String, locale: Locale): String {
        getMainLayoutDisplayName(mainLayoutName)?.let { return it } // works for custom and latin layouts

        // we have some locale-specific layout, use the subtype name
        for (subtype in SubtypeSettings.getResourceSubtypesForLocale(locale)) {
            if (mainLayoutName == getMainLayoutFromExtraValue(subtype.extraValue))
                return subtype.displayName()
        }
        return mainLayoutName // should never happen...
    }

    @JvmStatic
    fun getCombiningRulesExtraValue(subtype: InputMethodSubtype): String? = subtype.getExtraValueOf(ExtraValue.COMBINING_RULES)

    // Special language code to represent "no language".
    const val NO_LANGUAGE = "zz"
    const val QWERTY = "qwerty"
    const val EMOJI = "emoji"
    val UNKNOWN_KEYBOARD_LAYOUT  = R.string.subtype_generic
}
