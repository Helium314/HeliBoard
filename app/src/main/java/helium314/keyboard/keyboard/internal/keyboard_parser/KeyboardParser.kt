// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Configuration
import helium314.keyboard.latin.utils.Log
import android.view.inputmethod.EditorInfo
import androidx.annotation.StringRes
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.Key.KeyParams
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyLabel
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyType
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.SimplePopups
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.TextKeyData
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.define.DebugFlags
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.spellcheck.AndroidSpellCheckerService
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.InputTypeUtils
import helium314.keyboard.latin.utils.POPUP_KEYS_LAYOUT
import helium314.keyboard.latin.utils.POPUP_KEYS_NUMBER
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.getLayoutFile
import helium314.keyboard.latin.utils.runInLocale
import helium314.keyboard.latin.utils.splitAt
import helium314.keyboard.latin.utils.sumOf
import java.io.File

/**
 * Abstract parser class that handles creation of keyboard from [KeyData] arranged in rows,
 * provided by the extending class.
 *
 * Functional keys are pre-defined and can't be changed, with exception of comma, period and similar
 * keys in symbol layouts.
 * By default, all normal keys have the same width and flags, which may cause issues with the
 * requirements of certain non-latin languages.
 */
abstract class KeyboardParser(private val params: KeyboardParams, private val context: Context) {
    private val infos = layoutInfos(params)
    private val defaultLabelFlags = if (params.mId.isAlphabetKeyboard) {
            params.mLocaleKeyboardInfos.labelFlags
        } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            Key.LABEL_FLAGS_DISABLE_HINT_LABEL // reproduce the no-hints in symbol layouts, todo: add setting
        } else 0

    abstract fun parseCoreLayout(layoutContent: String): MutableList<List<KeyData>>

    // this thing does too much... make it more understandable after everything is implemented
    fun parseLayoutString(layoutContent: String): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)
        params.mProximityCharsCorrectionEnabled = infos.enableProximityCharsCorrection
        params.mAllowRedundantPopupKeys = infos.allowRedundantPopupKeys
        if (infos.touchPositionCorrectionData == null) // need to set correctly, as it's not properly done in readAttributes with attr = null
            params.mTouchPositionCorrection.load(emptyArray())
        else
            params.mTouchPositionCorrection.load(context.resources.getStringArray(infos.touchPositionCorrectionData))

        val baseKeys: MutableList<List<KeyData>> = parseCoreLayout(layoutContent)
        val keysInRows: ArrayList<ArrayList<KeyParams>>
        if (params.mId.isAlphaOrSymbolKeyboard) {
            keysInRows = createAlphaSymbolRows(baseKeys)
        } else if (params.mId.isNumberLayout) {
            keysInRows = createNumericRows(baseKeys)
        } else {
            throw(UnsupportedOperationException("creating KeyboardId ${params.mId.mElementId} not supported"))
        }
        // rescale height if we have more than 4 rows
        val heightRescale = if (keysInRows.size > 4) 4f / keysInRows.size else 1f
        if (heightRescale != 1f) {
            keysInRows.forEach { row -> row.forEach { it.mHeight *= heightRescale } }
        }

        return keysInRows
    }

    // todo
    //  escaping: just start everything with "!"? this should then also be in the label strings in KeyLabel!
    //   and still floris keys should be parsed correctly, so e.g. we would need a space > !space conversion
    //  make the default popups for comma and period appear after the additional popups, not before
    //  make sure the popups work with the different style of getting functional keys!
    //  maybe in this PR, maybe later: numeric rows should also be parsed in this function (might need adjusted layouts)
    //  emoji_com could be replaced with a variation selector
    //  same for the comma key label (hmm, but here the replacement label should go first... and with this change it wouldn't)
    //  replacement of comma and period should have their respective background
    //  does it still work for rtl?
    //  is width ignored when adding to a popup key?
    //  finish documentation, but for that need to actually use the colors
    //  set alternative names for types?
    // todo (when mostly done): test it, compare screenshots with old (after all is done)
    //  check tablet layouts, is the 9% default width necessary, or does it result from the number of keys anyway?
    //   also in landscape!
    //  what happens if we only have top functional keys and then a placeholder?
    //  check danish because of the special key shrink
    //  do comma and period show the correct symbols? see armenian and arabic
    //  check serbian latin because of the functional key shrink
    //  check numeric layouts
    //  check issues with merge popup keys and special labels (relevant for comma and period)
    //  check parsing performance (compare with old, measure time for parseLayoutString)
    //  check whether the mark-as-edge still works
    // todo: later commits
    //  move "/" from bottom row to symbols layout, and remove the weird addition of < and > (when making functional layouts customizable)
    //  parse number layouts same way as normal layouts, requires adjusting the layouts
    //  parse labels that match a toolbar key, also with icon(!)
    //  improve popups of non-action keys with action background

    // this should be ready for customizable functional layouts, but needs cleanup
    private fun getFunctionalKeyLayoutText(): String {
        if (!params.mId.isAlphaOrSymbolKeyboard) throw IllegalStateException("functional key layout only for aloha and symbol layouts")
        val layouts = Settings.getLayoutsDir(context).list() ?: emptyArray()
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            if ("functional_keys_symbols_shifted.json" in layouts)
                return getLayoutFile("functional_keys_symbols_shifted.json", context).readText()
        }
        if (!params.mId.isAlphabetKeyboard) {
            if ("functional_keys_symbols.json" in layouts)
                return getLayoutFile("functional_keys_symbols.json", context).readText()
        }
        if ("functional_keys.json" in layouts)
            return getLayoutFile("functional_keys.json", context).readText()
        val fileName = if (isTablet()) "functional_keys_tablet.json" else "functional_keys.json"
        return context.readAssetsLayoutFile(fileName)
    }

    private fun createAlphaSymbolRows(baseKeys: MutableList<List<KeyData>>): ArrayList<ArrayList<KeyParams>> {
        addNumberRowOrPopupKeys(baseKeys)
        if (params.mId.isAlphabetKeyboard)
            addSymbolPopupKeys(baseKeys)

        val keysInRows = ArrayList<ArrayList<KeyParams>>()
        val functionalKeysTop: List<List<KeyData>>
        val functionalKeysBottom: List<MutableList<KeyData>>
        // getFunctionalKeyLayoutName
        val allFunctionalKeys = JsonKeyboardParser(params, context).parseCoreLayout(getFunctionalKeyLayoutText())

        // todo (later): this sort of special treatment is not nice, but does the job for now
        //  maybe at least move to a separate function
        //  hmm, or just add a placeholder if there is none?
        if (allFunctionalKeys.any { it.singleOrNull()?.type == KeyType.PLACEHOLDER }) { // todo: add width check too, also below
            val a = allFunctionalKeys.splitAt { it.singleOrNull()?.type == KeyType.PLACEHOLDER }
            functionalKeysTop = a.first
            functionalKeysBottom = a.second.map { it.toMutableList() }
        } else {
            functionalKeysBottom = allFunctionalKeys.map { it.toMutableList() }
            functionalKeysTop = emptyList()
        }

        if (params.mLocaleKeyboardInfos.hasZwnjKey && params.mId.isAlphabetKeyboard) {
            // add zwnj key next to space
            val spaceIndex = functionalKeysBottom.last().indexOfFirst { it.label == KeyLabel.SPACE && it.width <= 0 } // 0 or -1
            functionalKeysBottom.last().add(spaceIndex + 1, TextKeyData(label = KeyLabel.ZWNJ))
        }
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
            // add / key next to space, todo (later): not any more, but keep it so this PR can be released without too many people complaining
            val spaceIndex = functionalKeysBottom.last().indexOfFirst { it.label == KeyLabel.SPACE }
            functionalKeysBottom.last().add(spaceIndex + 1, TextKeyData(label = "/", type = KeyType.FUNCTION))
        }
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            // add < and > keys next to space, todo (later): not any more, but keep it so this PR can be released without too many people complaining
            val spaceIndex = functionalKeysBottom.last().indexOfFirst { it.label == KeyLabel.SPACE }
            val key1 = TextKeyData(
                label = "<",
                popup = SimplePopups(listOf("!fixedColumnOrder!3", "‹", "≤", "«")),
                labelFlags = Key.LABEL_FLAGS_HAS_POPUP_HINT,
                type = KeyType.FUNCTION
            )
            val key2 = TextKeyData(
                label = ">",
                popup = SimplePopups(listOf("!fixedColumnOrder!3", "›", "≥", "»")),
                labelFlags = Key.LABEL_FLAGS_HAS_POPUP_HINT,
                type = KeyType.FUNCTION
            )
            functionalKeysBottom.last().add(spaceIndex + 1, key2)
            functionalKeysBottom.last().add(spaceIndex - 1, key1)
        }

        // adjust comma and period keys in bottom row of functionalKeysBottom
        // but also base it on the group, because otherwise changed labels for e.g. dvorak would not be found
        if (baseKeys.last().size == 2) {
            // essentially just replace the key with the specified one, and add a groupId
            for (i in functionalKeysBottom.last().indices) {
                if (functionalKeysBottom.last()[i].label == KeyLabel.COMMA || functionalKeysBottom.last()[i].groupId == KeyData.GROUP_COMMA) {
                    functionalKeysBottom.last()[i] = baseKeys.last()[0].copy(newGroupId = 1)
                } else if (functionalKeysBottom.last()[i].label == KeyLabel.PERIOD || functionalKeysBottom.last()[i].groupId == KeyData.GROUP_PERIOD) {
                    functionalKeysBottom.last()[i] = baseKeys.last()[1].copy(newGroupId = 2)
                }
            }
            baseKeys.removeLast() // todo: always remove? or only if sth was replaced?
        }

        baseKeys.add(emptyList()) // add an empty bottom row so the loop works as expected
        // offset for bottom
        val bottomIndexOffset = baseKeys.size - functionalKeysBottom.size

        // todo: this loop could use some performance improvements (re-check after changes!)
        baseKeys.forEachIndexed { i, it ->
            val row: List<KeyData> = if (i == baseKeys.lastIndex && isTablet()) {
                // add bottom row extra keys
                val tabletExtraKeys = params.mLocaleKeyboardInfos.getTabletExtraKeys(params.mId.mElementId)
                tabletExtraKeys.first + it + tabletExtraKeys.second
            } else {
                it
            }

            // todo (later): is is ugly (but should get the job done correctly)
            //  maybe just move into a separate function?
            // functional keys from top list
            val functionalKeysFromTop = functionalKeysTop.getOrNull(i) ?: emptyList()
            // functional keys from bottom list
            val functionalKeysFromBottom = functionalKeysBottom.getOrNull(i - bottomIndexOffset) ?: emptyList()
            val (functionalKeysFromTopLeft, functionalKeysFromTopRight) = functionalKeysFromTop.splitAt { it.type == KeyType.PLACEHOLDER && it.width == 0f }
            val (functionalKeysFromBottomLeft, functionalKeysFromBottomRight) = functionalKeysFromBottom.splitAt { it.type == KeyType.PLACEHOLDER && it.width == 0f }
            val functionalKeyFilter: (KeyData) -> Boolean = {
                // todo: when removing emoji_com key, emoji should only remove the first emoji key!
                // if (!Settings.getInstance().current.mSingleFunctionalLayout) true else todo: add this setting later when functional key layouts can be customized
                if (it.label == KeyLabel.EMOJI && (!Settings.getInstance().current.mShowsEmojiKey || !params.mId.isAlphabetKeyboard)) false
                else if (it.label == KeyLabel.LANGUAGE_SWITCH && (!Settings.getInstance().current.isLanguageSwitchKeyEnabled || !params.mId.isAlphabetKeyboard)) false
                else if (it.label == KeyLabel.NUMPAD && params.mId.isAlphabetKeyboard) false
                else true
            }
            val functionalKeysLeft = (functionalKeysFromTopLeft + functionalKeysFromBottomLeft).filter(functionalKeyFilter).map { it.processActionAndPeriodKeys().toKeyParams(params) }
            val functionalKeysRight = (functionalKeysFromBottomRight + functionalKeysFromTopRight).filter(functionalKeyFilter).map { it.processActionAndPeriodKeys().toKeyParams(params) }

            val keys = row.map { key ->
                val extraFlags = if (key.label.length > 2 && key.label.codePointCount(0, key.label.length) > 2 && !isEmoji(key.label))
                        Key.LABEL_FLAGS_AUTO_X_SCALE
                    else 0
                val keyData = key.compute(params)
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "adding key ${keyData.label}, ${keyData.code}")
                keyData.toKeyParams(params, defaultLabelFlags or extraFlags)
            }

            // sum up width, excluding -1 elements (but count those!)
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

            // re-scale total width, or add spacers (or do nothing if totalWidth is almost 1)
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
        resizeLastRowIfNecessaryForAlignment(keysInRows)
        if (params.mId.mNumberRowEnabled)
            keysInRows.add(0, getNumberRow())
        return keysInRows
    }

    // this is not nice in here, but otherwise we'd need context for toKeyParams, which might also not be optimal
    private fun KeyData.processActionAndPeriodKeys(): KeyData {
        if (label == KeyLabel.PERIOD) {
            return copy(newLabelFlags = labelFlags or defaultLabelFlags)
        }
        if (label != KeyLabel.ACTION) return this
        return copy(
            newLabel = "${getActionKeyLabel()}|${getActionKeyCode()}",
            newPopup = popup.merge(getActionKeyPopupKeys()?.let { SimplePopups(it) }),
            // the label change is messing with toKeyParams, so we need to supply the appropriate BG type here
            newType = type ?: KeyType.ENTER_EDITING
        )
    }

    private fun addNumberRowOrPopupKeys(baseKeys: MutableList<List<KeyData>>) {
        if (!params.mId.mNumberRowEnabled && params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
            // replace first symbols row with number row, but use the labels as popupKeys
            val numberRow = params.mLocaleKeyboardInfos.getNumberRow()
            numberRow.forEachIndexed { index, keyData -> keyData.popup.symbol = baseKeys[0].getOrNull(index)?.label }
            baseKeys[0] = numberRow
        } else if (!params.mId.mNumberRowEnabled && params.mId.isAlphabetKeyboard && infos.numbersOnTopRow) {
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

    private fun addSymbolPopupKeys(baseKeys: MutableList<List<KeyData>>) {
        val layoutName = getLayoutFileName(params, context, overrideElementId = KeyboardId.ELEMENT_SYMBOLS)
        val layout = if (layoutName.startsWith(CUSTOM_LAYOUT_PREFIX)) {
            val parser = if (layoutName.endsWith("json")) JsonKeyboardParser(params, context)
                else SimpleKeyboardParser(params, context, false)
            parser.parseCoreLayout(getLayoutFile(layoutName, context).readText())
        } else {
            SimpleKeyboardParser(params, context, false).parseCoreLayout(context.readAssetsLayoutFile("$layoutName.txt"))
        }
        layout.forEachIndexed { i, row ->
            val baseRow = baseKeys.getOrNull(i) ?: return@forEachIndexed
            row.forEachIndexed { j, key ->
                baseRow.getOrNull(j)?.popup?.symbol = key.label
            }
        }
    }

    // resize keys in last row if they are wider than keys in the row above
    // this is done so the keys align with the keys above, like in original layouts
    // e.g. for nordic and swiss layouts
    // todo: this will break for users that don't use default key backgrounds!
    private fun resizeLastRowIfNecessaryForAlignment(keysInRows: ArrayList<ArrayList<KeyParams>>) {
        if (keysInRows.size < 3)
            return
        // todo: now last row is not actually the last any more...
        val lastRow = keysInRows[keysInRows.lastIndex - 1]
        val rowAboveLast = keysInRows[keysInRows.lastIndex - 2]
        if (lastRow.any { it.isSpacer } || rowAboveLast.any { it.isSpacer })
            return // annoying to deal with, and probably no resize needed anyway
        val lastNormalRowKeyWidth = lastRow.first { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }.mWidth
        val rowAboveLastNormalRowKeyWidth = rowAboveLast.first { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }.mWidth
        if (lastNormalRowKeyWidth <= rowAboveLastNormalRowKeyWidth + 0.0001f)
            return // no need
        if (lastNormalRowKeyWidth / rowAboveLastNormalRowKeyWidth > 1.1f)
            return // don't resize on large size difference
        if (lastRow.any { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL && it.mWidth != lastNormalRowKeyWidth })
            return // normal keys have different width, don't deal with this
        val numberOfNormalKeys = lastRow.count { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }
        val widthBefore = numberOfNormalKeys * lastNormalRowKeyWidth
        val widthAfter = numberOfNormalKeys * rowAboveLastNormalRowKeyWidth
        val spacerWidth = (widthBefore - widthAfter) / 2
        // resize keys and add spacers
        lastRow.forEach { if (it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL) it.mWidth = rowAboveLastNormalRowKeyWidth }
        lastRow.add(lastRow.indexOfFirst { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }, KeyParams.newSpacer(params, spacerWidth))
        lastRow.add(lastRow.indexOfLast { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL } + 1, KeyParams.newSpacer(params, spacerWidth))
    }

    private fun createNumericRows(baseKeys: MutableList<List<KeyData>>): ArrayList<ArrayList<KeyParams>> {
        val keysInRows = ArrayList<ArrayList<KeyParams>>()
        if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE && params.mId.mElementId != KeyboardId.ELEMENT_NUMPAD) {
            // add padding here instead of using xml (actually this is not good... todo (later))
            params.mLeftPadding = (params.mOccupiedWidth * 0.1f).toInt()
            params.mRightPadding = (params.mOccupiedWidth * 0.1f).toInt()
            params.mBaseWidth = params.mOccupiedWidth - params.mLeftPadding - params.mRightPadding
        }
        baseKeys.forEachIndexed { i, row ->
            val paramsRow = ArrayList<KeyParams>()
            row.forEach { key ->
                var keyParams: KeyParams? = null
                // try parsing a functional key
                // todo: note that this is ignoring code on those keys, if any
                val functionalKeyName = when (key.label) {
                    // todo (later): maybe add special popupKeys for phone and number layouts?
                    "." -> if (params.mId.mElementId == KeyboardId.ELEMENT_NUMPAD) KeyLabel.PERIOD else "."
                    "," -> if (params.mId.mElementId == KeyboardId.ELEMENT_NUMPAD) KeyLabel.COMMA else ","
                    else -> key.label
                }
                if (functionalKeyName.length > 1 && key.type != KeyType.NUMERIC) {
                    try {
                        // todo: this works, but: needs the correct background
                        keyParams = TextKeyData(label = functionalKeyName).processActionAndPeriodKeys().toKeyParams(params)
                    } catch (_: Throwable) {} // just use normal label
                }
                if (keyParams == null) {
                    keyParams = if (key.type == KeyType.NUMERIC) {
                        val labelFlags = when (params.mId.mElementId) {
                            KeyboardId.ELEMENT_PHONE -> Key.LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER or Key.LABEL_FLAGS_HAS_HINT_LABEL or Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO
                            KeyboardId.ELEMENT_PHONE_SYMBOLS -> 0
                            else -> Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO
                        }
                        key.compute(params).toKeyParams(params, labelFlags or defaultLabelFlags)
                    } else if (key.label.length == 1 && (params.mId.mElementId == KeyboardId.ELEMENT_PHONE || params.mId.mElementId == KeyboardId.ELEMENT_NUMBER))
                        key.compute(params).toKeyParams(params, additionalLabelFlags = Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO or defaultLabelFlags)
                    else
                        key.compute(params).toKeyParams(params, additionalLabelFlags = defaultLabelFlags)
                }
                if (key.type != KeyType.NUMERIC && keyParams.mBackgroundType != Key.BACKGROUND_TYPE_ACTION)
                    keyParams.mBackgroundType = Key.BACKGROUND_TYPE_FUNCTIONAL

                if (params.mId.mElementId == KeyboardId.ELEMENT_PHONE && key.popup.main?.getPopupLabel(params)?.length?.let { it > 1 } == true) {
                    keyParams.mPopupKeys = null // the ABC and stuff labels should not create popupKeys
                }
                if (keyParams.mLabel?.length?.let { it > 1 } == true && keyParams.mLabel?.startsWith("!string/") == true) {
                    // resolve string label
                    val id = context.resources.getIdentifier(keyParams.mLabel?.substringAfter("!string/"), "string", context.packageName)
                    if (id != 0)
                        keyParams.mLabel = getInLocale(id)
                }
                paramsRow.add(keyParams)
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "adding key ${keyParams.mLabel}, ${keyParams.mCode}")
            }
            if (i == baseKeys.lastIndex) { // bottom row needs some adjustments
                val n = row.indexOfFirst { it.type == KeyType.NUMERIC }
                if (n != -1) {
                    // make sure the keys next to 0 have normal background
                    paramsRow.getOrNull(n - 1)?.mBackgroundType = Key.BACKGROUND_TYPE_NORMAL
                    paramsRow.getOrNull(n + 1)?.mBackgroundType = Key.BACKGROUND_TYPE_NORMAL

                    // make those keys same width as numeric keys except in numpad layout
                    // but determine from row size instead of from elementId, in case user wants to adjust numpad layout
                    if (row.size == baseKeys[0].size) {
                        paramsRow.getOrNull(n - 1)?.mWidth = paramsRow[n].mWidth
                        paramsRow.getOrNull(n + 1)?.mWidth = paramsRow[n].mWidth
                    } else if (row.size == baseKeys[0].size + 2) {
                        // numpad last row -> make sure the keys next to 0 fit nicely
                        paramsRow.getOrNull(n - 1)?.mWidth = paramsRow[n].mWidth * 0.55f
                        paramsRow.getOrNull(n - 2)?.mWidth = paramsRow[n].mWidth * 0.45f
                        paramsRow.getOrNull(n + 1)?.mWidth = paramsRow[n].mWidth * 0.55f
                        paramsRow.getOrNull(n + 2)?.mWidth = paramsRow[n].mWidth * 0.45f
                    }
                }
            }
            val widthSum = paramsRow.sumOf { it.mWidth }
            paramsRow.forEach { it.mWidth /= widthSum }
            keysInRows.add(paramsRow)
        }
        return keysInRows
    }

    private fun getNumberRow(): ArrayList<KeyParams> =
        params.mLocaleKeyboardInfos.getNumberRow().mapTo(ArrayList()) {
            it.toKeyParams(params, additionalLabelFlags = Key.LABEL_FLAGS_DISABLE_HINT_LABEL or defaultLabelFlags)
        }

    private fun getActionKeyLabel(): String {
        if (params.mId.isMultiLine && (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED))
            return "!icon/enter_key"
        val iconName = when (params.mId.imeAction()) {
            EditorInfo.IME_ACTION_GO -> KeyboardIconsSet.NAME_GO_KEY
            EditorInfo.IME_ACTION_SEARCH -> KeyboardIconsSet.NAME_SEARCH_KEY
            EditorInfo.IME_ACTION_SEND -> KeyboardIconsSet.NAME_SEND_KEY
            EditorInfo.IME_ACTION_NEXT -> KeyboardIconsSet.NAME_NEXT_KEY
            EditorInfo.IME_ACTION_DONE -> KeyboardIconsSet.NAME_DONE_KEY
            EditorInfo.IME_ACTION_PREVIOUS -> KeyboardIconsSet.NAME_PREVIOUS_KEY
            InputTypeUtils.IME_ACTION_CUSTOM_LABEL -> return params.mId.mCustomActionLabel
            else -> return "!icon/enter_key"
        }
        val replacement = iconName.replaceIconWithLabelIfNoDrawable()
        return if (iconName == replacement) // i.e. icon exists
            "!icon/$iconName"
        else
            replacement
    }

    private fun getActionKeyCode() =
        if (params.mId.isMultiLine && (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED))
            "!code/key_shift_enter"
        else "!code/key_enter"

    private fun getActionKeyPopupKeys(): Collection<String>? {
        val action = params.mId.imeAction()
        val navigatePrev = params.mId.navigatePrevious()
        val navigateNext = params.mId.navigateNext()
        return when {
            params.mId.passwordInput() -> when {
                navigatePrev && action == EditorInfo.IME_ACTION_NEXT -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                navigateNext && action == EditorInfo.IME_ACTION_PREVIOUS -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            // could change definition of numbers to query a range, or have a pre-defined list, but not that crucial
            params.mId.isNumberLayout || params.mId.mMode in listOf(KeyboardId.MODE_EMAIL, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME) -> when {
                action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_NEXT)
                navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS)
            action == EditorInfo.IME_ACTION_NEXT -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI)
            action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_NEXT)
            action == EditorInfo.IME_ACTION_PREVIOUS -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI)
            navigateNext && navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS_NEXT)
            navigateNext -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_NEXT)
            navigatePrev -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS)
            else -> createPopupKeys(POPUP_EYS_NAVIGATE_EMOJI)
        }
    }

    private fun createPopupKeys(popupKeysDef: String): List<String> {
        val popupKeys = mutableListOf<String>()
        for (popupKey in popupKeysDef.split(",")) {
            val iconPrefixRemoved = popupKey.substringAfter("!icon/")
            if (iconPrefixRemoved == popupKey) { // i.e. there is no !icon/
                popupKeys.add(popupKey)
                continue
            }
            val iconName = iconPrefixRemoved.substringBefore("|")
            val replacementText = iconName.replaceIconWithLabelIfNoDrawable()
            if (replacementText == iconName) { // i.e. we have the drawable
                popupKeys.add(popupKey)
            } else {
                popupKeys.add(Key.POPUP_KEYS_HAS_LABELS)
                popupKeys.add("$replacementText|${iconPrefixRemoved.substringAfter("|")}")
            }
        }
        // remove emoji shortcut on enter in tablet mode (like original, because bottom row always has an emoji key)
        // (probably not necessary, but whatever)
        if (isTablet() && popupKeys.remove("!icon/emoji_action_key|!code/key_emoji")) {
            val i = popupKeys.indexOfFirst { it.startsWith(Key.POPUP_KEYS_FIXED_COLUMN_ORDER) }
            if (i > -1) {
                val n = popupKeys[i].substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull()
                if (n != null)
                    popupKeys[i] = popupKeys[i].replace(n.toString(), (n - 1).toString())
            }
        }
        return popupKeys
    }

    private fun String.replaceIconWithLabelIfNoDrawable(): String {
        if (params.mIconsSet.getIconDrawable(KeyboardIconsSet.getIconId(this)) != null) return this
        if (params.mId.mWidth == AndroidSpellCheckerService.SPELLCHECKER_DUMMY_KEYBOARD_WIDTH
                && params.mId.mHeight == AndroidSpellCheckerService.SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT
                && !params.mId.mSubtype.hasExtraValue(Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
            )
            // fake keyboard that is used by spell checker (for key coordinates), but not shown to the user
            // often this doesn't have any icons loaded, and there is no need to bother with this
            return this
        val id = context.resources.getIdentifier("label_$this", "string", context.packageName)
        if (id == 0) {
            Log.w(TAG, "no resource for label $this in ${params.mId}")
            return this
        }
        return getInLocale(id)
    }

    private fun getInLocale(@StringRes id: Int): String {
        // todo: hi-Latn strings instead of this workaround?
        val locale = if (params.mId.locale.toLanguageTag() == "hi-Latn") "en_IN".constructLocale()
            else params.mId.locale
        return runInLocale(context, locale) { it.getString(id) }
    }

    private fun isTablet() = Settings.getInstance().isTablet // todo: put it in params?

    companion object {
        private const val TAG = "KeyboardParser"

        // todo: this is somewhat awkward and could be re-organized
        //  simple and json parser should just parse the core layout
        //  adding extra keys should be done in KeyboardParser
        fun parseLayout(params: KeyboardParams, context: Context): ArrayList<ArrayList<KeyParams>> {
            val layoutName = getLayoutFileName(params, context)
            if (layoutName.startsWith(CUSTOM_LAYOUT_PREFIX)) {
                val parser = if (layoutName.endsWith("json")) JsonKeyboardParser(params, context)
                    else SimpleKeyboardParser(params, context)
                return parser.parseLayoutString(getLayoutFile(layoutName, context).readText())
            }
            val layoutFileNames = context.assets.list("layouts")!!
            if (layoutFileNames.contains("$layoutName.json")) {
                return JsonKeyboardParser(params, context).parseLayoutString(context.readAssetsLayoutFile("$layoutName.json"))
            }
            if (layoutFileNames.contains("$layoutName.txt")) {
                return SimpleKeyboardParser(params, context).parseLayoutString(context.readAssetsLayoutFile("$layoutName.txt"))
            }
            throw IllegalStateException("can't parse layout $layoutName with id ${params.mId} and elementId ${params.mId.mElementId}")
        }

        private fun Context.readAssetsLayoutFile(name: String) = assets.open("layouts${File.separator}$name").reader().readText()

        private fun getLayoutFileName(params: KeyboardParams, context: Context, overrideElementId: Int? = null): String {
            var checkForCustom = true
            val layoutName = when (overrideElementId ?: params.mId.mElementId) {
                KeyboardId.ELEMENT_SYMBOLS -> if (params.mId.locale.script() == ScriptUtils.SCRIPT_ARABIC) LAYOUT_SYMBOLS_ARABIC else LAYOUT_SYMBOLS
                KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> LAYOUT_SYMBOLS_SHIFTED
                KeyboardId.ELEMENT_NUMPAD -> if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
                    LAYOUT_NUMPAD_LANDSCAPE
                else
                    LAYOUT_NUMPAD
                KeyboardId.ELEMENT_NUMBER -> LAYOUT_NUMBER
                KeyboardId.ELEMENT_PHONE -> LAYOUT_PHONE
                KeyboardId.ELEMENT_PHONE_SYMBOLS -> LAYOUT_PHONE_SYMBOLS
                else -> {
                    checkForCustom = false // "custom" is already in keyboardLayoutSetName
                    params.mId.mSubtype.keyboardLayoutSetName.substringBeforeLast("+")
                }
            }
            return if (checkForCustom) Settings.readLayoutName(layoutName, context)
            else layoutName
        }

        // todo:
        //  layoutInfos should be stored in method.xml (imeSubtypeExtraValue)
        //  or somewhere else... some replacement for keyboard_layout_set xml maybe
        //  some assets file?
        //  some extended version of locale_key_texts? that would be good, just need to rename the class and file
        // touchPositionCorrectionData is just the resId, needs to be loaded in parser
        //  currently always holo is applied in readAttributes
        private fun layoutInfos(params: KeyboardParams): LayoutInfos {
            val layout = params.mId.mSubtype.keyboardLayoutSetName
            // only for alphabet, but some exceptions for shift layouts
            val enableProximityCharsCorrection = params.mId.isAlphabetKeyboard && when (layout) {
                "bengali_akkhor", "georgian", "hindi", "lao", "nepali_romanized", "nepali_traditional", "sinhala", "thai" ->
                    params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET
                else -> true
            }
            val allowRedundantPopupKeys = params.mId.mElementId != KeyboardId.ELEMENT_SYMBOLS // todo: always set to false?
            // essentially this is default for 4 row and non-alphabet layouts, maybe this could be determined automatically instead of using a list
            // todo: check the difference between default (i.e. none) and holo (test behavior on keyboard)
            val touchPositionCorrectionData = if (params.mId.isAlphabetKeyboard && layout in listOf("armenian_phonetic", "khmer", "lao", "malayalam", "pcqwerty", "thai"))
                    R.array.touch_position_correction_data_default
                else R.array.touch_position_correction_data_holo
            // custom non-json layout for non-uppercase language should not have shift key
            val hasShiftKey = !params.mId.isAlphabetKeyboard
                    || layout !in listOf("hindi_compact", "bengali", "arabic", "arabic_pc", "hebrew", "kannada", "kannada_extended","malayalam", "marathi", "farsi", "tamil", "telugu")
            val numbersOnTopRow = layout !in listOf("pcqwerty", "lao", "thai", "korean_sebeolsik_390", "korean_sebeolsik_final")
            return LayoutInfos(enableProximityCharsCorrection, allowRedundantPopupKeys, touchPositionCorrectionData, hasShiftKey, numbersOnTopRow)
        }
    }

}

