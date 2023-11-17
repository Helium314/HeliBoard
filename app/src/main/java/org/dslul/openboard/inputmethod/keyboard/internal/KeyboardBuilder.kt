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
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.LocaleKeyTexts
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.MORE_KEYS_ALL
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.MORE_KEYS_MORE
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.SimpleKeyboardParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.XmlKeyboardParser
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.putLanguageMoreKeysAndLabels
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils
import org.dslul.openboard.inputmethod.latin.utils.sumOf
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
        putLanguageMoreKeysAndLabels(mContext, mParams)
        when (Settings.getInstance().current.mShowMoreKeys) {
            MORE_KEYS_ALL -> mParams.mLocaleKeyTexts.addFile(mContext.assets.open("language_key_texts/all_more_keys.txt"))
            MORE_KEYS_MORE -> mParams.mLocaleKeyTexts.addFile(mContext.assets.open("language_key_texts/more_more_keys.txt"))
        }
        keysInRows = try {
            SimpleKeyboardParser(mParams, mContext).parseFromAssets(id.mSubtype.keyboardLayoutSetName)
        } catch (e: Exception) {
            Log.e(TAG, "parsing layout \"${id.mSubtype.keyboardLayoutSetName}\" failed, trying fallback to qwerty", e)
            SimpleKeyboardParser(mParams, mContext).parseFromAssets("qwerty")
        }

        useRelative()

        // todo:
        //  add the other latin layouts (dvorak and so on) except pcqwerty
        //  allow users to switch to old style (keep it until all layouts are switched)
        //   really helps to find differences
        //    add a text that issues / unwanted differences should be reported, as the setting will be removed at some point
        //   have some fallback to the old style when parse or build fails?
        //    would be good for users, but need to inform them so they can provide stack traces
        //  problem (already exists): joined moreKeys are in cached keyboard, so removing a language doesn't change anything
        //   -> remove from cache, if necessary just reload all
        //   also happens when changing the more more keys setting
        // todo: documentation needed
        //  key and then (optionally) moreKeys, separated by space
        //  backslash before some characters (check which ones... ?, @, comma and a few more)
        //   for user-defined stuff not necessary (will be inserted as needed when reading)
        //  % for language morekeys (also other placeholders, but usually not necessary)
        //  language morekeys should never contain "special" morekeys, i.e. those starting with !
        //   exception for punctuation
        //   if it's necessary that they contain special stuff, parsing of those things needs to be adapted
        //  placeholder for currency key: $$$

        // todo: further plan to make it actually useful
        //  make the remove duplicate moreKey thing an option?
        //   why is it on for serbian (latin), but not for german (german)?
        //   only nordic and serbian_qwertz layouts have it disabled, default it enabled
        //  migrate symbol layouts to this style
        //   better before user-defined layouts
        //   should be straightforward to do
        //  allow users to define their own layouts
        //   need to solve the scaling issue with number row and 5 row keyboards
        //   write up how things work for users, also regarding language more keys
        //    readme, maybe also some "help" button in a dialog
        //   some sort of proper UI, or simply text input?
        //    better text import for the start because of much work
        //    ui follows later (consider that users need to be able to start from existing layouts!)
        //   some warning if more than 2 or 3 characters on a single label
        //    currently can't resize keys, but could set autoXScale (does only decrease size, never increase)
        //   careful about moreKeys: if moreKeys don't fit on screen, parser throws an exception!
        //    need to somehow test for this
        //    is that autoColumnOrder thing a workaround for that?
        //     still would crash for a single huge label
        //   popup and (single key) long press preview rescale the label on x only, which may deform emojis
        //   does glide typing work with multiple letters on one key? if not, users should be notified
        //   maybe allow users to define their own symbol and shift-symbol layouts
        //  migrate emoji layouts to this style
        //   emojis are defined in that string array, should be simple to handle
        //   parsing could be done into a single row, which is then split as needed
        //    this might help with split layout (no change in key size, but in number of rows!)
        //   write another parser, it should already consider split
        //   more dynamic / lazy way for loading the 10 emoji keyboards?
        //    use recyclerView instead of a keyboard?
        //    or recyclerView with one keyboardView per row?
        //    could be possible if creating the keyboards is fast enough... but also need to check whether it's ok for memory use and stuff
        //  migrate keypad layouts to this style
        //   will need more configurable layout definition -> another parser
        //  migrate moreKeys and moreSuggestions to this style?
        //   at least they should not make use of the KeyTextsSet/Table (and of the XmlKeyboardParser?)
        //  migrate other languages to this style
        //   may be difficult in some cases, like additional row, or no shift key, or pc qwerty layout
        //   also the (integrated) number row might cause issues
        //   at least some of these layouts will need more complicated definition, not just a simple text file
        //   some languages also change symbol view, e.g. fa changes symbols row 3
        //   add more layouts before doing this? or just keep the layout conversion script

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
        //  shiftedLetterActivated: period and some keys on pcqwerty, tablet only
        //  fromCustomActionLabel: action key with customLabelActionKeyStyle -> check parser where to get this info
        //  followFunctionalTextColor: number mode keys, action key
        //  keepBackgroundAspectRatio: lxx and rounded action more keys, lxx no-border action and emoji, moreKeys keyboard view
        //  disableKeyHintLabel: keys in pcqwerty row 1 and number row
        //  disableAdditionalMoreKeys: keys in pcqwerty row 1
        //  -> probably can't define the related layouts in a simple way, better use some json or xml or anything more reasonable than the simple text format
        //   maybe remove some of the flags? or keep supporting them?
        //  for pcqwerty: hasShiftedLetterHint -> hasShiftedLetterHint|shiftedLetterActivated when shift is enabled, need to consider if the flag is used
        //   actually period key also has shifted letter hint

        return this
    }

    fun loadFromXml(xmlId: Int, id: KeyboardId): KeyboardBuilder<KP> {
        mParams.mId = id
        if (id.mElementId == KeyboardId.ELEMENT_ALPHABET // todo: id.isAlphabetKeyboard (and check the setting, to be implemented)
            && this::class == KeyboardBuilder::class
            && ScriptUtils.getScriptFromSpellCheckerLocale(mParams.mId.locale) == ScriptUtils.SCRIPT_LATIN // todo: fails in too many cases, wtf? better go by layout name
            && id.mSubtype.keyboardLayoutSetName != "pcqwerty"
        ) {
            loadSimpleKeyboard(id)
            return this
        }
        // loading a keyboard should set default params like mParams.readAttributes(mContext, attrs);
        // attrs may be null, then default values are used (looks good for "normal" keyboards)
        try {
            XmlKeyboardParser(xmlId, mParams, mContext).use { keyboardParser ->
                keysInRows = keyboardParser.parseKeyboard()
            }
        } catch (e: XmlPullParserException) {
            Log.w(TAG, "keyboard XML parse error", e)
            throw IllegalArgumentException(e.message, e)
        } catch (e: IOException) {
            Log.w(TAG, "keyboard XML parse error", e)
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
            var currentX = 0f
            row.forEach {
                it.setDimensionsFromRelativeSize(currentX, currentY)
                currentX += it.mFullWidth
            }
            // need to truncate to int here, otherwise it may end up one pixel lower than original
            // though actually not truncating would be more correct... but that's already an y / height issue somewhere in Key
            // todo (later): round, and do the change together with the some thing in Key(KeyParams keyParams)
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
            val spacer = KeyParams.newSpacer(mParams, spacerRelativeWidth)
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
                // todo (later): markAsBottomKey if in bottom row?
                //  this is not done in original parsing style, but why not?
                //  just test it (with different bottom paddings)
            }
            endRow()
        }
        endKeyboard()
    }

    companion object {
        private const val TAG = "Keyboard.Builder"
    }
}
