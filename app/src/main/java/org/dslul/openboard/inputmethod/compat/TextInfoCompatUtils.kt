/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.view.textservice.TextInfo
import org.dslul.openboard.inputmethod.annotations.UsedForTesting

object TextInfoCompatUtils {
    // Note that TextInfo.getCharSequence() is supposed to be available in API level 21 and later.
    private val TEXT_INFO_GET_CHAR_SEQUENCE = CompatUtils.getMethod(TextInfo::class.java, "getCharSequence")

    @get:UsedForTesting
    val isCharSequenceSupported: Boolean
        get() = TEXT_INFO_GET_CHAR_SEQUENCE != null

    @JvmStatic
    @UsedForTesting
    fun newInstance(charSequence: CharSequence, start: Int, end: Int, cookie: Int,
                    sequenceNumber: Int): TextInfo {
        return TextInfo(charSequence, start, end, cookie, sequenceNumber)
    }

    /**
     * Returns the result of [TextInfo.getCharSequence] when available. Otherwise returns
     * the result of [TextInfo.getText] as fall back.
     * @param textInfo the instance for which [TextInfo.getCharSequence] or
     * [TextInfo.getText] is called.
     * @return the result of [TextInfo.getCharSequence] when available. Otherwise returns
     * the result of [TextInfo.getText] as fall back. If `textInfo` is `null`,
     * returns `null`.
     */
    @JvmStatic
    @UsedForTesting
    fun getCharSequenceOrString(textInfo: TextInfo): CharSequence {
        return textInfo.charSequence
    }
}