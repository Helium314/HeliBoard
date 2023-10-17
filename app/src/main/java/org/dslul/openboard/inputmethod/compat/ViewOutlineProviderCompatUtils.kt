/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.inputmethodservice.InputMethodService
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.View

object ViewOutlineProviderCompatUtils {
    private val EMPTY_INSETS_UPDATER: InsetsUpdater = object : InsetsUpdater {
        override fun setInsets(insets: InputMethodService.Insets) {}
    }

    @JvmStatic
    fun setInsetsOutlineProvider(view: View): InsetsUpdater {
        return if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
            EMPTY_INSETS_UPDATER
        } else ViewOutlineProviderCompatUtilsLXX.setInsetsOutlineProvider(view)
    }

    interface InsetsUpdater {
        fun setInsets(insets: InputMethodService.Insets)
    }
}