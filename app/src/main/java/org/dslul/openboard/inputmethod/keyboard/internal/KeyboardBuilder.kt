/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.dslul.openboard.inputmethod.keyboard.internal

import android.content.Context
import android.content.res.Resources
import android.util.Xml
import androidx.annotation.XmlRes
import org.dslul.openboard.inputmethod.annotations.UsedForTesting
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.Keyboard
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.EmojiParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.KeyboardParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.define.DebugFlags
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.Log
import org.dslul.openboard.inputmethod.latin.utils.sumOf
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

    fun setAllowRedundantMoreKeys(enabled: Boolean) {
        mParams.mAllowRedundantMoreKeys = enabled
    }

    // todo for adding layouts
    //  make sure additional newlines, spaces, or windows newlines don't mess up anything
    //  set autoXScale if more than 2 codepoints on a key
    //   looks like is it already set? check!
    //  careful about moreKeys: if moreKeys don't fit on screen, parser throws an exception!
    //   need to somehow test for this, try adding many moreKeys and few very long ones
    //   depending on where we get crashes, inform user what is invalid and don't accept layout
    //  potential keyspec parsing issues:
    //   KeySpecParser.getLabel and similar
    //    these work with special characters like | and \ doing things depending on their position
    //    if used wrongly, things can crash
    //    -> maybe disable this style of parsing when creating MoreKeySpec of a user-provided layout
    //     or when reading the layouts make sure stuff is escaped properly
    //     maybe just try spec-parsing all the stuff and give error message if not ok
    //  write up how things work for users, also regarding language more keys
    //   readme, maybe also some "help" button in a dialog
    //   some warning if more than 2 or 3 characters on a key / moreKey (but only refuse if moreKey is too long)
    //  popup and (single key) long press preview rescale the label on x only, which may deform emojis (test)
    //  does glide typing work with multiple letters on one key? if not, users should be warned if they try it
    //  allow users to define their own symbol and shift-symbol layouts
    //   will need a different way of loading then...
    //  allow editing layouts in a simple UI (and allow starting from existing layoust

        // labelFlags should be set correctly
        //  alignHintLabelToBottom: on lxx and rounded themes, but did not find what it actually does...
        //  alignIconToBottom: space_key_for_number_layout
        //  alignLabelOffCenter: number keys in phone layout
        //  fontNormal: turkish (rows 1 and 2 only), .com, emojis, numModeKeyStyle, a bunch of non-latin languages
        //    -> switches to normal typeface, only relevant for holo which has bold
        //  fontMonoSpace: unused
        //  fontDefault: keyExclamationQuestion, a bunch of "normal" keys in fontNormal layouts like thai
        //    -> switches to default defined typeface, useful e.g. if row has fontNormal
        //  followKeyLargeLetterRatio: number keys in number/phone/numpad layouts
        //  followKeyLetterRatio: mode keys in number layouts, some keys in some non-latin layouts
        //  followKeyLabelRatio: enter key, some keys in phone layout (same as followKeyLetterRatio + followKeyLargeLetterRatio)
        //  followKeyHintLabelRatio: unused directly (but includes some others)
        //  hasPopupHint: basically the long-pressable functional keys
        //  hasShiftedLetterHint: period key and some keys on pcqwerty
        //  hasHintLabel: number keys in number layouts
        //  autoXScale: com key, action keys, some on phone layout, some non-latin languages
        //  autoScale: only one single letter in khmer layout (includes autoXScale)
        //  preserveCase: action key + more keys, com key, shift keys
        //  shiftedLetterActivated: period and some keys on pcqwerty, tablet only (wtf, when enabled can't open moreKeys -> remove? or what would be the use?)
        //  fromCustomActionLabel: action key with customLabelActionKeyStyle -> check parser where to get this info
        //  followFunctionalTextColor: number mode keys, action key
        //  keepBackgroundAspectRatio: lxx and rounded action more keys, lxx no-border action and emoji, moreKeys keyboard view
        //  disableKeyHintLabel: keys in pcqwerty row 1 and number row
        //  disableAdditionalMoreKeys: only keys in pcqwerty row 1 so there is no number row -> not necessary with the new layouts, just remove it completely
        //  maybe remove some of the flags? or keep supporting them?
        //  for pcqwerty: hasShiftedLetterHint -> hasShiftedLetterHint|shiftedLetterActivated when shift is enabled, need to consider if the flag is used
        //   actually period key also has shifted letter hint

    fun load(xmlId: Int, id: KeyboardId): KeyboardBuilder<KP> {
        mParams.mId = id
        if (id.isEmojiKeyboard) {
            setAllowRedundantMoreKeys(true)
            readAttributes(R.xml.kbd_emoji_category1) // all the same anyway, gridRows are ignored
            keysInRows = EmojiParser(mParams, mContext).parse()
        } else {
            try {
                val sv = Settings.getInstance().current
                addLocaleKeyTextsToParams(mContext, mParams, sv.mShowMoreMoreKeys)
                mParams.mMoreKeyTypes.addAll(sv.mMoreKeyTypes)
                // add label source only if moreKey type enabled
                sv.mMoreKeyLabelSources.forEach { if (it in sv.mMoreKeyTypes) mParams.mMoreKeyLabelSources.add(it) }
                keysInRows = KeyboardParser.parseFromAssets(mParams, mContext)
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

    @UsedForTesting
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
