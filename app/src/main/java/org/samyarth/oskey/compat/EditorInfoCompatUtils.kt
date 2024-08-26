/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.compat

import android.os.Build
import android.view.inputmethod.EditorInfo
import java.util.*
import kotlin.collections.ArrayList

object EditorInfoCompatUtils {

    @JvmStatic
    fun imeActionName(imeOptions: Int): String {
        return when (val actionId = imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_UNSPECIFIED -> "actionUnspecified"
            EditorInfo.IME_ACTION_NONE -> "actionNone"
            EditorInfo.IME_ACTION_GO -> "actionGo"
            EditorInfo.IME_ACTION_SEARCH -> "actionSearch"
            EditorInfo.IME_ACTION_SEND -> "actionSend"
            EditorInfo.IME_ACTION_NEXT -> "actionNext"
            EditorInfo.IME_ACTION_DONE -> "actionDone"
            EditorInfo.IME_ACTION_PREVIOUS -> "actionPrevious"
            else -> "actionUnknown($actionId)"
        }
    }

    @JvmStatic
    fun getHintLocales(editorInfo: EditorInfo?): List<Locale>? {
        if (editorInfo == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            return null
        }
        val localeList = editorInfo.hintLocales ?: return null
        val locales = ArrayList<Locale>(localeList.size())
        for (i in 0 until localeList.size()) {
            locales.add(localeList.get(i))
        }
        return locales
    }
}