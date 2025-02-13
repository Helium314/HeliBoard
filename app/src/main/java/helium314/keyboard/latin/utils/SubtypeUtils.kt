package helium314.keyboard.latin.utils

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET
import helium314.keyboard.latin.common.LocaleUtils
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import java.util.Locale

fun InputMethodSubtype.locale(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (languageTag.isNotEmpty())
            return languageTag.constructLocale()
    }
    @Suppress("deprecation") return locale.constructLocale()
}

fun InputMethodSubtype.mainLayoutName(): String {
    val map = LayoutType.getLayoutMap(getExtraValueOf(KEYBOARD_LAYOUT_SET) ?: "")
    return map[LayoutType.MAIN] ?: "qwerty"
}

fun InputMethodSubtype.explicitMainLayoutName(): String? {
    val map = LayoutType.getLayoutMap(getExtraValueOf(KEYBOARD_LAYOUT_SET) ?: "")
    return map[LayoutType.MAIN]
}

/** Workaround for SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale ignoring custom layout names */
// todo (later): this should be done properly and in SubtypeLocaleUtils
fun InputMethodSubtype.displayName(context: Context): CharSequence {
    val layoutName = SubtypeLocaleUtils.getKeyboardLayoutSetName(this)
    if (layoutName.startsWith(CUSTOM_LAYOUT_PREFIX))
        return "${LocaleUtils.getLocaleDisplayNameInSystemLocale(locale(), context)} (${getLayoutDisplayName(layoutName)})"
    return SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(this)
}
