package helium314.keyboard.latin.utils

import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.StringUtils
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutUtilsCustom.isCustomLayout
import helium314.keyboard.latin.utils.ScriptUtils.script
import java.util.Locale

object SubtypeUtilsAdditional {
    fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
        return subtype.containsExtraValueKey(ExtraValue.IS_ADDITIONAL_SUBTYPE)
    }

    private fun createAdditionalSubtypeInternal(locale: Locale, keyboardLayoutSetName: String,
        isAsciiCapable: Boolean, isEmojiCapable: Boolean
    ): InputMethodSubtype {
        val nameId = SubtypeLocaleUtils.getSubtypeNameResId(locale, keyboardLayoutSetName)
        val platformVersionDependentExtraValues =
            getPlatformVersionDependentExtraValue(locale, keyboardLayoutSetName, isAsciiCapable, isEmojiCapable)
        val platformVersionIndependentSubtypeId =
            getPlatformVersionIndependentSubtypeId(locale, keyboardLayoutSetName)
        val builder = InputMethodSubtypeBuilder()
            .setSubtypeNameResId(nameId)
            .setSubtypeIconResId(R.drawable.ic_ime_switcher)
            .setSubtypeLocale(locale.toString())
            .setSubtypeMode(Constants.Subtype.KEYBOARD_MODE)
            .setSubtypeExtraValue(platformVersionDependentExtraValues)
            .setIsAuxiliary(false)
            .setOverridesImplicitlyEnabledSubtype(false)
            .setSubtypeId(platformVersionIndependentSubtypeId)
            .setIsAsciiCapable(isAsciiCapable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            builder.setLanguageTag(locale.toLanguageTag())
        return builder.build()
    }

    fun createDummyAdditionalSubtype(locale: Locale, keyboardLayoutSetName: String) =
        createAdditionalSubtypeInternal(locale, keyboardLayoutSetName, false, false)

    fun createEmojiCapableAdditionalSubtype(locale: Locale, keyboardLayoutSetName: String, asciiCapable: Boolean) =
        createAdditionalSubtypeInternal(locale, keyboardLayoutSetName, asciiCapable, true)

    fun addAdditionalSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
        val oldAdditionalSubtypesString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val additionalSubtypes = createAdditionalSubtypes(oldAdditionalSubtypesString).toMutableSet()
        additionalSubtypes.add(subtype)
        val newAdditionalSubtypesString = createPrefSubtypes(additionalSubtypes.toTypedArray())
        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
    }

    fun removeAdditionalSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
        val oldAdditionalSubtypesString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val oldAdditionalSubtypes = createAdditionalSubtypes(oldAdditionalSubtypesString)
        val newAdditionalSubtypes = oldAdditionalSubtypes.filter { it != subtype }
        val newAdditionalSubtypesString = createPrefSubtypes(newAdditionalSubtypes.toTypedArray())
        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
    }

    // todo: adjust so we can store more stuff in extra values
    private fun getPrefSubtype(subtype: InputMethodSubtype): String {
        val mainLayoutName = SubtypeLocaleUtils.getMainLayoutName(subtype)
        val layoutExtraValue = ExtraValue.KEYBOARD_LAYOUT_SET + "=MAIN" + Separators.KV + mainLayoutName
        val extraValue = StringUtils.removeFromCommaSplittableTextIfExists(
            layoutExtraValue, StringUtils.removeFromCommaSplittableTextIfExists(
                ExtraValue.IS_ADDITIONAL_SUBTYPE, subtype.extraValue
            )
        )
        require(!extraValue.contains(Separators.SETS) && !extraValue.contains(Separators.SET))
            { "extra value contains not allowed characters $extraValue" }
        val basePrefSubtype = subtype.locale().toLanguageTag() + Separators.SET + mainLayoutName
        return if (extraValue.isEmpty()) basePrefSubtype
        else basePrefSubtype + Separators.SET + extraValue
    }

    fun createAdditionalSubtypes(prefSubtypes: String): List<InputMethodSubtype> {
        if (TextUtils.isEmpty(prefSubtypes)) {
            return emptyList()
        }
        return prefSubtypes.split(Separators.SETS)
            .mapNotNull { createSubtypeFromString(it) }
    }

    // use string created with getPrefSubtype
    fun createSubtypeFromString(prefSubtype: String): InputMethodSubtype? {
        val elems = prefSubtype.split(Separators.SET)
        if (elems.size != LENGTH_WITHOUT_EXTRA_VALUE && elems.size != LENGTH_WITH_EXTRA_VALUE) {
            Log.w(TAG, "Unknown additional subtype specified: $prefSubtype")
            return null
        }
        val languageTag = elems[INDEX_OF_LANGUAGE_TAG]
        val locale = languageTag.constructLocale()
        val keyboardLayoutSet = elems[INDEX_OF_KEYBOARD_LAYOUT]
        val asciiCapable = locale.script() == ScriptUtils.SCRIPT_LATIN
        // Here we assume that all the additional subtypes are EmojiCapable
        val subtype = createEmojiCapableAdditionalSubtype(locale, keyboardLayoutSet, asciiCapable)
        if (subtype.nameResId == SubtypeLocaleUtils.UNKNOWN_KEYBOARD_LAYOUT && !isCustomLayout(keyboardLayoutSet)) {
            // Skip unknown keyboard layout subtype. This may happen when predefined keyboard
            // layout has been removed.
            return null
        }
        return subtype
    }

    fun createPrefSubtypes(subtypes: Array<InputMethodSubtype>?): String {
        if (subtypes.isNullOrEmpty()) {
            return ""
        }
        val sb = StringBuilder()
        for (subtype in subtypes) {
            if (sb.isNotEmpty()) {
                sb.append(Separators.SETS)
            }
            sb.append(getPrefSubtype(subtype))
        }
        return sb.toString()
    }

    fun createPrefSubtypes(prefSubtypes: Array<String>?): String {
        if (prefSubtypes.isNullOrEmpty()) {
            return ""
        }
        val sb = StringBuilder()
        for (prefSubtype in prefSubtypes) {
            if (sb.isNotEmpty()) {
                sb.append(Separators.SETS)
            }
            sb.append(prefSubtype)
        }
        return sb.toString()
    }

    /**
     * Returns the extra value that is optimized for the running OS.
     *
     *
     * Historically the extra value has been used as the last resort to annotate various kinds of
     * attributes. Some of these attributes are valid only on some platform versions. Thus we cannot
     * assume that the extra values stored in a persistent storage are always valid. We need to
     * regenerate the extra value on the fly instead.
     *
     * @param keyboardLayoutSetName the keyboard layout set name (e.g., "dvorak").
     * @param isAsciiCapable true when ASCII characters are supported with this layout.
     * @param isEmojiCapable true when Unicode Emoji characters are supported with this layout.
     * @return extra value that is optimized for the running OS.
     * @see .getPlatformVersionIndependentSubtypeId
     */
    private fun getPlatformVersionDependentExtraValue(locale: Locale,
        keyboardLayoutSetName: String, isAsciiCapable: Boolean, isEmojiCapable: Boolean
    ): String {
        val extraValueItems = mutableListOf<String>()
        extraValueItems.add(ExtraValue.KEYBOARD_LAYOUT_SET + "=MAIN:" + keyboardLayoutSetName)
        if (isAsciiCapable) {
            extraValueItems.add(ExtraValue.ASCII_CAPABLE)
        }
        if (SubtypeLocaleUtils.isExceptionalLocale(locale)) {
            extraValueItems.add(
                ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" +
                        SubtypeLocaleUtils.getMainLayoutDisplayName(keyboardLayoutSetName)
            )
        }
        if (isEmojiCapable) {
            extraValueItems.add(ExtraValue.EMOJI_CAPABLE)
        }
        extraValueItems.add(ExtraValue.IS_ADDITIONAL_SUBTYPE)
        return extraValueItems.joinToString(",")
    }

    /**
     * Returns the subtype ID that is supposed to be compatible between different version of OSes.
     *
     *
     * From the compatibility point of view, it is important to keep subtype id predictable and
     * stable between different OSes. For this purpose, the calculation code in this method is
     * carefully chosen and then fixed. Treat the following code as no more or less than a
     * hash function. Each component to be hashed can be different from the corresponding value
     * that is used to instantiate [InputMethodSubtype] actually.
     * For example, you don't need to update `compatibilityExtraValueItems` in this
     * method even when we need to add some new extra values for the actual instance of
     * [InputMethodSubtype].
     *
     * @param keyboardLayoutSetName the keyboard layout set name (e.g., "dvorak").
     * @return a platform-version independent subtype ID.
     * @see .getPlatformVersionDependentExtraValue
     */
    private fun getPlatformVersionIndependentSubtypeId(locale: Locale, keyboardLayoutSetName: String): Int {
        // For compatibility reasons, we concatenate the extra values in the following order.
        // - KeyboardLayoutSet
        // - AsciiCapable
        // - UntranslatableReplacementStringInSubtypeName
        // - EmojiCapable
        // - isAdditionalSubtype
        val compatibilityExtraValueItems = mutableListOf<String>()
        compatibilityExtraValueItems.add(ExtraValue.KEYBOARD_LAYOUT_SET + "=MAIN:" + keyboardLayoutSetName)
        compatibilityExtraValueItems.add(ExtraValue.ASCII_CAPABLE)
        if (SubtypeLocaleUtils.isExceptionalLocale(locale)) {
            compatibilityExtraValueItems.add(
                ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" +
                        SubtypeLocaleUtils.getMainLayoutDisplayName(keyboardLayoutSetName)
            )
        }
        compatibilityExtraValueItems.add(ExtraValue.EMOJI_CAPABLE)
        compatibilityExtraValueItems.add(ExtraValue.IS_ADDITIONAL_SUBTYPE)
        val compatibilityExtraValues = compatibilityExtraValueItems.joinToString(",")
        return arrayOf(
            locale,
            Constants.Subtype.KEYBOARD_MODE,
            compatibilityExtraValues,
            false, // isAuxiliary
            false // overrideImplicitlyEnabledSubtype
        ).contentHashCode()
    }

    private val TAG: String = SubtypeUtilsAdditional::class.java.simpleName
    private const val INDEX_OF_LANGUAGE_TAG: Int = 0
    private const val INDEX_OF_KEYBOARD_LAYOUT: Int = 1
    private const val INDEX_OF_EXTRA_VALUE: Int = 2
    private const val LENGTH_WITHOUT_EXTRA_VALUE: Int = (INDEX_OF_KEYBOARD_LAYOUT + 1)
    private const val LENGTH_WITH_EXTRA_VALUE: Int = (INDEX_OF_EXTRA_VALUE + 1)
}