// todo: actually this should be in some separate file
data class LayoutInfos(
    // disabled by default, but enabled for all alphabet layouts
    // currently set in keyboardLayoutSet
    val enableProximityCharsCorrection: Boolean = false,
    // previously was false for nordic and serbian_qwertz, true for all others
    val allowRedundantPopupKeys: Boolean = true,
    // there is holo, default and null
    // null only for popupKeys keyboard
    val touchPositionCorrectionData: Int? = null,
    // some layouts do not have a shift key
    val hasShiftKey: Boolean = true,
    // some layouts have different number layout, e.g. thai or korean_sebeolsik
    val numbersOnTopRow: Boolean = true,
)

fun String.rtlLabel(params: KeyboardParams): String {
    if (!params.mId.mSubtype.isRtlSubtype || params.mId.isNumberLayout) return this
    return when (this) {
        "{" -> "{|}"
        "}" -> "}|{"
        "(" -> "(|)"
        ")" -> ")|("
        "[" -> "[|]"
        "]" -> "]|["
        "<" -> "<|>"
        ">" -> ">|<"
        "≤" -> "≤|≥"
        "≥" -> "≥|≤"
        "«" -> "«|»"
        "»" -> "»|«"
        "‹" -> "‹|›"
        "›" -> "›|‹"
        "﴾" -> "﴾|﴿"
        "﴿" -> "﴿|﴾"
        else -> this
    }
}

// could make arrays right away, but they need to be copied anyway as popupKeys arrays are modified when creating KeyParams
private const val POPUP_EYS_NAVIGATE_PREVIOUS = "!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard"
private const val POPUP_EYS_NAVIGATE_NEXT = "!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val POPUP_EYS_NAVIGATE_PREVIOUS_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val POPUP_EYS_NAVIGATE_EMOJI = "!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val POPUP_EYS_NAVIGATE_EMOJI_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
private const val POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS_NEXT = "!fixedColumnOrder!4,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"

const val LAYOUT_SYMBOLS = "symbols"
const val LAYOUT_SYMBOLS_SHIFTED = "symbols_shifted"
const val LAYOUT_SYMBOLS_ARABIC = "symbols_arabic"
const val LAYOUT_NUMPAD = "numpad"
const val LAYOUT_NUMPAD_LANDSCAPE = "numpad_landscape"
const val LAYOUT_NUMBER = "number"
const val LAYOUT_PHONE = "phone"
const val LAYOUT_PHONE_SYMBOLS = "phone_symbols"
