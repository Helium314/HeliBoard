/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal

import android.content.Context
import android.content.res.Resources
import android.util.Log
import org.dslul.openboard.inputmethod.annotations.UsedForTesting
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.Keyboard
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.XmlKeyboardParser
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.xmlpull.v1.XmlPullParserException
import java.io.IOException

// TODO: Write unit tests for this class.
open class KeyboardBuilder<KP : KeyboardParams>(protected val mContext: Context, @JvmField val mParams: KP) {
    @JvmField
    protected val mResources: Resources
    private var mCurrentY = 0

    private var mLeftEdge = false
    private var mTopEdge = false
    private var mRightEdgeKey: Key? = null
    private lateinit var keysInRows: ArrayList<ArrayList<KeyParams>>

    init {
        val res = mContext.resources
        mResources = res
        mParams.GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width)
        mParams.GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height)
    }

    fun setAllowRedundantMoreKeys(enabled: Boolean) {
        mParams.mAllowRedundantMoreKeys = enabled
    }

    fun loadFromXml(xmlId: Int, id: KeyboardId): KeyboardBuilder<KP> {
        mParams.mId = id
        // loading a keyboard should set default params like mParams.readAttributes(mContext, attrs);
        // attrs may be null, then default values are used (looks good for "normal" keyboards)
        try {
            XmlKeyboardParser(xmlId, mParams, mContext).use { keyboardParser ->
                keysInRows = keyboardParser.parseKeyboard()
            }
        } catch (e: XmlPullParserException) {
            Log.w(BUILDER_TAG, "keyboard XML parse error", e)
            throw IllegalArgumentException(e.message, e)
        } catch (e: IOException) {
            Log.w(BUILDER_TAG, "keyboard XML parse error", e)
            throw RuntimeException(e.message, e)
        }
        return this
    }

    @UsedForTesting
    fun disableTouchPositionCorrectionDataForTest() {
        mParams.mTouchPositionCorrection.setEnabled(false)
    }

    fun setProximityCharsCorrectionEnabled(enabled: Boolean) {
        mParams.mProximityCharsCorrectionEnabled = enabled
    }

    open fun build(): Keyboard {
        if (Settings.getInstance().current.mIsSplitKeyboardEnabled // todo: remove parsing for split layouts and read params, not settings
                && mParams.mId.mElementId in KeyboardId.ELEMENT_ALPHABET..KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            addSplit()
        }
        addKeysToParams()
        return Keyboard(mParams)
    }

    // resize keyboard using relative params
    // ideally this should not change anything
    //  but it does a little, depending on how float -> int is done (cast or round, and when to sum up gaps and width)
    //  still should not be more than a pixel difference
    // keep it around for a while, for testing
    private fun useRelative() {
        for (row in keysInRows) {
            if (row.isEmpty()) continue
            fillGapsWithSpacers(row)
            val y = row.first().yPos
            assert(row.all { it.yPos == y })
            var currentX = 0f
            row.forEach {
                it.setDimensionsFromRelativeSize(currentX, y)
                currentX += it.mFullWidth
            }
        }
    }

    // necessary for adjusting widths and positions properly
    // without adding spacers whose width can then be adjusted, we would have to deal with keyXPos,
    // which is more complicated than expected
    private fun fillGapsWithSpacers(row: MutableList<KeyParams>) {
        if (mParams.mId.mElementId !in KeyboardId.ELEMENT_ALPHABET..KeyboardId.ELEMENT_SYMBOLS_SHIFTED) return
        if (row.isEmpty()) return
        var currentX = 0f + mParams.mLeftPadding
        var i = 0
        while (i < row.size) {
            val currentKeyXPos = row[i].xPos
            if (currentKeyXPos > currentX) {
                // insert spacer
                val spacer = KeyParams.newSpacer(mParams)
                spacer.mRelativeWidth = (currentKeyXPos - currentX) / mParams.mBaseWidth
                spacer.yPos = row[i].yPos
                spacer.mRelativeHeight = row[i].mRelativeHeight
                row.add(i, spacer)
                i++
                currentX += currentKeyXPos - currentX
            }
            currentX += row[i].mFullWidth
            i++
        }
        if (currentX < mParams.mOccupiedWidth) {
            // insert spacer
            val spacer = KeyParams.newSpacer(mParams)
            spacer.mRelativeWidth = (mParams.mOccupiedWidth - currentX) / mParams.mBaseWidth
            spacer.mRelativeHeight = row.last().mRelativeHeight
            spacer.yPos = row.last().yPos
            row.add(spacer)
        }
    }

    private fun addSplit() {
        val metrics = mResources.displayMetrics
        val widthDp = metrics.widthPixels / metrics.density
        // adapted relative space width to (current) screen width (ca between 0.15 - 0.25), todo: maybe make it further adjustable by the user (sth like 50-200%)
        val spacerRelativeWidth = ((widthDp - 600) / 6000f + 0.15f).coerceAtLeast(0.15f).coerceAtMost(0.25f)
        // adjust gaps for the whole keyboard, so it's the same for all rows, todo: maybe remove? not sure if more narrow gaps are desirable
        mParams.mRelativeHorizontalGap *= 1f / (1f + spacerRelativeWidth)
        mParams.mHorizontalGap = (mParams.mRelativeHorizontalGap * mParams.mId.mWidth).toInt()
        var maxWidthBeforeSpacer = 0f
        var maxWidthAfterSpacer = 0f
        for (row in keysInRows) {
            fillGapsWithSpacers(row)
            val y = row.first().yPos // all have the same y, so this is fine
            val relativeWidthSum = row.sumOf { it.mRelativeWidth } // sum up relative widths
            val spacer = KeyParams.newSpacer(mParams)
            spacer.mRelativeWidth = spacerRelativeWidth
            spacer.mRelativeHeight = row.first().mRelativeHeight
            // insert spacer before first key that starts right of the center (also consider gap)
            var insertIndex = row.indexOfFirst { it.xPos > mParams.mOccupiedWidth / 2 }
                .takeIf { it > -1 } ?: (row.size / 2) // fallback should never be needed, but better than having an error
            if (row.any { it.mCode == Constants.CODE_SPACE }) {
                val spaceLeft = row.single { it.mCode == Constants.CODE_SPACE }
                reduceSymbolAndActionKeyWidth(row)
                insertIndex = row.indexOf(spaceLeft) + 1
                val widthBeforeSpace = row.subList(0, insertIndex - 1).sumOf { it.mRelativeWidth }
                val widthAfterSpace = row.subList(insertIndex, row.size).sumOf { it.mRelativeWidth }
                val spaceLeftWidth = (maxWidthBeforeSpacer - widthBeforeSpace).coerceAtLeast(mParams.mDefaultRelativeKeyWidth)
                val spaceRightWidth = (maxWidthAfterSpacer - widthAfterSpace).coerceAtLeast(mParams.mDefaultRelativeKeyWidth)
                val spacerWidth = spaceLeft.mRelativeWidth + spacerRelativeWidth - spaceLeftWidth - spaceRightWidth
                if (spacerWidth > 0.05f) {
                    // only insert if the spacer has a reasonable width
                    val spaceRight = KeyParams(spaceLeft)
                    spaceLeft.mRelativeWidth = spaceLeftWidth
                    spaceRight.mRelativeWidth = spaceRightWidth
                    spacer.mRelativeWidth = spacerWidth
                    row.add(insertIndex, spaceRight)
                    row.add(insertIndex, spacer)
                } else {
                    // otherwise increase space width, so other keys are resized properly
                    spaceLeft.mRelativeWidth += spacerWidth
                }
            } else {
                val widthBeforeSpacer = row.subList(0, insertIndex).sumOf { it.mRelativeWidth }
                val widthAfterSpacer = row.subList(insertIndex, row.size).sumOf { it.mRelativeWidth }
                maxWidthBeforeSpacer = maxWidthBeforeSpacer.coerceAtLeast(widthBeforeSpacer)
                maxWidthAfterSpacer = maxWidthAfterSpacer.coerceAtLeast(widthAfterSpacer)
                row.add(insertIndex, spacer)
            }
            // re-calculate relative widths
            val relativeWidthSumNew = row.sumOf { it.mRelativeWidth }
            val widthFactor = relativeWidthSum / relativeWidthSumNew
            // re-calculate absolute sizes and positions
            var currentX = 0f
            row.forEach {
                it.mRelativeWidth *= widthFactor
                it.setDimensionsFromRelativeSize(currentX, y)
                currentX += it.mFullWidth
            }
        }
    }

    // reduce width of symbol and action key if in the row, and add this width to space to keep other key size constant
    private fun reduceSymbolAndActionKeyWidth(row: ArrayList<KeyParams>) {
        val spaceKey = row.first { it.mCode == Constants.CODE_SPACE }
        val symbolKey = row.firstOrNull { it.mCode == Constants.CODE_SWITCH_ALPHA_SYMBOL }
        val symbolKeyWidth = symbolKey?.mRelativeWidth ?: 0f
        if (symbolKeyWidth > mParams.mDefaultRelativeKeyWidth) {
            val widthToChange = symbolKey!!.mRelativeWidth - mParams.mDefaultRelativeKeyWidth
            symbolKey.mRelativeWidth -= widthToChange
            spaceKey.mRelativeWidth += widthToChange
        }
        val actionKey = row.firstOrNull { it.mBackgroundType == Key.BACKGROUND_TYPE_ACTION }
        val actionKeyWidth = actionKey?.mRelativeWidth ?: 0f
        if (actionKeyWidth > mParams.mDefaultRelativeKeyWidth * 1.1f) { // allow it to stay a little wider
            val widthToChange = actionKey!!.mRelativeWidth - mParams.mDefaultRelativeKeyWidth * 1.1f
            actionKey.mRelativeWidth -= widthToChange
            spaceKey.mRelativeWidth += widthToChange
        }
    }

    private fun startKeyboard() {
        mCurrentY += mParams.mTopPadding
        mTopEdge = true
    }

    private fun startRow() {
        mLeftEdge = true
        mRightEdgeKey = null
    }

    private fun endRow() {
        val rightEdgeKey = mRightEdgeKey
        if (rightEdgeKey != null) {
            rightEdgeKey.markAsRightEdge(mParams)
            mCurrentY += rightEdgeKey.height + rightEdgeKey.verticalGap
            mRightEdgeKey = null
        }
        mLeftEdge = false
        mTopEdge = false
    }

    private fun endKey(key: Key) {
        mParams.onAddKey(key)
        if (mLeftEdge) {
            key.markAsLeftEdge(mParams)
            mLeftEdge = false
        }
        if (mTopEdge) {
            key.markAsTopEdge(mParams)
        }
        mRightEdgeKey = key
    }

    private fun endKeyboard() {
        mParams.removeRedundantMoreKeys()
        // {@link #parseGridRows(XmlPullParser,boolean)} may populate keyboard rows higher than
        // previously expected.
        val actualHeight = mCurrentY - mParams.mVerticalGap + mParams.mBottomPadding
        mParams.mOccupiedHeight = Math.max(mParams.mOccupiedHeight, actualHeight)
    }

    private fun addKeysToParams() {
        // need to reset it, we need to sum it up to get the height nicely
        // (though in the end we could just not touch it at all, final used value is the same as the one before resetting)
        mCurrentY = 0
        startKeyboard()
        for (row in keysInRows) {
            startRow()
            for (keyParams in row) {
                endKey(keyParams.createKey())
            }
            endRow()
        }
        endKeyboard()
    }

    companion object {
        private const val BUILDER_TAG = "Keyboard.Builder"
    }
}

// adapted from Kotlin source: https://github.com/JetBrains/kotlin/blob/7a7d392b3470b38d42f80c896b7270678d0f95c3/libraries/stdlib/common/src/generated/_Collections.kt#L3004
private inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
