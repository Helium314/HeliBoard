/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.keyboard.internal

import android.content.Context
import android.content.res.Resources
import android.util.Xml
import androidx.annotation.XmlRes
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.keyboard_parser.EmojiParser
import helium314.keyboard.keyboard.internal.keyboard_parser.KeyboardParser
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.sumOf
import org.xmlpull.v1.XmlPullParser

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

    fun setAllowRedundantPopupKeys(enabled: Boolean) {
        mParams.mAllowRedundantPopupKeys = enabled
    }

    fun load(id: KeyboardId): KeyboardBuilder<KP> {
        mParams.mId = id
        if (id.isEmojiKeyboard) {
            setAllowRedundantPopupKeys(true)
            readAttributes(R.xml.kbd_emoji)
            keysInRows = EmojiParser(mParams, mContext).parse()
        } else {
            try {
                val sv = Settings.getInstance().current
                addLocaleKeyTextsToParams(mContext, mParams, sv.mShowMorePopupKeys)
                mParams.mPopupKeyTypes.addAll(sv.mPopupKeyTypes)
                // add label source only if popup key type enabled
                sv.mPopupKeyLabelSources.forEach { if (it in sv.mPopupKeyTypes) mParams.mPopupKeyLabelSources.add(it) }
                keysInRows = KeyboardParser.parseLayout(mParams, mContext)
                determineAbsoluteValues()
            } catch (e: Exception) {
                Log.e(TAG, "error parsing layout $id ${id.mElementId}", e)
                throw e
            }
        }
        return this
    }

    // todo: remnant of old parser, replace it if reasonably simple
    protected fun readAttributes(@XmlRes xmlId: Int) {
        val parser = mResources.getXml(xmlId)
        while (parser.eventType != XmlPullParser.END_DOCUMENT) {
            val event = parser.next()
            if (event == XmlPullParser.START_TAG) {
                val tag = parser.name
                if ("Keyboard" == tag) {
                    mParams.readAttributes(mContext, Xml.asAttributeSet(parser))
                    return
                }
            }
        }
        mParams.readAttributes(mContext, null)
    }

    fun disableTouchPositionCorrectionDataForTest() {
        mParams.mTouchPositionCorrection.setEnabled(false)
    }

    fun setProximityCharsCorrectionEnabled(enabled: Boolean) {
        mParams.mProximityCharsCorrectionEnabled = enabled
    }

    open fun build(): Keyboard {
        if (mParams.mId.mIsSplitLayout
                && mParams.mId.mElementId in KeyboardId.ELEMENT_ALPHABET..KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            addSplit()
        }
        addKeysToParams()
        return Keyboard(mParams)
    }

    // determine key size and positions using relative width and height
    private fun determineAbsoluteValues() {
        var currentY = mParams.mTopPadding.toFloat()
        for (row in keysInRows) {
            if (row.isEmpty()) continue
            fillGapsWithSpacers(row)
            var currentX = mParams.mLeftPadding.toFloat()
            row.forEach {
                it.setDimensionsFromRelativeSize(currentX, currentY)
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "setting size and position for ${it.mLabel}, ${it.mCode}: x ${currentX.toInt()}, w ${it.mFullWidth.toInt()}")
                currentX += it.mFullWidth
            }
            currentY += row.first().mFullHeight
        }
    }

    // necessary for adjusting widths and positions properly
    // without adding spacers whose width can then be adjusted, we would have to deal with keyXPos,
    // which is more complicated than expected
    // todo: remove? maybe was only necessary with old parser
    private fun fillGapsWithSpacers(row: MutableList<KeyParams>) {
        if (mParams.mId.mElementId !in KeyboardId.ELEMENT_ALPHABET..KeyboardId.ELEMENT_SYMBOLS_SHIFTED) return
        if (row.isEmpty()) return
        if (row.all { it.xPos == 0f }) return // need existing xPos to determine gaps
        var currentX = 0f + mParams.mLeftPadding
        var i = 0
        while (i < row.size) {
            val currentKeyXPos = row[i].xPos
            if (currentKeyXPos > currentX) {
                // insert spacer
                val spacer = KeyParams.newSpacer(mParams, (currentKeyXPos - currentX) / mParams.mBaseWidth)
                spacer.yPos = row[i].yPos
                row.add(i, spacer)
                i++
                currentX += currentKeyXPos - currentX
            }
            currentX += row[i].mFullWidth
            i++
        }
        if (currentX < mParams.mOccupiedWidth) {
            // insert spacer
            val spacer = KeyParams.newSpacer(mParams, (mParams.mOccupiedWidth - currentX) / mParams.mBaseWidth)
            spacer.yPos = row.last().yPos
            row.add(spacer)
        }
    }

    private fun addSplit() {
        val spacerRelativeWidth = Settings.getInstance().current.mSplitKeyboardSpacerRelativeWidth
        // adjust gaps for the whole keyboard, so it's the same for all rows
        mParams.mRelativeHorizontalGap *= 1f / (1f + spacerRelativeWidth)
        mParams.mHorizontalGap = (mParams.mRelativeHorizontalGap * mParams.mId.mWidth).toInt()
        var maxWidthBeforeSpacer = 0f
        var maxWidthAfterSpacer = 0f
        for (row in keysInRows) {
            fillGapsWithSpacers(row)
            val y = row.first().yPos // all have the same y, so this is fine
            val relativeWidthSum = row.sumOf { it.mRelativeWidth } // sum up relative widths
            val spacer = KeyParams.newSpacer(mParams, spacerRelativeWidth)
            // insert spacer before first key that starts right of the center (also consider gap)
            var insertIndex = row.indexOfFirst { it.xPos + it.mFullWidth / 3 > mParams.mOccupiedWidth / 2 }
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
    // todo: this assumes fixed layout for symbols keys, which will change soon!
    private fun reduceSymbolAndActionKeyWidth(row: ArrayList<KeyParams>) {
        val spaceKey = row.first { it.mCode == Constants.CODE_SPACE }
        val symbolKey = row.firstOrNull { it.mCode == KeyCode.ALPHA_SYMBOL }
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
        if (mLeftEdge && !key.isSpacer) {
            key.markAsLeftEdge(mParams)
            mLeftEdge = false
        }
        if (mTopEdge) {
            key.markAsTopEdge(mParams)
        }
        if (!key.isSpacer) {
            mRightEdgeKey = key
        }
    }

    private fun endKeyboard() {
        mParams.removeRedundantPopupKeys()
        // {@link #parseGridRows(XmlPullParser,boolean)} may populate keyboard rows higher than
        // previously expected.
        // todo (low priority): mCurrentY may end up too high with the new parser and 4 row keyboards in landscape mode
        //  -> why is this happening?
        // but anyway, since the height is resized correctly already, we don't need to adjust the
        // occupied height, except for the scrollable emoji keyoards
        if (!mParams.mId.isEmojiKeyboard) return
        val actualHeight = mCurrentY - mParams.mVerticalGap + mParams.mBottomPadding
        mParams.mOccupiedHeight = mParams.mOccupiedHeight.coerceAtLeast(actualHeight)
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
        private const val TAG = "Keyboard.Builder"
    }
}
