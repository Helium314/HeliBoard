/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat

import android.graphics.Outline
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.ViewOutlineProvider
import org.dslul.openboard.inputmethod.compat.ViewOutlineProviderCompatUtils.InsetsUpdater

internal object ViewOutlineProviderCompatUtilsLXX {
    fun setInsetsOutlineProvider(view: View): InsetsUpdater {
        val provider = InsetsOutlineProvider(view)
        view.outlineProvider = provider
        return provider
    }

    private class InsetsOutlineProvider(private val mView: View) : ViewOutlineProvider(), InsetsUpdater {
        private var mLastVisibleTopInsets = NO_DATA
        override fun setInsets(insets: InputMethodService.Insets) {
            val visibleTopInsets = insets.visibleTopInsets
            if (mLastVisibleTopInsets != visibleTopInsets) {
                mLastVisibleTopInsets = visibleTopInsets
                mView.invalidateOutline()
            }
        }

        override fun getOutline(view: View, outline: Outline) {
            if (mLastVisibleTopInsets == NO_DATA) { // Call default implementation.
                BACKGROUND.getOutline(view, outline)
                return
            }
            // TODO: Revisit this when floating/resize keyboard is supported.
            outline.setRect(view.left, mLastVisibleTopInsets, view.right, view.bottom)
        }

        companion object {
            private const val NO_DATA = -1
        }

        init {
            mView.outlineProvider = this
        }
    }
}