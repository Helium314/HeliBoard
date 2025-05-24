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

    // Keyboard layout to subtype name resource id map.
    private val keyboardLayoutToNameIds = HashMap<String, Int>()

    // Exceptional locale whose name should be displayed in Locale.ROOT.
    private val exceptionalLocaleDisplayedInRootLocale = HashMap<String, Int>()

    // Exceptional locale to subtype name resource id map.
    private val exceptionalLocaleToNameIds = HashMap<String, Int>()

    // Exceptional locale to subtype name with layout resource id map.
    private val exceptionalLocaleToWithLayoutNameIds = HashMap<String, Int>()
    private val resourceSubtypeDisplayNames = HashMap<Int, String>()

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
        val packageName = context.packageName
        resources = context.resources

        // todo: layout names are currently translatable in subtype_no_language_* but not the default names
        //  just remove the separate "alphabet (<layout>)" strings and have a single "alphabet (%s)"?
        //  or rather use the same style as for languages and only have "alphabet"
        val predefinedLayouts = resources.getStringArray(R.array.predefined_layouts)
        val layoutDisplayNames = resources.getStringArray(R.array.predefined_layout_display_names)
        for (i in predefinedLayouts.indices) {
            val layoutName = predefinedLayouts[i]
            keyboardLayoutToDisplayName[layoutName] = layoutDisplayNames[i]
            val resourceName = SUBTYPE_NAME_RESOURCE_GENERIC_PREFIX + layoutName
            val resId = resources.getIdentifier(resourceName, null, packageName)
            keyboardLayoutToNameIds[layoutName] = resId
            // Register subtype name resource id of "No language" with key "zz_<layout>"
            val noLanguageResName = SUBTYPE_NAME_RESOURCE_NO_LANGUAGE_PREFIX + layoutName
            val noLanguageResId = resources.getIdentifier(noLanguageResName, null, packageName)
            val key = getNoLanguageLayoutKey(layoutName)
            keyboardLayoutToNameIds[key] = noLanguageResId
        }

        // todo: do it using 2 arrays like predefined_layouts (and adjust information in layouts.md)
        val exceptionalLocaleInRootLocale = resources.getStringArray(R.array.subtype_locale_displayed_in_root_locale)
        for (languageTag in exceptionalLocaleInRootLocale) {
            val resourceName = SUBTYPE_NAME_RESOURCE_IN_ROOT_LOCALE_PREFIX + languageTag.replace('-', '_')
            val resId = resources.getIdentifier(resourceName, null, packageName)
            exceptionalLocaleDisplayedInRootLocale[languageTag] = resId
        }

        // todo: do it using 2 arrays like predefined_layouts (and adjust information in layouts.md)
        //  and the _with_layout variants can be removed?
        val exceptionalLocales = resources.getStringArray(R.array.subtype_locale_exception_keys)
        for (languageTag in exceptionalLocales) {
            val resourceName = SUBTYPE_NAME_RESOURCE_PREFIX + languageTag.replace('-', '_')
            val resId = resources.getIdentifier(resourceName, null, packageName)
            exceptionalLocaleToNameIds[languageTag] = resId
            val resourceNameWithLayout = SUBTYPE_NAME_RESOURCE_WITH_LAYOUT_PREFIX + languageTag.replace('-', '_')
            val resIdWithLayout = resources.getIdentifier(resourceNameWithLayout, null, packageName)
            exceptionalLocaleToWithLayoutNameIds[languageTag] = resIdWithLayout
        }
    }

    fun isExceptionalLocale(locale: Locale): Boolean {
        return exceptionalLocaleToNameIds.containsKey(locale.toLanguageTag())
    }

    private fun getNoLanguageLayoutKey(keyboardLayoutName: String): String {
        return NO_LANGUAGE + "_" + keyboardLayoutName
    }

    fun getSubtypeNameResId(locale: Locale, keyboardLayoutName: String): Int {
        val languageTag = locale.toLanguageTag()
        if (isExceptionalLocale(locale)) {
            return exceptionalLocaleToWithLayoutNameIds[languageTag]!!
        }
        val key = if (languageTag == NO_LANGUAGE) getNoLanguageLayoutKey(keyboardLayoutName)
            else keyboardLayoutName
        return keyboardLayoutToNameIds[key] ?: UNKNOWN_KEYBOARD_LAYOUT
    }

    private fun getDisplayLocaleOfSubtypeLocale(locale: Locale): Locale {
        val languageTag = locale.toLanguageTag()
        if (languageTag == NO_LANGUAGE)
            return resources.configuration.locale()
        if (exceptionalLocaleDisplayedInRootLocale.containsKey(languageTag))
            return Locale.ROOT
        return locale
    }

    fun getSubtypeLocaleDisplayNameInSystemLocale(locale: Locale): String {
        val displayLocale = resources.configuration.locale()
        return getSubtypeLocaleDisplayNameInternal(locale, displayLocale)
    }

    fun getSubtypeLocaleDisplayName(locale: Locale): String {
        val displayLocale = getDisplayLocaleOfSubtypeLocale(locale)
        return getSubtypeLocaleDisplayNameInternal(locale, displayLocale)
    }

    fun getSubtypeLanguageDisplayName(locale: Locale): String {
        val languageLocale = if (exceptionalLocaleDisplayedInRootLocale.containsKey(locale.toLanguageTag()))
            locale
        else
            locale.language.constructLocale()
        return getSubtypeLocaleDisplayNameInternal(languageLocale, getDisplayLocaleOfSubtypeLocale(locale))
    }

    private fun getSubtypeLocaleDisplayNameInternal(locale: Locale, displayLocale: Locale): String {
        val languageTag = locale.toLanguageTag()
        if (languageTag == NO_LANGUAGE) {
            // "No language" subtype should be displayed in system locale.
            return resources.getString(R.string.subtype_no_language)
        }
        val exceptionalNameResId = if (displayLocale == Locale.ROOT
            && exceptionalLocaleDisplayedInRootLocale.containsKey(languageTag)
        )
            exceptionalLocaleDisplayedInRootLocale[languageTag]
        else
            exceptionalLocaleToNameIds[languageTag]
        val displayName = if (exceptionalNameResId != null) {
            runInLocale(resources, displayLocale) { res: Resources -> res.getString(exceptionalNameResId) }
        } else {
            locale.localizedDisplayName(resources, displayLocale)
        }
        return StringUtils.capitalizeFirstCodePoint(displayName, displayLocale)
    }

    // InputMethodSubtype's display name in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  en_US qwerty  F  English (US)            exception
    //  en_GB qwerty  F  English (UK)            exception
    //  es_US spanish F  Español (EE.UU.)        exception
    //  fr    azerty  F  Français
    //  fr_CA qwerty  F  Français (Canada)
    //  fr_CH swiss   F  Français (Suisse)
    //  de    qwertz  F  Deutsch
    //  de_CH swiss   T  Deutsch (Schweiz)
    //  zz    qwerty  F  Alphabet (QWERTY)       in system locale
    //  fr    qwertz  T  Français (QWERTZ)
    //  de    qwerty  T  Deutsch (QWERTY)
    //  en_US azerty  T  English (US) (AZERTY)   exception
    //  zz    azerty  T  Alphabet (AZERTY)       in system locale
    private fun getReplacementString(subtype: InputMethodSubtype, displayLocale: Locale): String =
        subtype.getExtraValueOf(ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME)
            ?: getSubtypeLocaleDisplayNameInternal(subtype.locale(), displayLocale)

    fun getDisplayNameInSystemLocale(mainLayoutName: String, locale: Locale): String {
        getMainLayoutDisplayName(mainLayoutName)?.let { return it } // works for custom and latin layouts

        // we have some locale-specific layout
        for (subtype in SubtypeSettings.getResourceSubtypesForLocale(locale)) {
            if (mainLayoutName == getMainLayoutFromExtraValue(subtype.extraValue))
                return getSubtypeDisplayNameInSystemLocale(subtype)
        }
        return mainLayoutName // should never happen...
    }

    fun getSubtypeDisplayNameInSystemLocale(subtype: InputMethodSubtype): String {
        resourceSubtypeDisplayNames[subtype.hashCode()]?.let { return it }

        val displayName = getSubtypeDisplayNameInternal(subtype, resources.configuration.locale())
        if (!subtype.containsExtraValueKey(ExtraValue.IS_ADDITIONAL_SUBTYPE)) {
            resourceSubtypeDisplayNames[subtype.hashCode()] = displayName
        }
        return displayName
    }

    @JvmStatic
    fun clearDisplayNameCache() {
        resourceSubtypeDisplayNames.clear()
    }

    @JvmStatic
    fun getSubtypeNameForLogging(subtype: InputMethodSubtype?): String {
        if (subtype == null) {
            return "<null subtype>"
        }
        return subtype.locale().toString() + "/" + subtype.mainLayoutNameOrQwerty()
    }

    private fun getSubtypeDisplayNameInternal(subtype: InputMethodSubtype, displayLocale: Locale): String {
        val replacementString = getReplacementString(subtype, displayLocale)
        return runInLocale(resources, displayLocale) { res: Resources ->
            try {
                StringUtils.capitalizeFirstCodePoint(res.getString(subtype.nameResId, replacementString), displayLocale)
            } catch (e: Resources.NotFoundException) {
                Log.w(TAG, ("Unknown subtype: mode=${subtype.mode} nameResId=${subtype.nameResId} locale=${subtype.locale()} extra=${subtype.extraValue}\n${DebugLogUtils.getStackTrace()}"))
                ""
            }
        }
    }

    fun getMainLayoutDisplayName(layoutName: String): String? =
        if (LayoutUtilsCustom.isCustomLayout(layoutName)) LayoutUtilsCustom.getDisplayName(layoutName)
        else keyboardLayoutToDisplayName[layoutName]

    fun InputMethodSubtype.displayName(): String {
        val layoutName = mainLayoutNameOrQwerty()
        if (LayoutUtilsCustom.isCustomLayout(layoutName))
            return "${locale().localizedDisplayName(resources)} (${LayoutUtilsCustom.getDisplayName(layoutName)})"
        return getSubtypeDisplayNameInSystemLocale(this)
    }

    @JvmStatic
    fun getCombiningRulesExtraValue(subtype: InputMethodSubtype): String? = subtype.getExtraValueOf(ExtraValue.COMBINING_RULES)

    // Special language code to represent "no language".
    const val NO_LANGUAGE = "zz"
    const val QWERTY = "qwerty"
    const val EMOJI = "emoji"
    val UNKNOWN_KEYBOARD_LAYOUT  = R.string.subtype_generic

    private val TAG = SubtypeLocaleUtils::class.java.simpleName
    private const val SUBTYPE_NAME_RESOURCE_PREFIX = "string/subtype_"
    private const val SUBTYPE_NAME_RESOURCE_GENERIC_PREFIX = "string/subtype_generic_"
    private const val SUBTYPE_NAME_RESOURCE_WITH_LAYOUT_PREFIX = "string/subtype_with_layout_"
    private const val SUBTYPE_NAME_RESOURCE_NO_LANGUAGE_PREFIX = "string/subtype_no_language_"
    private const val SUBTYPE_NAME_RESOURCE_IN_ROOT_LOCALE_PREFIX = "string/subtype_in_root_locale_"
}
