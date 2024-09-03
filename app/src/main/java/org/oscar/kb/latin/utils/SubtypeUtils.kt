package org.oscar.kb.latin.utils

import android.content.Context
import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import org.oscar.kb.latin.utils.SubtypeLocaleUtils
import org.oscar.kb.latin.common.LocaleUtils
import org.oscar.kb.latin.common.LocaleUtils.constructLocale
import java.util.Locale

fun InputMethodSubtype.locale(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (languageTag.isNotEmpty())
            return languageTag.constructLocale()
    }
    @Suppress("deprecation") return locale.constructLocale()
}

/** Workaround for SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale ignoring custom layout names */
// todo (later): this should be done properly and in SubtypeLocaleUtils
fun InputMethodSubtype.displayName(context: Context): CharSequence {
    val layoutName = SubtypeLocaleUtils.getKeyboardLayoutSetName(this)
    if (layoutName.startsWith(CUSTOM_LAYOUT_PREFIX))
        return "${LocaleUtils.getLocaleDisplayNameInSystemLocale(locale(), context)} (${getLayoutDisplayName(layoutName)})"
    return SubtypeLocaleUtils.getSubtypeDisplayNameInSystemLocale(this)
}
