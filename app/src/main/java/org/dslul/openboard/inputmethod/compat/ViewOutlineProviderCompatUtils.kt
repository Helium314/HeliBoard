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

// todo: this is not compat any more
object ViewOutlineProviderCompatUtils {
    @JvmStatic
    fun setInsetsOutlineProvider(view: View): InsetsOutlineProvider {
        val provider = InsetsOutlineProvider(view)
        view.outlineProvider = provider
        return provider
    }
}

class InsetsOutlineProvider(private val mView: View) : ViewOutlineProvider() {
    private var mLastVisibleTopInsets = NO_DATA
    init {
        mView.outlineProvider = this
    }
    fun setInsets(insets: InputMethodService.Insets) {
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
}
