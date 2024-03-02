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
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyType
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.SimplePopups
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.common.LocaleUtils.constructLocale
import helium314.keyboard.latin.common.isEmoji
import helium314.keyboard.latin.common.splitOnWhitespace
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
        if (params.mId.mElementId <= KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
            keysInRows = createAlphaSymbolRows(baseKeys)
        } else if (params.mId.isNumberLayout) {
            keysInRows = createNumericRows(baseKeys)
        } else {
            throw(UnsupportedOperationException("creating KeyboardId ${params.mId.mElementId} not supported"))
        }
        // rescale height if we have more than 4 rows
        val heightRescale = if (keysInRows.size > 4) 4f / keysInRows.size else 1f
        if (heightRescale != 1f) {
            keysInRows.forEach { row -> row.forEach { it.mRelativeHeight *= heightRescale } }
        }

        return keysInRows
    }

    private fun createAlphaSymbolRows(baseKeys: MutableList<List<KeyData>>): ArrayList<ArrayList<KeyParams>> {
        addNumberRowOrPopupKeys(baseKeys)
        if (params.mId.isAlphabetKeyboard)
            addSymbolPopupKeys(baseKeys)
        val bottomRow = getBottomRowAndAdjustBaseKeys(baseKeys) // not really fast, but irrelevant compared to the loop

        val keysInRows = ArrayList<ArrayList<KeyParams>>()
        val functionalKeys = parseFunctionalKeys(R.string.key_def_functional)
        val functionalKeysTop = parseFunctionalKeys(R.string.key_def_functional_top_row)

        // todo: this loop could use some performance improvements
        baseKeys.forEachIndexed { i, it ->
            val row: List<KeyData> = if (i == baseKeys.lastIndex && isTablet()) {
                // add bottom row extra keys
                val tabletExtraKeys = params.mLocaleKeyboardInfos.getTabletExtraKeys(params.mId.mElementId)
                tabletExtraKeys.first + it + tabletExtraKeys.second
            } else {
                it
            }
            // parse functional keys for this row (if any)
            val offset = baseKeys.size - functionalKeys.size
            val functionalKeysDefs = if (i >= offset) functionalKeys[i - offset] // functional keys are aligned to bottom
            else emptyList<String>() to emptyList()
            val outerFunctionalKeyDefs = if (i == 0 && functionalKeysTop.isNotEmpty()) functionalKeysTop.first() // top row
                else emptyList<String>() to emptyList()
            // if we have a top row and top row entries from normal functional key defs, use top row as outer keys
            val functionalKeysLeft = outerFunctionalKeyDefs.first.map { getFunctionalKeyParams(it) } + functionalKeysDefs.first.map { getFunctionalKeyParams(it) }
            val functionalKeysRight = functionalKeysDefs.second.map { getFunctionalKeyParams(it) } + outerFunctionalKeyDefs.second.map { getFunctionalKeyParams(it) }
            val paramsRow = ArrayList<KeyParams>(functionalKeysLeft)

            // determine key width, maybe scale factor for keys, and spacers to add
            val usedKeyWidth = params.mDefaultRelativeKeyWidth * row.size
            val functionalKeyWidth = (functionalKeysLeft.sumOf { it.mRelativeWidth }) + (functionalKeysRight.sumOf { it.mRelativeWidth })
            val availableWidth = 1f - functionalKeyWidth
            var keyWidth: Float
            val spacerWidth: Float
            if (availableWidth - usedKeyWidth > 0.0001f) { // don't add spacers if only a tiny bit is empty
                // width available, add spacer
                keyWidth = params.mDefaultRelativeKeyWidth
                spacerWidth = (availableWidth - usedKeyWidth) / 2
            } else {
                // need more width, re-scale
                spacerWidth = 0f
                keyWidth = availableWidth / row.size
            }
            if (spacerWidth != 0f) {
                paramsRow.add(KeyParams.newSpacer(params, spacerWidth))
            }
            if (keyWidth < params.mDefaultRelativeKeyWidth * 0.82 && spacerWidth == 0f) {
                // keys are very narrow, also rescale the functional keys to make keys a little wider
                // 0.82 is just some guess for "too narrow"
                val allKeyScale = 1f / (functionalKeyWidth + row.size * params.mDefaultRelativeKeyWidth)
                keyWidth = params.mDefaultRelativeKeyWidth * allKeyScale
                functionalKeysLeft.forEach { it.mRelativeWidth *= allKeyScale }
                functionalKeysRight.forEach { it.mRelativeWidth *= allKeyScale }
            }

            for (key in row) {
                val extraFlags = if (key.label.length > 2 && key.label.codePointCount(0, key.label.length) > 2 && !isEmoji(key.label))
                        Key.LABEL_FLAGS_AUTO_X_SCALE
                    else 0
                val keyData = key.compute(params)
                if (DebugFlags.DEBUG_ENABLED)
                    Log.d(TAG, "adding key ${keyData.label}, ${keyData.code}")
                val keyParams = keyData.toKeyParams(params, keyWidth, defaultLabelFlags or extraFlags)
                paramsRow.add(keyParams)
            }
            if (spacerWidth != 0f) {
                paramsRow.add(KeyParams.newSpacer(params, spacerWidth))
            }
            functionalKeysRight.forEach { paramsRow.add(it) }
            keysInRows.add(paramsRow)
        }
        resizeLastRowIfNecessaryForAlignment(keysInRows)
        keysInRows.add(bottomRow)
        if (params.mId.mNumberRowEnabled)
            keysInRows.add(0, getNumberRow())
        return keysInRows
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
            SimpleKeyboardParser(params, context, false).parseCoreLayout(context.readAssetsFile("layouts/$layoutName.txt"))
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
    private fun resizeLastRowIfNecessaryForAlignment(keysInRows: ArrayList<ArrayList<KeyParams>>) {
        if (keysInRows.size < 3)
            return
        val lastRow = keysInRows.last()
        val rowAboveLast = keysInRows[keysInRows.lastIndex - 1]
        if (lastRow.any { it.isSpacer } || rowAboveLast.any { it.isSpacer })
            return // annoying to deal with, and probably no resize needed anyway
        val lastNormalRowKeyWidth = lastRow.first { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }.mRelativeWidth
        val rowAboveLastNormalRowKeyWidth = rowAboveLast.first { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }.mRelativeWidth
        if (lastNormalRowKeyWidth <= rowAboveLastNormalRowKeyWidth + 0.0001f)
            return // no need
        if (lastNormalRowKeyWidth / rowAboveLastNormalRowKeyWidth > 1.1f)
            return // don't resize on large size difference
        if (lastRow.any { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL && it.mRelativeWidth != lastNormalRowKeyWidth })
            return // normal keys have different width, don't deal with this
        val numberOfNormalKeys = lastRow.count { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }
        val widthBefore = numberOfNormalKeys * lastNormalRowKeyWidth
        val widthAfter = numberOfNormalKeys * rowAboveLastNormalRowKeyWidth
        val spacerWidth = (widthBefore - widthAfter) / 2
        // resize keys and add spacers
        lastRow.forEach { if (it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL) it.mRelativeWidth = rowAboveLastNormalRowKeyWidth }
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
                // try parsing a functional key, converting names from florisBoard to what is used here (should be unified)
                // note that this is ignoring code on those keys, if any
                val functionalKeyName = when (key.label) {
                    "view_characters" -> "alpha"
                    "view_symbols" -> "symbol"
                    "enter" -> "action"
                    // todo (later): maybe add special popupKeys for phone and number layouts?
                    "." -> if (params.mId.mElementId == KeyboardId.ELEMENT_NUMPAD) "period" else "."
                    "," -> if (params.mId.mElementId == KeyboardId.ELEMENT_NUMPAD) "comma" else ","
                    else -> key.label
                }
                if (functionalKeyName.length > 1 && key.type != KeyType.NUMERIC) {
                    try {
                        keyParams = getFunctionalKeyParams(functionalKeyName)
                    } catch (_: Throwable) {} // just use normal label
                }
                if (keyParams == null) {
                    keyParams = if (key.type == KeyType.NUMERIC) {
                        val labelFlags = when (params.mId.mElementId) {
                            KeyboardId.ELEMENT_PHONE -> Key.LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER or Key.LABEL_FLAGS_HAS_HINT_LABEL or Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO
                            KeyboardId.ELEMENT_PHONE_SYMBOLS -> 0
                            else -> Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO
                        }
                        key.compute(params).toKeyParams(params, 0.17f, labelFlags or defaultLabelFlags)
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
                        paramsRow.getOrNull(n - 1)?.mRelativeWidth = paramsRow[n].mRelativeWidth
                        paramsRow.getOrNull(n + 1)?.mRelativeWidth = paramsRow[n].mRelativeWidth
                    } else if (row.size == baseKeys[0].size + 2) {
                        // numpad last row -> make sure the keys next to 0 fit nicely
                        paramsRow.getOrNull(n - 1)?.mRelativeWidth = paramsRow[n].mRelativeWidth * 0.55f
                        paramsRow.getOrNull(n - 2)?.mRelativeWidth = paramsRow[n].mRelativeWidth * 0.45f
                        paramsRow.getOrNull(n + 1)?.mRelativeWidth = paramsRow[n].mRelativeWidth * 0.55f
                        paramsRow.getOrNull(n + 2)?.mRelativeWidth = paramsRow[n].mRelativeWidth * 0.45f
                    }
                }
            }
            val widthSum = paramsRow.sumOf { it.mRelativeWidth }
            paramsRow.forEach { it.mRelativeWidth /= widthSum }
            keysInRows.add(paramsRow)
        }
        return keysInRows
    }

    private fun parseFunctionalKeys(@StringRes id: Int): List<Pair<List<String>, List<String>>> =
        context.getString(id).split("\n").mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(";")
            splitFunctionalKeyDefs(p.first()) to splitFunctionalKeyDefs(p.last())
        }

    private fun splitFunctionalKeyDefs(def: String): List<String> {
        if (def.isBlank()) return emptyList()
        return def.split(",").filter { infos.hasShiftKey || !it.trim().startsWith("shift") }
    }

    private fun getBottomRowAndAdjustBaseKeys(baseKeys: MutableList<List<KeyData>>): ArrayList<KeyParams> {
        val adjustableKeyCount = when (params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS -> 3
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> 4
            else -> 2 // must be alphabet, parser doesn't work for other elementIds
        }
        val adjustedKeys = if (baseKeys.last().size == adjustableKeyCount) baseKeys.last()
            else null
        if (adjustedKeys != null)
            baseKeys.removeLast()
        val bottomRow = ArrayList<KeyParams>()
        context.getString(R.string.key_def_bottom_row).split(",").forEach {
            val key = it.trim().splitOnWhitespace().first()
            val adjustKey = when (key) {
                "comma" -> adjustedKeys?.first()
                "period" -> adjustedKeys?.last()
                else -> null
            }
            val keyParams = getFunctionalKeyParams(it, adjustKey?.label, adjustKey?.popup?.getPopupKeyLabels(params))
            if (key == "space") { // add the extra keys around space
                if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
                    bottomRow.add(getFunctionalKeyParams(FunctionalKey.NUMPAD))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(
                        adjustedKeys?.get(1)?.label ?: "/",
                        params,
                        params.mDefaultRelativeKeyWidth,
                        defaultLabelFlags,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.popup
                    ))
                } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
                    bottomRow.add(KeyParams(
                        (adjustedKeys?.get(1)?.label ?: "<").rtlLabel(params),
                        params,
                        params.mDefaultRelativeKeyWidth,
                        defaultLabelFlags or Key.LABEL_FLAGS_HAS_POPUP_HINT,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.popup ?: SimplePopups(listOf("!fixedColumnOrder!3", "‹", "≤", "«"))
                    ))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(
                        (adjustedKeys?.get(2)?.label ?: ">").rtlLabel(params),
                        params,
                        params.mDefaultRelativeKeyWidth,
                        defaultLabelFlags or Key.LABEL_FLAGS_HAS_POPUP_HINT,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(2)?.popup ?: SimplePopups(listOf("!fixedColumnOrder!3", "›", "≥", "»"))
                    ))
                } else { // alphabet
                    if (params.mId.mLanguageSwitchKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.LANGUAGE_SWITCH))
                    if (params.mId.mEmojiKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.EMOJI))
                    bottomRow.add(keyParams)
                    if (params.mLocaleKeyboardInfos.hasZwnjKey)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.ZWNJ))
                }
            } else {
                bottomRow.add(keyParams)
            }
        }
        // set space width
        val space = bottomRow.first { it.mBackgroundType == Key.BACKGROUND_TYPE_SPACEBAR }
        space.mRelativeWidth = 1f - bottomRow.filter { it != space }.sumOf { it.mRelativeWidth }
        return bottomRow
    }

    private fun getNumberRow(): ArrayList<KeyParams> =
        params.mLocaleKeyboardInfos.getNumberRow().mapTo(ArrayList()) {
            it.toKeyParams(params, additionalLabelFlags = Key.LABEL_FLAGS_DISABLE_HINT_LABEL or defaultLabelFlags)
        }

    private fun getFunctionalKeyParams(def: String, label: String? = null, popupKeys: Collection<String>? = null): KeyParams {
        val split = def.trim().splitOnWhitespace()
        val key = FunctionalKey.valueOf(split[0].uppercase())
        val width = if (split.size == 2) split[1].substringBefore("%").toFloat() / 100f
            else params.mDefaultRelativeKeyWidth
        return getFunctionalKeyParams(key, width, label, popupKeys)
    }

    private fun getFunctionalKeyParams(key: FunctionalKey, relativeWidth: Float? = null, label: String? = null, popupKeys: Collection<String>? = null): KeyParams {
        // for comma and period: label will override default, popupKeys will be appended
        val width = relativeWidth ?: params.mDefaultRelativeKeyWidth
        return when (key) {
            FunctionalKey.SYMBOL_ALPHA -> KeyParams(
                if (params.mId.isAlphabetKeyboard) getToSymbolLabel() else params.mLocaleKeyboardInfos.labelAlphabet,
                Constants.CODE_SWITCH_ALPHA_SYMBOL,
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.SYMBOL -> KeyParams(
                getToSymbolLabel(),
                Constants.CODE_SWITCH_SYMBOL,
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.ALPHA -> KeyParams(
                params.mLocaleKeyboardInfos.labelAlphabet,
                Constants.CODE_SWITCH_ALPHA,
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.COMMA -> KeyParams(
                label ?: getCommaLabel(),
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT, // previously only if normal comma, but always is more correct
                if (label?.first()?.isLetter() == true) Key.BACKGROUND_TYPE_NORMAL // mimic behavior of old dvorak and halmak layouts
                    else Key.BACKGROUND_TYPE_FUNCTIONAL,
                SimplePopups(popupKeys?.let { getCommaPopupKeys() + it } ?: getCommaPopupKeys())
            )
            FunctionalKey.PERIOD -> KeyParams(
                label ?: getPeriodLabel(),
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT or defaultLabelFlags,
                if (label?.first()?.isLetter() == true) Key.BACKGROUND_TYPE_NORMAL
                    else Key.BACKGROUND_TYPE_FUNCTIONAL,
                SimplePopups(popupKeys?.let { getPunctuationPopupKeys() + it } ?: getPunctuationPopupKeys())
            )
            FunctionalKey.SPACE -> KeyParams(
                getSpaceLabel(),
                params,
                width, // will not be used for normal space (only in number layouts)
                if (params.mId.isNumberLayout) Key.LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM else 0,
                Key.BACKGROUND_TYPE_SPACEBAR,
                null
            )
            FunctionalKey.ACTION -> KeyParams(
                "${getActionKeyLabel()}|${getActionKeyCode()}",
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE
                        or Key.LABEL_FLAGS_AUTO_X_SCALE
                        or Key.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
                        or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR
                        or Key.LABEL_FLAGS_HAS_POPUP_HINT
                        or KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId),
                Key.BACKGROUND_TYPE_ACTION,
                getActionKeyPopupKeys()?.let { SimplePopups(it) }
            )
            FunctionalKey.DELETE -> KeyParams(
                "!icon/delete_key|!code/key_delete",
                params,
                width,
                0,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.SHIFT -> KeyParams(
                "${getShiftLabel()}|!code/key_shift",
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or if (!params.mId.isAlphabetKeyboard) Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR else 0,
                // todo (later): possibly the whole stickyOn/Off stuff can be removed, currently it should only have a very slight effect in holo
                if (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED)
                    Key.BACKGROUND_TYPE_STICKY_ON
                else Key.BACKGROUND_TYPE_STICKY_OFF,
                if (params.mId.isAlphabetKeyboard) SimplePopups(listOf("!noPanelAutoPopupKey!", " |!code/key_capslock")) else null // why the alphabet popup keys actually?
            )
            FunctionalKey.EMOJI -> KeyParams(
                "!icon/emoji_normal_key|!code/key_emoji",
                params,
                width,
                KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId),
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            // tablet layout has an emoji key that changes to com key in url / mail
            FunctionalKey.EMOJI_COM -> if (params.mId.mMode == KeyboardId.MODE_URL || params.mId.mMode == KeyboardId.MODE_EMAIL)
                        getFunctionalKeyParams(FunctionalKey.COM, width)
                    else getFunctionalKeyParams(FunctionalKey.EMOJI, width)
            FunctionalKey.COM -> KeyParams(
                // todo (later): label and popupKeys could be in localeKeyTexts, handled similar to currency key
                //  better not in the text files, because it should be handled per country
                ".com",
                params,
                width,
                Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                SimplePopups(listOf(Key.POPUP_KEYS_HAS_LABELS, ".net", ".org", ".gov", ".edu"))
            )
            FunctionalKey.LANGUAGE_SWITCH -> KeyParams(
                "!icon/language_switch_key|!code/key_language_switch",
                params,
                width,
                0,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.NUMPAD -> KeyParams(
                "!icon/numpad_key|!code/key_numpad",
                params,
                width,
                0,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.ZWNJ -> KeyParams(
                "!icon/zwnj_key|\u200C",
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT,
                // this may not be a good place to make this choice, but probably it's fine (though reading from settings here is not good)
                if (Settings.getInstance().current.mColors.hasKeyBorders) Key.BACKGROUND_TYPE_SPACEBAR else Key.BACKGROUND_TYPE_NORMAL,
                SimplePopups(listOf("!icon/zwj_key|\u200D"))
            )
        }
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

    private fun getToSymbolLabel() =
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            params.mLocaleKeyboardInfos.labelAlphabet
        else params.mLocaleKeyboardInfos.labelSymbol

    private fun getShiftLabel(): String {
        val elementId = params.mId.mElementId
        if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return params.mLocaleKeyboardInfos.labelSymbol
        if (elementId == KeyboardId.ELEMENT_SYMBOLS)
            return params.mLocaleKeyboardInfos.getShiftSymbolLabel(isTablet())
        if (elementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
            || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
            return "!icon/shift_key_shifted"
        return "!icon/shift_key"
    }

    private fun getPeriodLabel(): String {
        if (params.mId.isNumberLayout) return "."
        if (params.mId.isAlphabetKeyboard || params.mId.locale.language in listOf("ar", "fa")) // todo: this exception is not so great...
            return params.mLocaleKeyboardInfos.labelPeriod
        return "."
    }

    private fun getCommaLabel(): String {
        if (params.mId.mMode == KeyboardId.MODE_URL && params.mId.isAlphabetKeyboard)
            return "/"
        if (params.mId.mMode == KeyboardId.MODE_EMAIL && params.mId.isAlphabetKeyboard)
            return "\\@"
        if (params.mId.isNumberLayout)
            return ","
        return params.mLocaleKeyboardInfos.labelComma
    }

    private fun getCommaPopupKeys(): List<String> {
        val keys = mutableListOf<String>()
        if (!params.mId.mDeviceLocked)
            keys.add("!icon/clipboard_normal_key|!code/key_clipboard")
        if (!params.mId.mEmojiKeyEnabled && !params.mId.isNumberLayout)
            keys.add("!icon/emoji_normal_key|!code/key_emoji")
        if (!params.mId.mLanguageSwitchKeyEnabled)
            keys.add("!icon/language_switch_key|!code/key_language_switch")
        if (!params.mId.mOneHandedModeEnabled)
            keys.add("!icon/start_onehanded_mode_key|!code/key_start_onehanded")
        if (!params.mId.mDeviceLocked)
            keys.add("!icon/settings_key|!code/key_settings")
        return keys
    }

    private fun getPunctuationPopupKeys(): List<String> {
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return listOf("…")
        if (params.mId.isNumberLayout)
            return listOf(":", "…", ";", "∞", "π", "√", "°", "^")
        val popupKeys = params.mLocaleKeyboardInfos.getPopupKeys("punctuation")!!.toMutableList()
        if (params.mId.mSubtype.isRtlSubtype) {
            for (i in popupKeys.indices)
                popupKeys[i] = popupKeys[i].rtlLabel(params) // for parentheses
        }
        if (isTablet() && popupKeys.contains("!") && popupKeys.contains("?")) {
            // remove ! and ? keys and reduce number in autoColumnOrder
            // this makes use of removal of empty popupKeys in PopupKeySpec.insertAdditionalPopupKeys
            popupKeys[popupKeys.indexOf("!")] = ""
            popupKeys[popupKeys.indexOf("?")] = ""
            val columns = popupKeys[0].substringAfter(Key.POPUP_KEYS_AUTO_COLUMN_ORDER).toIntOrNull()
            if (columns != null)
                popupKeys[0] = "${Key.POPUP_KEYS_AUTO_COLUMN_ORDER}${columns - 1}"
        }
        return popupKeys
    }

    private fun getSpaceLabel(): String =
        if (params.mId.mElementId <= KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            "!icon/space_key|!code/key_space"
        else "!icon/space_key_for_number_layout|!code/key_space"

    private fun isTablet() = context.resources.getInteger(R.integer.config_screen_metrics) >= 3

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
                return JsonKeyboardParser(params, context).parseLayoutString(context.readAssetsFile("layouts${File.separator}$layoutName.json"))
            }
            if (layoutFileNames.contains("$layoutName.txt")) {
                return SimpleKeyboardParser(params, context).parseLayoutString(context.readAssetsFile("layouts${File.separator}$layoutName.txt"))
            }
            throw IllegalStateException("can't parse layout $layoutName with id ${params.mId} and elementId ${params.mId.mElementId}")
        }

        private fun Context.readAssetsFile(name: String) = assets.open(name).reader().readText()

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
            val language = params.mId.locale.language
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

    protected enum class FunctionalKey {
        EMOJI, LANGUAGE_SWITCH, COM, EMOJI_COM, ACTION, DELETE, PERIOD, COMMA, SPACE, SHIFT, NUMPAD, SYMBOL, ALPHA, SYMBOL_ALPHA, ZWNJ
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
