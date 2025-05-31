/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin

import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.utils.LayoutType
import helium314.keyboard.latin.utils.LayoutUtilsCustom
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.SubtypeLocaleUtils
import helium314.keyboard.latin.utils.SubtypeSettings
import helium314.keyboard.latin.utils.locale
import helium314.keyboard.latin.utils.mainLayoutNameOrQwerty
import java.util.Locale

/**
 * Enrichment class for InputMethodSubtype that extracts settings from extra values
 */
class RichInputMethodSubtype private constructor(val rawSubtype: InputMethodSubtype) {
    val locale: Locale = rawSubtype.locale()

    // The subtype is considered RTL if the language of the main subtype is RTL.
    val isRtlSubtype: Boolean = ScriptUtils.isScriptRtl(locale.script())

    fun getExtraValueOf(key: String): String? = rawSubtype.getExtraValueOf(key)

    fun hasExtraValue(key: String): Boolean = rawSubtype.containsExtraValueKey(key)

    val isNoLanguage: Boolean get() = SubtypeLocaleUtils.NO_LANGUAGE == locale.language

    val mainLayoutName: String get() = layouts[LayoutType.MAIN] ?: "qwerty"

    /** layout names for this subtype by LayoutType */
    val layouts = LayoutType.getLayoutMap(getExtraValueOf(KEYBOARD_LAYOUT_SET) ?: "")

    val isCustom: Boolean get() = LayoutUtilsCustom.isCustomLayout(mainLayoutName)

    val fullDisplayName: String get() = SubtypeLocaleUtils.getSubtypeLocaleDisplayName(locale)

    val middleDisplayName: String get() = SubtypeLocaleUtils.getSubtypeLanguageDisplayName(locale)

    override fun equals(other: Any?): Boolean {
        if (other !is RichInputMethodSubtype) return false
        return rawSubtype == other.rawSubtype && locale == other.locale
    }

    override fun hashCode(): Int {
        return rawSubtype.hashCode() + locale.hashCode()
    }

    override fun toString(): String = rawSubtype.extraValue

    companion object {
        private val TAG: String = RichInputMethodSubtype::class.java.simpleName

        fun get(subtype: InputMethodSubtype?): RichInputMethodSubtype =
            if (subtype == null) noLanguageSubtype
            else RichInputMethodSubtype(subtype)

        // Dummy no language QWERTY subtype. See method_dummy.xml}.
        private const val EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE = ("KeyboardLayoutSet=" + SubtypeLocaleUtils.QWERTY
                + "," + Constants.Subtype.ExtraValue.ASCII_CAPABLE
                + "," + Constants.Subtype.ExtraValue.ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE
                + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
        private val DUMMY_NO_LANGUAGE_SUBTYPE = RichInputMethodSubtype(
            InputMethodSubtypeBuilder()
                .setSubtypeNameResId(R.string.subtype_no_language)
                .setSubtypeIconResId(R.drawable.ic_ime_switcher)
                .setSubtypeLocale(SubtypeLocaleUtils.NO_LANGUAGE)
                .setSubtypeMode(Constants.Subtype.KEYBOARD_MODE)
                .setSubtypeExtraValue(EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE)
                .setIsAuxiliary(false)
                .setOverridesImplicitlyEnabledSubtype(false)
                .setSubtypeId(0x7000000f)
                .setIsAsciiCapable(true)
                .build()
        )

        // Caveat: We probably should remove this when we add an Emoji subtype in {@link R.xml.method}.
        // Dummy Emoji subtype. See {@link R.xml.method}.
        private const val SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE = -0x2874d130
        private const val EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE = ("KeyboardLayoutSet=" + SubtypeLocaleUtils.EMOJI
                + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
        val emojiSubtype: RichInputMethodSubtype = RichInputMethodSubtype(
            InputMethodSubtypeBuilder()
                .setSubtypeNameResId(R.string.subtype_emoji)
                .setSubtypeIconResId(R.drawable.ic_ime_switcher)
                .setSubtypeLocale(SubtypeLocaleUtils.NO_LANGUAGE)
                .setSubtypeMode(Constants.Subtype.KEYBOARD_MODE)
                .setSubtypeExtraValue(EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE)
                .setIsAuxiliary(false)
                .setOverridesImplicitlyEnabledSubtype(false)
                .setSubtypeId(SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE)
                .build()
        )
        private var sNoLanguageSubtype: RichInputMethodSubtype? = null

        val noLanguageSubtype: RichInputMethodSubtype get() {
            sNoLanguageSubtype?.let { return it }
            var noLanguageSubtype = sNoLanguageSubtype
            val rawNoLanguageSubtype = SubtypeSettings.getResourceSubtypesForLocale(SubtypeLocaleUtils.NO_LANGUAGE.constructLocale())
                .firstOrNull { it.mainLayoutNameOrQwerty() == SubtypeLocaleUtils.QWERTY }
            if (rawNoLanguageSubtype != null) {
                noLanguageSubtype = RichInputMethodSubtype(rawNoLanguageSubtype)
            }
            if (noLanguageSubtype != null) {
                sNoLanguageSubtype = noLanguageSubtype
                return noLanguageSubtype
            }
            Log.w(TAG, "Can't find any language with QWERTY subtype")
            Log.w(TAG, "No input method subtype found; returning dummy subtype: $DUMMY_NO_LANGUAGE_SUBTYPE")
            return DUMMY_NO_LANGUAGE_SUBTYPE
        }
    }
}
