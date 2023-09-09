package org.dslul.openboard.inputmethod.compat

import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.inputmethod.InputMethodSubtype
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.settings.locale
import java.util.*

object InputMethodSubtypeCompatUtils {
    // Note that InputMethodSubtype.getLanguageTag() is expected to be available in Android N+.
    private val GET_LANGUAGE_TAG = CompatUtils.getMethod(InputMethodSubtype::class.java, "getLanguageTag")

    @JvmStatic
    fun getLocaleObject(subtype: InputMethodSubtype): Locale { // Locale.forLanguageTag() is available only in Android L and later.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.LOLLIPOP) {
            val languageTag = CompatUtils.invoke(subtype, null, GET_LANGUAGE_TAG) as String?
            if (!languageTag.isNullOrEmpty()) {
                return Locale.forLanguageTag(languageTag)
            }
        }
        return LocaleUtils.constructLocaleFromString(subtype.locale())
    }

}