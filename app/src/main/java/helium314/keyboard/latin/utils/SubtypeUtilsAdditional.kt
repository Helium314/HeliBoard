package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.SettingsSubtype.Companion.toSettingsSubtype
import java.util.Locale

object SubtypeUtilsAdditional {

    fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
        return subtype.containsExtraValueKey(ExtraValue.IS_ADDITIONAL_SUBTYPE)
    }

    // todo: extra value does not contain UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME for custom layout
    //  it did contain that key in 2.3, but where was it set? anyway, need to be careful with separators if we want to use it
    //  see also todo in SettingsSubtype
    // todo: the name always contains the layout, but we may just use the original one
    fun createAdditionalSubtype(locale: Locale, extraValue: String, isAsciiCapable: Boolean,
                                        isEmojiCapable: Boolean): InputMethodSubtype {
        val mainLayoutName = LayoutType.getMainLayoutFromExtraValue(extraValue) ?: "qwerty"
        val nameId = getNameResId(locale, mainLayoutName)
        val fullExtraValue = extraValue + "," + getAdditionalExtraValues(locale, mainLayoutName, isAsciiCapable, isEmojiCapable)
        val subtypeId = getSubtypeId(locale, fullExtraValue, isAsciiCapable)
        val builder = InputMethodSubtypeBuilder()
            .setSubtypeNameResId(nameId)
            .setSubtypeIconResId(R.drawable.ic_ime_switcher)
            .setSubtypeLocale(locale.toString())
            .setSubtypeMode(Constants.Subtype.KEYBOARD_MODE)
            .setSubtypeExtraValue(fullExtraValue)
            .setIsAuxiliary(false)
            .setOverridesImplicitlyEnabledSubtype(false)
            .setSubtypeId(subtypeId)
            .setIsAsciiCapable(isAsciiCapable)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N)
            builder.setLanguageTag(locale.toLanguageTag())
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE && LayoutUtilsCustom.isCustomLayout(mainLayoutName))
            builder.setSubtypeNameOverride(LayoutUtilsCustom.getDisplayName(mainLayoutName))
        return builder.build()
    }

    fun createDummyAdditionalSubtype(locale: Locale, mainLayoutName: String) =
        createAdditionalSubtype(locale, "${ExtraValue.KEYBOARD_LAYOUT_SET}=MAIN${Separators.KV}$mainLayoutName", false, false)

    fun createEmojiCapableAdditionalSubtype(locale: Locale, mainLayoutName: String, asciiCapable: Boolean) =
        createAdditionalSubtype(locale, "${ExtraValue.KEYBOARD_LAYOUT_SET}=MAIN${Separators.KV}$mainLayoutName", asciiCapable, true)

    // todo: consider using SettingsSubtype
    fun addAdditionalSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
        val oldAdditionalSubtypesString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val additionalSubtypes = createAdditionalSubtypes(oldAdditionalSubtypesString).toMutableSet()
        additionalSubtypes.add(subtype)
        val newAdditionalSubtypesString = createPrefSubtypes(additionalSubtypes)
        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
    }

    fun removeAdditionalSubtype(prefs: SharedPreferences, subtype: InputMethodSubtype) {
        val oldAdditionalSubtypesString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val oldAdditionalSubtypes = createAdditionalSubtypes(oldAdditionalSubtypesString)
        val newAdditionalSubtypes = oldAdditionalSubtypes.filter { it != subtype }
        val newAdditionalSubtypesString = createPrefSubtypes(newAdditionalSubtypes)
        Settings.writePrefAdditionalSubtypes(prefs, newAdditionalSubtypesString)
    }

    // updates additional subtypes, enabled subtypes, and selected subtype
    fun changeAdditionalSubtype(from: SettingsSubtype, to: SettingsSubtype, context: Context) {
        val prefs = context.prefs()
        // read now because there may be an intermediate state where the subtype is invalid and thus removed
        val isSelected = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, Defaults.PREF_SELECTED_SUBTYPE)!!.toSettingsSubtype() == from
        val isEnabled = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!.split(Separators.SETS)
            .any { it.toSettingsSubtype() == from }
        val new = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
            .split(Separators.SETS).mapNotNullTo(sortedSetOf()) {
                if (it == from.toPref()) null else it
            } + to.toPref()
        prefs.edit().putString(Settings.PREF_ADDITIONAL_SUBTYPES, new.joinToString(Separators.SETS)).apply()

        val fromSubtype = from.toAdditionalSubtype() // will be null if we edit a resource subtype
        val toSubtype = to.toAdditionalSubtype() // should never be null
        if (isSelected && toSubtype != null) {
            SubtypeSettings.setSelectedSubtype(prefs, toSubtype)
        }
        if (fromSubtype != null && isEnabled && toSubtype != null) {
            SubtypeSettings.removeEnabledSubtype(context, fromSubtype)
            SubtypeSettings.addEnabledSubtype(prefs, toSubtype)
        }
    }

    fun createAdditionalSubtypes(prefSubtypes: String): List<InputMethodSubtype> {
        if (prefSubtypes.isEmpty())
            return emptyList()
        return prefSubtypes.split(Separators.SETS).mapNotNull { it.toSettingsSubtype().toAdditionalSubtype() }
    }

    fun createPrefSubtypes(subtypes: Collection<InputMethodSubtype>): String {
        if (subtypes.isEmpty())
            return ""
        return subtypes.joinToString(Separators.SETS) { it.toSettingsSubtype().toPref() }
    }

    private fun getNameResId(locale: Locale, mainLayoutName: String): Int {
        val nameId = SubtypeLocaleUtils.getSubtypeNameResId(locale, mainLayoutName)
        if (nameId != SubtypeLocaleUtils.UNKNOWN_KEYBOARD_LAYOUT) return nameId
        SubtypeSettings.getResourceSubtypesForLocale(locale).forEach {
            if (it.mainLayoutName() == mainLayoutName) return it.nameResId
        }
        return SubtypeLocaleUtils.UNKNOWN_KEYBOARD_LAYOUT
    }

    /**
     * Returns the subtype ID that is supposed to be compatible between different version of OSes.
     *
     * From the compatibility point of view, it is important to keep subtype id predictable and
     * stable between different OSes. For this purpose, the calculation code in this method is
     * carefully chosen and then fixed. Treat the following code as no more or less than a
     * hash function. Each component to be hashed can be different from the corresponding value
     * that is used to instantiate [InputMethodSubtype] actually.
     * For example, you don't need to update `compatibilityExtraValueItems` in this
     * method even when we need to add some new extra values for the actual instance of
     * [InputMethodSubtype].
     */
    private fun getSubtypeId(locale: Locale, extraValue: String, asciiCapable: Boolean): Int {
        // basically we use the hashCode as specified for id in https://developer.android.com/reference/android/view/inputmethod/InputMethodSubtype
        return arrayOf(
            locale,
            Constants.Subtype.KEYBOARD_MODE,
            extraValue,
            false, // isAuxiliary
            false, // overrideImplicitlyEnabledSubtype
            asciiCapable // asciiCapable
        ).contentHashCode()
    }

    /**
     * Returns the extra value that is optimized for the running OS.
     *
     * Historically the extra value has been used as the last resort to annotate various kinds of
     * attributes. Some of these attributes are valid only on some platform versions. Thus we cannot
     * assume that the extra values stored in a persistent storage are always valid. We need to
     * regenerate the extra value on the fly instead.
     *
     * @param mainLayoutName the keyboard main layout name (e.g., "dvorak").
     * @param isAsciiCapable true when ASCII characters are supported with this layout.
     * @param isEmojiCapable true when Unicode Emoji characters are supported with this layout.
     * @return extra value that is optimized for the running OS.
     * @see .getPlatformVersionIndependentSubtypeId
     */
    private fun getAdditionalExtraValues(locale: Locale, mainLayoutName: String, isAsciiCapable: Boolean, isEmojiCapable: Boolean): String {
        val extraValueItems = mutableListOf<String>()
        if (isAsciiCapable)
            extraValueItems.add(ExtraValue.ASCII_CAPABLE)
        if (SubtypeLocaleUtils.isExceptionalLocale(locale)) {
            extraValueItems.add(
                ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" + SubtypeLocaleUtils.getMainLayoutDisplayName(mainLayoutName)
            )
        }
        if (isEmojiCapable)
            extraValueItems.add(ExtraValue.EMOJI_CAPABLE)
        extraValueItems.add(ExtraValue.IS_ADDITIONAL_SUBTYPE)
        return extraValueItems.joinToString(",")
    }
}
