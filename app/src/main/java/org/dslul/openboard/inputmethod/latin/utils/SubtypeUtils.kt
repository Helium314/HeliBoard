package org.dslul.openboard.inputmethod.latin.utils

import android.os.Build
import android.view.inputmethod.InputMethodSubtype
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils.constructLocale
import java.util.Locale

fun InputMethodSubtype.locale(): Locale {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        if (languageTag.isNotEmpty())
            return languageTag.constructLocale()
    }
    @Suppress("deprecation") return locale.constructLocale()
}
