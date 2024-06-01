// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Configuration
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyLabel
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyType
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.TextKeyData
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import helium314.keyboard.latin.utils.POPUP_KEYS_NUMBER
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.removeFirst
import helium314.keyboard.latin.utils.replaceFirst
import helium314.keyboard.latin.utils.splitAt
import helium314.keyboard.latin.utils.sumOf

/**
 * Abstract parser class that handles creation of keyboard from [KeyData] arranged in rows,
 * provided by the extending class.
 *
 * Functional keys are pre-defined and can't be changed, with exception of comma, period and similar
 * keys in symbol layouts.
 * By default, all normal keys have the same width and flags, which may cause issues with the
 * requirements of certain non-latin languages.
 */
class KeyboardParser(private val params: KeyboardParams, private val context: Context) {
    private val defaultLabelFlags = when {
        params.mId.isAlphabetKeyboard -> params.mLocaleKeyboardInfos.labelFlags
        // reproduce the no-hints in symbol layouts
        // todo: add setting? or put it in TextKeyData to happen only if no label flags specified explicitly?
        params.mId.isAlphaOrSymbolKeyboard -> Key.LABEL_FLAGS_DISABLE_HINT_LABEL
        else -> 0
    }

    fun parseLayout(): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)

        val baseKeys = RawKeyboardParser.parseLayout(params, context)
        val keysInRows = createRows(baseKeys)
        // rescale height if we have anything but the usual 4 rows
        val heightRescale = if (keysInRows.size != 4) 4f / keysInRows.size else 1f
        if (heightRescale != 1f) {
            keysInRows.forEach { row -> row.forEach { it.mHeight *= heightRescale } }
        }

        return keysInRows
    }

    private fun createRows(baseKeys: MutableList<MutableList<KeyData>>): ArrayList<ArrayList<KeyParams>> {
        // add padding for number layouts in landscape mode (maybe do it some other way later)
        if (params.mId.isNumberLayout && params.mId.mElementId != KeyboardId.ELEMENT_NUMPAD
                && context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            params.mLeftPadding = (params.mOccupiedWidth * 0.1f).toInt()
            params.mRightPadding = (params.mOccupiedWidth * 0.1f).toInt()
            params.mBaseWidth = params.mOccupiedWidth - params.mLeftPadding - params.mRightPadding
        }

        addNumberRowOrPopupKeys(baseKeys)
        if (params.mId.isAlphabetKeyboard)
            addSymbolPopupKeys(baseKeys)
        if (params.mId.isAlphaOrSymbolKeyboard && params.mId.mNumberRowEnabled)
            baseKeys.add(0, params.mLocaleKeyboardInfos.getNumberRow()
                .mapTo(mutableListOf()) { it.copy(newLabelFlags = Key.LABEL_FLAGS_DISABLE_HINT_LABEL or defaultLabelFlags) })

        val allFunctionalKeys = RawKeyboardParser.parseLayout(params, context, true)
        adjustBottomFunctionalRowAndBaseKeys(allFunctionalKeys, baseKeys)

        if (allFunctionalKeys.none { it.singleOrNull()?.isKeyPlaceholder() == true })
            // add a placeholder so splitAt does what we really want
            allFunctionalKeys.add(0, mutableListOf(TextKeyData(type = KeyType.PLACEHOLDER)))

        val (functionalKeysTop, functionalKeysBottom) = allFunctionalKeys.splitAt { it.singleOrNull()?.isKeyPlaceholder() == true }

        // offset for bottom, relevant for getting correct functional key rows
        val bottomIndexOffset = baseKeys.size - functionalKeysBottom.size

        val functionalKeys = mutableListOf<Pair<List<KeyParams>, List<KeyParams>>>()
        val baseKeyParams = baseKeys.mapIndexed { i, it ->
            val row: List<KeyData> = if (params.mId.isAlphaOrSymbolKeyboard && i == baseKeys.lastIndex - 1 && Settings.getInstance().isTablet) {
                // add bottom row extra keys
                // todo (later): this can make very customized layouts look awkward
                //  decide when to (not) add it
                //  when not adding, consider that punctuation popup keys should not remove those keys!
                val tabletExtraKeys = params.mLocaleKeyboardInfos.getTabletExtraKeys(params.mId.mElementId)
                tabletExtraKeys.first + it + tabletExtraKeys.second
            } else {
                it
            }

            // build list of functional keys of same size as baseKeys
            val functionalKeysFromTop = functionalKeysTop.getOrNull(i) ?: emptyList()
            val functionalKeysFromBottom = functionalKeysBottom.getOrNull(i - bottomIndexOffset) ?: emptyList()
            functionalKeys.add(getFunctionalKeysBySide(functionalKeysFromTop, functionalKeysFromBottom))

            row.map { key ->
                val extraFlags = if (key.label.length > 2 && key.label.codePointCount(0, key.label.length) > 2 && !isEmoji(key.label))
                        Key.LABEL_FLAGS_AUTO_X_SCALE
                    else 0
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "adding key ${key.label}, ${key.code}")
                key.toKeyParams(params, defaultLabelFlags or extraFlags)
            }
        }
        return setReasonableWidths(baseKeyParams, functionalKeys)
    }

    /** interprets key width -1, adjusts row size to nicely fit on screen, adds spacers if necessary */
    private fun setReasonableWidths(bassKeyParams: List<List<KeyParams>>, functionalKeys: List<Pair<List<KeyParams>, List<KeyParams>>>): ArrayList<ArrayList<KeyParams>> {
        val keysInRows = ArrayList<ArrayList<KeyParams>>()
        // expand width = -1 keys and make sure rows fit on screen, insert spacers if necessary
        bassKeyParams.forEachIndexed { i, keys ->
            val (functionalKeysLeft, functionalKeysRight) = functionalKeys[i]
            // sum up width, excluding -1 elements (put those in a separate list)
            val varWidthKeys = mutableListOf<KeyParams>()
            var totalWidth = 0f
            val allKeys = (functionalKeysLeft + keys + functionalKeysRight)
            allKeys.forEach {
                if (it.mWidth == -1f) varWidthKeys.add(it)
                else totalWidth += it.mWidth
            }

            // set width for varWidthKeys
            if (varWidthKeys.isNotEmpty()) {
                val width = if (totalWidth + varWidthKeys.size * params.mDefaultKeyWidth > 1)
                    params.mDefaultKeyWidth // never go below default width
                else (1f - totalWidth) / varWidthKeys.size // split remaining space evenly
                varWidthKeys.forEach { it.mWidth = width }

                // re-calculate total width
                totalWidth = allKeys.sumOf { it.mWidth }
            }

            // re-scale total width, or add spacers (or do nothing if totalWidth is near 1)
            if (totalWidth < 0.9999f) { // add spacers
                val spacerWidth = (1f - totalWidth) / 2
                val paramsRow = ArrayList<KeyParams>(functionalKeysLeft + KeyParams.newSpacer(params, spacerWidth) + keys +
                        KeyParams.newSpacer(params, spacerWidth) + functionalKeysRight)
                keysInRows.add(paramsRow)
            } else {
                if (totalWidth > 1.0001f) { // re-scale total width
                    val normalKeysWith = keys.sumOf { it.mWidth }
                    val functionalKeysWidth = totalWidth - normalKeysWith
                    val scaleFactor = (1f - functionalKeysWidth) / normalKeysWith
                    // re-scale normal  keys if factor is > 0.82, otherwise re-scale all keys
                    if (scaleFactor > 0.82f) keys.forEach { it.mWidth *= scaleFactor }
                    else allKeys.forEach { it.mWidth /= totalWidth }
                }
                keysInRows.add(ArrayList(allKeys))
            }
        }

        // adjust last normal row key widths to be aligned with row above, assuming a reasonably close-to-default alpha / symbol layout
        // like in original layouts, e.g. for nordic and swiss layouts
        if (!params.mId.isAlphaOrSymbolKeyboard || bassKeyParams.size < 3 || bassKeyParams.last().isNotEmpty())
            return keysInRows
        val lastNormalRow = bassKeyParams[bassKeyParams.lastIndex - 1]
        val rowAboveLast = bassKeyParams[bassKeyParams.lastIndex - 2]
        val lastNormalRowKeyWidth = lastNormalRow.first().mWidth
        val rowAboveLastNormalRowKeyWidth = rowAboveLast.first().mWidth
        if (lastNormalRowKeyWidth <= rowAboveLastNormalRowKeyWidth + 0.0001f // no need
            || lastNormalRowKeyWidth / rowAboveLastNormalRowKeyWidth > 1.1f // don't resize on large size difference
            || lastNormalRow.any { it.isSpacer } || rowAboveLast.any { it.isSpacer } // annoying to deal with, and probably no resize wanted anyway
            || lastNormalRow.any { it.mWidth != lastNormalRowKeyWidth } || rowAboveLast.any { it.mWidth != rowAboveLastNormalRowKeyWidth })
            return keysInRows
        val numberOfKeysInLast = lastNormalRow.count { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }
        val widthBefore = numberOfKeysInLast * lastNormalRowKeyWidth
        val widthAfter = numberOfKeysInLast * rowAboveLastNormalRowKeyWidth
        val spacerWidth = (widthBefore - widthAfter) / 2
        // resize keys
        lastNormalRow.forEach { if (it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL) it.mWidth = rowAboveLastNormalRowKeyWidth }
        // add spacers
        val lastNormalFullRow = keysInRows[keysInRows.lastIndex - 1]
        lastNormalFullRow.add(lastNormalFullRow.indexOfFirst { it == lastNormalRow.first() }, KeyParams.newSpacer(params, spacerWidth))
        lastNormalFullRow.add(lastNormalFullRow.indexOfLast { it == lastNormalRow.last() } + 1, KeyParams.newSpacer(params, spacerWidth))

        return keysInRows
    }

    /**
     *  adds / removes keys to the bottom row
     *  assumes a close-to-default bottom row consisting only of functional keys
     *  does nothing if not isAlphaOrSymbolKeyboard or assumptions not met
     *  adds an empty row to baseKeys, to have a baseKey row for the bottom functional row
     */
    private fun adjustBottomFunctionalRowAndBaseKeys(allFunctionalKeys: MutableList<MutableList<KeyData>>, baseKeys: MutableList<MutableList<KeyData>>) {
        val functionalKeysBottom = allFunctionalKeys.lastOrNull() ?: return
        if (!params.mId.isAlphaOrSymbolKeyboard || functionalKeysBottom.isEmpty() || functionalKeysBottom.any { it.isKeyPlaceholder() })
            return
        if (!Settings.getInstance().current.mHasCustomFunctionalLayout) {
            // remove keys that should only exist on specific layouts or depend on setting (emoji, numpad, language switch)
            if (!Settings.getInstance().current.mShowsEmojiKey || !params.mId.isAlphabetKeyboard)
                functionalKeysBottom.removeFirst { it.label == KeyLabel.EMOJI }
            if (!Settings.getInstance().current.isLanguageSwitchKeyEnabled || !params.mId.isAlphabetKeyboard)
                functionalKeysBottom.removeFirst { it.label == KeyLabel.LANGUAGE_SWITCH }
            if (params.mId.mElementId != KeyboardId.ELEMENT_SYMBOLS)
                functionalKeysBottom.removeFirst { it.label == KeyLabel.NUMPAD }
        }
        //   replace comma / period if 2 keys in normal bottom row
        if (baseKeys.last().size == 2) {
            functionalKeysBottom.replaceFirst(
                { it.label == KeyLabel.COMMA || it.groupId == KeyData.GROUP_COMMA},
                { baseKeys.last()[0].copy(newGroupId = 1, newType = baseKeys.last()[0].type ?: it.type) }
            )
            functionalKeysBottom.replaceFirst(
                { it.label == KeyLabel.PERIOD || it.groupId == KeyData.GROUP_PERIOD},
                { baseKeys.last()[1].copy(newGroupId = 2, newType = baseKeys.last()[1].type ?: it.type) }
            )
            baseKeys.removeLast()
        }
        // add zwnj key next to space if necessary
        val spaceIndex = functionalKeysBottom.indexOfFirst { it.label == KeyLabel.SPACE && it.width <= 0 } // width could be 0 or -1
        if (spaceIndex >= 0) {
            if (params.mLocaleKeyboardInfos.hasZwnjKey && params.mId.isAlphabetKeyboard) {
                functionalKeysBottom.add(spaceIndex + 1, TextKeyData(label = KeyLabel.ZWNJ))
            }
        }
        baseKeys.add(mutableListOf())
    }

    // ideally we would get all functional keys in a nice list of pairs from the start, but at least it works...
    private fun getFunctionalKeysBySide(functionalKeysFromTop: List<KeyData>, functionalKeysFromBottom: List<KeyData>): Pair<List<KeyParams>, List<KeyParams>> {
        val (functionalKeysFromTopLeft, functionalKeysFromTopRight) = functionalKeysFromTop.splitAt { it.isKeyPlaceholder() }
        val (functionalKeysFromBottomLeft, functionalKeysFromBottomRight) = functionalKeysFromBottom.splitAt { it.isKeyPlaceholder() }
        // functional keys from top rows are the outermost, if there are some in the same row
        functionalKeysFromTopLeft.addAll(functionalKeysFromBottomLeft)
        functionalKeysFromBottomRight.addAll(functionalKeysFromTopRight)
        val functionalKeysLeft = functionalKeysFromTopLeft.map { it.toKeyParams(params) }
        val functionalKeysRight = functionalKeysFromBottomRight.map { it.toKeyParams(params) }
        return functionalKeysLeft to functionalKeysRight
    }

    private fun addNumberRowOrPopupKeys(baseKeys: MutableList<MutableList<KeyData>>) {
        if (!params.mId.mNumberRowEnabled && params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
            // replace first symbols row with number row, but use the labels as popupKeys
            val numberRow = params.mLocaleKeyboardInfos.getNumberRow()
            numberRow.forEachIndexed { index, keyData -> keyData.popup.symbol = baseKeys[0].getOrNull(index)?.label }
            baseKeys[0] = numberRow.toMutableList()
        } else if (!params.mId.mNumberRowEnabled && params.mId.isAlphabetKeyboard && hasNumbersOnTopRow()) {
            if (baseKeys[0].any { it.popup.main != null || !it.popup.relevant.isNullOrEmpty() } // first row of baseKeys has any layout popup key
                && params.mPopupKeyLabelSources.let {
                    val layout = it.indexOf(POPUP_KEYS_LAYOUT)
                    val number = it.indexOf(POPUP_KEYS_NUMBER)
                    layout != -1 && layout < number // layout before number label
                }
            ) {
                // remove number from labels, to avoid awkward mix of numbers and others caused by layout popup keys
                params.mPopupKeyLabelSources.remove(POPUP_KEYS_NUMBER)
            }
            // add number to the first 10 keys in first row
            baseKeys.first().take(10).forEachIndexed { index, keyData -> keyData.popup.numberIndex = index }
            if (baseKeys.first().size < 10) {
                Log.w(TAG, "first row only has ${baseKeys.first().size} keys: ${baseKeys.first().map { it.label }}")
            }
        }
    }

    private fun addSymbolPopupKeys(baseKeys: MutableList<MutableList<KeyData>>) {
        val layoutName = if (params.mId.locale.script() == ScriptUtils.SCRIPT_ARABIC) LAYOUT_SYMBOLS_ARABIC else LAYOUT_SYMBOLS
        val layout = RawKeyboardParser.parseLayout(layoutName, params, context)
        layout.forEachIndexed { i, row ->
            val baseRow = baseKeys.getOrNull(i) ?: return@forEachIndexed
            row.forEachIndexed { j, key ->
                baseRow.getOrNull(j)?.popup?.symbol = key.label
            }
        }
    }

    // some layouts have different number layout, and there we don't want the numbers on the top row
    // todo: actually should not be in here, but in subtype extra values
    private fun hasNumbersOnTopRow() = params.mId.mSubtype.keyboardLayoutSetName !in listOf("pcqwerty", "lao", "thai", "korean_sebeolsik_390", "korean_sebeolsik_final")

    companion object {
        private const val TAG = "KeyboardParser"
    }

}

const val LAYOUT_SYMBOLS = "symbols"
const val LAYOUT_SYMBOLS_SHIFTED = "symbols_shifted"
const val LAYOUT_SYMBOLS_ARABIC = "symbols_arabic"
const val LAYOUT_NUMPAD = "numpad"
const val LAYOUT_NUMPAD_LANDSCAPE = "numpad_landscape"
const val LAYOUT_NUMBER = "number"
const val LAYOUT_PHONE = "phone"
const val LAYOUT_PHONE_SYMBOLS = "phone_symbols"
