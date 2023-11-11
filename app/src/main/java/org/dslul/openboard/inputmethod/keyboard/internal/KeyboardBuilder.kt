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
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.SimpleLayoutParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.XmlKeyboardParser
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.common.StringUtils
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

    fun loadSimpleKeyboard(id: KeyboardId): KeyboardBuilder<KP> {
        mParams.mId = id
        keysInRows = SimpleLayoutParser(mParams, mContext).parse()
        useRelative()

        // todo: further plan after this thing is merged, but unused
        //  create languageMoreKeys list from stuff in keyboard-text tools
        //   probably use files in assets, and cache them in a weak hash map with localestring as key
        //    or better 2 letter code, and join codes when combining languageMoreKeys for multiple
        //    or no caching if loading and combining is fast anyway
        //    the locale morekeys then should be a map label -> moreKeys
        //    the whole moreKeys map for the current keyboard could be in mParams to simplify access
        //   file format? it's easy to switch, but still... text like above? json?
        //    resources? could look like donottranslate-more-keys files
        //    test first whether something like morekeys_&#x1002;, or morekeys_&#x00F8; or better morekeys_ø actually works
        //     if not, definitely don't use resources
        //   consider the % placeholder, this should still be used and documented
        //    though maybe has issues when merging languages
        //   how to deal with unnecessary moreKeys?
        //    e.g. german should have ö as moreKey on o, but swiss german layout has ö as separate key
        //    still have ö on o (like now), or remove it? or make it optional?
        //    is this handled by KeyboardParams.removeRedundantMoreKeys?
        //   doing it in resources should be possible with configuration and contextThemeWrapper, but probably more complicated than simple files
        //   not only moreKeys, also currency key and some labels keys should be translated, though not necessarily in that map
        //  migrate latin layouts to this style
        //   allow users to define their own layouts
        //    some sort of proper UI, or simply text input?
        //     better text import for the start because of much work
        //     ui follows later (consider that users need to be able to start from existing layouts!)
        //    some warning if more than 2 characters on a key
        //     currently can't resize keys, but could set autoXScale
        //    check whether emojis are correctly not colored when on main keyboard
        //   write up how things work, also regarding language more keys
        //  migrate symbol layouts to this style
        //   maybe allow users to define their own symbol and shift-symbol layouts
        //   write a new parser, most of the code should be re-usable anyway
        //  migrate emoji layouts to this style
        //   emojis are defined in that string array, should be simple to handle
        //   more dynamic / lazy way for loading the 10 emoji keyboards?
        //   parsing could be done into a single row, which is then split as needed
        //    this might help with split layout (no change in key size, but in number of rows!)
        //   write another parser, it should already consider split
        //  migrate keypad layouts to this style
        //   will need more configurable layout definition -> another parser (json? xml?), and check how to handle code that is needed in both
        //  migrate moreKeys and moreSuggestions to this style
        //  migrate other languages to this style
        //   may be difficult in some cases, like additional row, or no shift key, or pc qwerty layout
        //   also the (integrated) number row might cause issues
        //   at least some of these layouts will need more complicated definition, not just a simple text file
        //  remove all the keyboard layout related xmls if possible
        //   rows_, rowkeys_, row_, kbd_ maybe keyboard_layout_set, keys_, keystyle_, key_
        //   and the texts_table and its source tools
        return this
    }

    fun loadFromXml(xmlId: Int, id: KeyboardId): KeyboardBuilder<KP> {
        // need to check for exact class, otherwise moreKeys look weird
        if (id.mElementId == KeyboardId.ELEMENT_ALPHABET && this::class == KeyboardBuilder::class)
            return loadSimpleKeyboard(id) // lets try...
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
        if (id.isAlphabetKeyboard && this::class == KeyboardBuilder::class)
            useRelative()
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
        if (Settings.getInstance().current.mIsSplitKeyboardEnabled
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
        var currentY = mParams.mTopPadding.toFloat()
        for (row in keysInRows) {
            if (row.isEmpty()) continue
            fillGapsWithSpacers(row)
            require(row.all { it.yPos == row.first().yPos }) { "not all yPos equal" } // this is if yPos is already pre-filled
            var currentX = 0f
            row.forEach {
                it.setDimensionsFromRelativeSize(currentX, currentY)
                Log.i("test", "key ${it.mCode} / ${StringUtils.newSingleCodePointString(it.mCode)}: ${it.xPos} + ${it.mFullWidth}")
                currentX += it.mFullWidth
            }
            // need to truncate here, otherwise it may end up one pixel lower than original
            // though actually not truncating would be more correct... but that's already an y / height issue somewhere in Key
            currentY += row.first().mFullHeight.toInt()
        }
    }

    // necessary for adjusting widths and positions properly
    // without adding spacers whose width can then be adjusted, we would have to deal with keyXPos,
    // which is more complicated than expected
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
        val spacerRelativeWidth = Settings.getInstance().current.mSpacerRelativeWidth
        // adjust gaps for the whole keyboard, so it's the same for all rows
        // todo: maybe remove? not sure if narrower gaps are desirable
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
// todo: move to some utils
inline fun <T> Iterable<T>.sumOf(selector: (T) -> Float): Float {
    var sum = 0f
    for (element in this) {
        sum += selector(element)
    }
    return sum
}
