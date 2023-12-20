// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.keyboard.clipboard

import android.content.res.Resources
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.RecyclerView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils

class ClipboardLayoutParams(res: Resources) {

    private val keyVerticalGap: Int
    private val keyHorizontalGap: Int
    private val topPadding: Int
    private val bottomPadding: Int
    private val listHeight: Int
    private val actionBarHeight: Int

    companion object {
        private const val DEFAULT_KEYBOARD_ROWS = 4
    }

    init {
        val defaultKeyboardHeight = ResourceUtils.getKeyboardHeight(res, Settings.getInstance().current)
        val defaultKeyboardWidth = ResourceUtils.getKeyboardWidth(res, Settings.getInstance().current)

        if (Settings.getInstance().current.mNarrowKeyGaps) {
            keyVerticalGap = res.getFraction(R.fraction.config_key_vertical_gap_holo_narrow,
                defaultKeyboardHeight, defaultKeyboardHeight).toInt()
            keyHorizontalGap = res.getFraction(R.fraction.config_key_horizontal_gap_holo_narrow,
                defaultKeyboardWidth, defaultKeyboardWidth).toInt()
        } else {
            keyVerticalGap = res.getFraction(R.fraction.config_key_vertical_gap_holo,
                defaultKeyboardHeight, defaultKeyboardHeight).toInt()
            keyHorizontalGap = res.getFraction(R.fraction.config_key_horizontal_gap_holo,
                defaultKeyboardWidth, defaultKeyboardWidth).toInt()
        }
        bottomPadding = (res.getFraction(R.fraction.config_keyboard_bottom_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight) * Settings.getInstance().current.mBottomPaddingScale).toInt()
        topPadding = res.getFraction(R.fraction.config_keyboard_top_padding_holo,
                defaultKeyboardHeight, defaultKeyboardHeight).toInt()

        val numRows = if (Settings.getInstance().current.mShowsNumberRow) 1 else 0
        actionBarHeight = (defaultKeyboardHeight - bottomPadding - topPadding) / (DEFAULT_KEYBOARD_ROWS + numRows) - keyVerticalGap / 2
        listHeight = defaultKeyboardHeight - actionBarHeight - bottomPadding
    }

    fun setListProperties(recycler: RecyclerView) {
        (recycler.layoutParams as FrameLayout.LayoutParams).apply {
            height = listHeight
            recycler.layoutParams = this
        }
    }

    fun setActionBarProperties(layout: FrameLayout) {
        (layout.layoutParams as LinearLayout.LayoutParams).apply {
            height = actionBarHeight
            layout.layoutParams = this
        }
    }

    fun setItemProperties(view: View) {
        (view.layoutParams as RecyclerView.LayoutParams).apply {
            topMargin = keyHorizontalGap / 2
            bottomMargin = keyVerticalGap / 2
            marginStart = keyHorizontalGap / 2
            marginEnd = keyHorizontalGap / 2
            view.layoutParams = this
        }
    }

    val actionBarContentHeight
        get() = actionBarHeight
}