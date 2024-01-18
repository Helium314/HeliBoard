/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.inputmethod.InputMethodSubtype
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils
import org.dslul.openboard.inputmethod.latin.utils.locale
import java.util.*

object InputMethodSubtypeCompatUtils {
    @JvmStatic
    fun getLocaleObject(subtype: InputMethodSubtype): Locale { // Locale.forLanguageTag() is available only in Android L and later.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.N) {
            val languageTag = subtype.languageTag
            if (languageTag.isNotEmpty())
                return Locale.forLanguageTag(languageTag)
        }
        return LocaleUtils.constructLocaleFromString(subtype.locale())
    }
}