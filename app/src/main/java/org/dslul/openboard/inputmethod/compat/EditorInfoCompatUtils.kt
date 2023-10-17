/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.view.inputmethod.EditorInfo
import java.util.*

object EditorInfoCompatUtils {
    // Note that EditorInfo.IME_FLAG_FORCE_ASCII has been introduced
    // in API level 16 (Build.VERSION_CODES.JELLY_BEAN).
    private val FIELD_IME_FLAG_FORCE_ASCII = CompatUtils.getField(EditorInfo::class.java, "IME_FLAG_FORCE_ASCII")
    private val OBJ_IME_FLAG_FORCE_ASCII: Int? = CompatUtils.getFieldValue(null, null, FIELD_IME_FLAG_FORCE_ASCII) as? Int
    private val FIELD_HINT_LOCALES = CompatUtils.getField(EditorInfo::class.java, "hintLocales")

    @JvmStatic
    fun hasFlagForceAscii(imeOptions: Int): Boolean {
        return if (OBJ_IME_FLAG_FORCE_ASCII == null) false else imeOptions and OBJ_IME_FLAG_FORCE_ASCII != 0
    }

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

    fun imeOptionsName(imeOptions: Int): String {
        val action = imeActionName(imeOptions)
        val flags = StringBuilder()
        if (imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) {
            flags.append("flagNoEnterAction|")
        }
        if (imeOptions and EditorInfo.IME_FLAG_NAVIGATE_NEXT != 0) {
            flags.append("flagNavigateNext|")
        }
        if (imeOptions and EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS != 0) {
            flags.append("flagNavigatePrevious|")
        }
        if (hasFlagForceAscii(imeOptions)) {
            flags.append("flagForceAscii|")
        }
        return flags.toString() + action
    }

    @JvmStatic
    fun getPrimaryHintLocale(editorInfo: EditorInfo?): Locale? {
        if (editorInfo == null) {
            return null
        }
        val localeList = CompatUtils.getFieldValue(editorInfo, null, FIELD_HINT_LOCALES) ?: return null
        return if (LocaleListCompatUtils.isEmpty(localeList)) {
            null
        } else LocaleListCompatUtils[localeList, 0]
    }
}