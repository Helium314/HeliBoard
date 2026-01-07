package helium314.keyboard.latin.utils

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import android.view.inputmethod.InputMethodSubtype.InputMethodSubtypeBuilder
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.Constants.Separators
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Defaults.default
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.LayoutType.Companion.toExtraValue
import helium314.keyboard.latin.utils.ScriptUtils.script
import java.util.Locale
import androidx.core.content.edit

object SubtypeUtilsAdditional {

    fun isAdditionalSubtype(subtype: InputMethodSubtype): Boolean {
        return subtype.containsExtraValueKey(ExtraValue.IS_ADDITIONAL_SUBTYPE)
    }

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

    // only used in tests
    fun createEmojiCapableAdditionalSubtype(locale: Locale, mainLayoutName: String, asciiCapable: Boolean) =
        createAdditionalSubtype(locale, "${ExtraValue.KEYBOARD_LAYOUT_SET}=MAIN${Separators.KV}$mainLayoutName", asciiCapable, true)

    /** creates a subtype with every layout being the default for its type */
    fun createDefaultSubtype(locale: Locale): InputMethodSubtype {
        val layouts = LayoutType.entries.associateWithTo(LayoutType.getLayoutMap(null)) { it.default }
        SubtypeSettings.getResourceSubtypesForLocale(locale).firstOrNull()?.mainLayoutName()?.let { layouts[LayoutType.MAIN] = it }
        val extra = ExtraValue.KEYBOARD_LAYOUT_SET + "=" + layouts.toExtraValue()
        return createAdditionalSubtype(locale, extra, locale.script() == ScriptUtils.SCRIPT_LATIN, true)
    }

    fun removeAdditionalSubtype(context: Context, subtype: InputMethodSubtype) {
        val prefs = context.prefs()
        SubtypeSettings.removeEnabledSubtype(context, subtype)
        val oldAdditionalSubtypesString = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!
        val oldAdditionalSubtypes = SubtypeSettings.createSettingsSubtypes(oldAdditionalSubtypesString)
        val settingsSubtype = subtype.toSettingsSubtype()
        val newAdditionalSubtypes = oldAdditionalSubtypes.filter { it != settingsSubtype }
        val newAdditionalSubtypesString = SubtypeSettings.createPrefSubtypes(newAdditionalSubtypes)
        prefs.edit { putString(Settings.PREF_ADDITIONAL_SUBTYPES, newAdditionalSubtypesString) }
    }

    // updates additional subtypes, enabled subtypes, and selected subtype
    @SuppressLint("UseKtx") // easier to understand
    fun changeAdditionalSubtype(from: SettingsSubtype, to: SettingsSubtype, context: Context) {
        val prefs = context.prefs()
        // read now because there may be an intermediate state where the subtype is invalid and thus removed
        val isSelected = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, Defaults.PREF_SELECTED_SUBTYPE)!!.toSettingsSubtype() == from
        val isEnabled = prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!.split(Separators.SETS)
            .any { it.toSettingsSubtype() == from }
        val additionalSubtypes = SubtypeSettings.createSettingsSubtypes(prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!)
            .toMutableList()
        additionalSubtypes.remove(from)
        if (!to.isSameAsDefault()) {
            // We only add the "to" subtype if it's not equal to a resource subtype.
            // This means we make additional subtype disappear as magically as it was added if all settings are default.
            // If we don't do this, enabling the base subtype will result in the additional subtype being enabled,
            // as both have the same settingsSubtype.
            additionalSubtypes.add(to)
        }
        val editor = prefs.edit()
        editor.putString(Settings.PREF_ADDITIONAL_SUBTYPES, SubtypeSettings.createPrefSubtypes(additionalSubtypes))
        if (isSelected) {
            editor.putString(Settings.PREF_SELECTED_SUBTYPE, to.toPref())
        }
        if (isEnabled) {
            val enabled = SubtypeSettings.createSettingsSubtypes(prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!)
                .toMutableList()
            enabled.remove(from)
            enabled.add(to)
            editor.putString(Settings.PREF_ENABLED_SUBTYPES, SubtypeSettings.createPrefSubtypes(enabled))
        }
        prefs.all.filterKeys { it.startsWith(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX) }
            .filterValues { it.toString().toSettingsSubtype() == from }.forEach { editor.putString(it.key, to.toPref()) }
        editor.apply()
        SubtypeSettings.reloadEnabledSubtypes(context)
    }

    fun createAdditionalSubtypes(prefSubtypes: String): List<InputMethodSubtype> =
        prefSubtypes.split(Separators.SETS).mapNotNull {
            if (it.isEmpty()) null
            else it.toSettingsSubtype().toAdditionalSubtype()
        }

    private fun getNameResId(locale: Locale, mainLayoutName: String): Int {
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
/*        if (SubtypeLocaleUtils.isExceptionalLocale(locale)) {
            // this seems to be for shorter names (e.g. English (US) instead English (United States))
            // but is now also used for languages that are not known by Android (at least older versions)
            // todo: actually this should never contain a custom layout name, because it may contain any
            //  characters including , and = which may break extra values
            // todo: disabled for now, not necessary with the more generic subtype name
            //  might become necessary again when exposing subtypes to the system
            extraValueItems.add(
                ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME + "=" + SubtypeLocaleUtils.getMainLayoutDisplayName(mainLayoutName)
            )
        }*/
        if (isEmojiCapable)
            extraValueItems.add(ExtraValue.EMOJI_CAPABLE)
        extraValueItems.add(ExtraValue.IS_ADDITIONAL_SUBTYPE)
        return extraValueItems.joinToString(",")
    }
}
