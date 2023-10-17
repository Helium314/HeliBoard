/*
 * Copyright (C) 2016 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import java.util.*

object LocaleListCompatUtils {
    private val CLASS_LocaleList = CompatUtils.getClass("android.os.LocaleList")
    private val METHOD_get = CompatUtils.getMethod(CLASS_LocaleList, "get", Int::class.javaPrimitiveType)
    private val METHOD_isEmpty = CompatUtils.getMethod(CLASS_LocaleList, "isEmpty")
    fun isEmpty(localeList: Any?): Boolean {
        return CompatUtils.invoke(localeList, java.lang.Boolean.FALSE, METHOD_isEmpty) as Boolean
    }

    operator fun get(localeList: Any?, index: Int): Locale? {
        return CompatUtils.invoke(localeList, null, METHOD_get, index) as Locale
    }
}