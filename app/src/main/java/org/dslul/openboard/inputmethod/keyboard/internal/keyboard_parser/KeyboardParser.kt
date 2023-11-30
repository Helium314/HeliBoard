package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.KeyData
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.toTextKey
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants
import org.dslul.openboard.inputmethod.latin.common.splitOnWhitespace
import org.dslul.openboard.inputmethod.latin.define.DebugFlags
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils
import org.dslul.openboard.inputmethod.latin.utils.RunInLocale
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils
import org.dslul.openboard.inputmethod.latin.utils.sumOf

/**
 * Abstract parser class that handles creation of keyboard from [KeyData] arranged in rows,
 * provided by the extending class.
 *
 * Functional keys are pre-defined and can't be changed, with exception of comma, period and similar
 * keys in symbol layouts.
 * By default, all normal keys have the same width and flags, which may cause issues with the
 * requirements of certain non-latin languages. todo: add labelFlags to Json parser, or determine automatically?
 *
 * Currently the number, phone and numpad layouts are not compatible with this parser.
 */
abstract class KeyboardParser(private val params: KeyboardParams, private val context: Context) {
    private val infos = layoutInfos(params)
    private val defaultLabelFlags = infos.defaultLabelFlags or if (!params.mId.isAlphabetKeyboard)
            Key.LABEL_FLAGS_DISABLE_HINT_LABEL // reproduce the no-hints in symbol layouts, todo: add setting
        else 0

    protected abstract fun getLayoutFromAssets(layoutName: String): String

    protected abstract fun parseCoreLayout(layoutContent: String): MutableList<List<KeyData>>

    fun parseLayoutFromAssets(layoutName: String): ArrayList<ArrayList<KeyParams>> =
        parseLayoutString(getLayoutFromAssets(layoutName))

    fun parseLayoutString(layoutContent: String): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)
        if (infos.touchPositionCorrectionData == null) // need to set correctly, as it's not properly done in readAttributes with attr = null
            params.mTouchPositionCorrection.load(emptyArray())
        else
            params.mTouchPositionCorrection.load(context.resources.getStringArray(infos.touchPositionCorrectionData))

        val keysInRows = ArrayList<ArrayList<KeyParams>>()
        val baseKeys: MutableList<List<KeyData>> = parseCoreLayout(layoutContent)
        if (!params.mId.mNumberRowEnabled && params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
            // replace first symbols row with number row
            baseKeys[0] = params.mLocaleKeyTexts.getNumberRow()
        } else if (!params.mId.mNumberRowEnabled && params.mId.isAlphabetKeyboard && params.mId.locale.language != "ko") {
            // add number to the first 10 keys in first row
            // setting the correct moreKeys is handled in PopupSet
            // not for korean layouts, todo: should be decided in the layout, not in the parser
            baseKeys.first().take(10).forEachIndexed { index, keyData -> keyData.popup.numberIndex = index }
        }
        val functionalKeysReversed = parseFunctionalKeys().reversed()

        // keyboard parsed bottom-up because the number of rows is not fixed, but the functional keys
        // are always added to the rows near the bottom
        keysInRows.add(getBottomRowAndAdjustBaseKeys(baseKeys))

        baseKeys.reversed().forEachIndexed { i, it ->
            val row: List<KeyData> = if (i == 0) {
                // add bottom row extra keys
                it + context.getString(R.string.key_def_extra_bottom_right)
                    .split(",").mapNotNull { if (it.isBlank()) null else it.trim().toTextKey() }
            } else {
                it
            }
            // parse functional keys for this row (if any)
            val functionalKeysDefs = if (i < functionalKeysReversed.size) functionalKeysReversed[i]
                else emptyList<String>() to emptyList()
            val functionalKeysLeft = functionalKeysDefs.first.map { getFunctionalKeyParams(it) }
            val functionalKeysRight = functionalKeysDefs.second.map { getFunctionalKeyParams(it) }
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
                // todo (idea): works reasonably well, but actually functional keys could give some more of their width
                val allKeyScale = 1f / (functionalKeyWidth + row.size * params.mDefaultRelativeKeyWidth)
                keyWidth = params.mDefaultRelativeKeyWidth * allKeyScale
                functionalKeysLeft.forEach { it.mRelativeWidth *= allKeyScale }
                functionalKeysRight.forEach { it.mRelativeWidth *= allKeyScale }
            }

            for (key in row) {
                // todo: maybe autoScale / autoXScale if label has more than 2 characters (exception for emojis?)
                //  but that could also be determined in toKeyParams
                val keyParams = key.compute(params).toKeyParams(params, keyWidth, defaultLabelFlags)
                paramsRow.add(keyParams)
            }
            if (spacerWidth != 0f) {
                paramsRow.add(KeyParams.newSpacer(params, spacerWidth))
            }
            functionalKeysRight.forEach { paramsRow.add(it) }
            keysInRows.add(0, paramsRow) // we're doing it backwards, so add on top
        }
        resizeLastNormalRowIfNecessaryForAlignment(keysInRows)
        // rescale height if we have more than 4 rows
        val heightRescale = if (keysInRows.size > 4) 4f / keysInRows.size else 1f
        if (params.mId.mNumberRowEnabled)
            keysInRows.add(0, getNumberRow())
        if (heightRescale != 1f) {
            // rescale all keys, so number row doesn't look weird (this is done like in current parsing)
            // todo: in symbols view, number row is not rescaled
            //  so the symbols keyboard is higher than the normal one
            //  not a new issue, but should be solved in this migration
            //  how? possibly scale all keyboards to height of main alphabet? (consider suggestion strip)
            keysInRows.forEach { key -> key.forEach { it.mRelativeHeight *= heightRescale } }
        }

        return keysInRows
    }

    // resize keys in last row if they are wider than keys in the row above
    // this is done so the keys align with the keys above, like in original layouts
    // done e.g. for nordic and swiss layouts
    private fun resizeLastNormalRowIfNecessaryForAlignment(keysInRows: ArrayList<ArrayList<KeyParams>>) {
        if (keysInRows.size < 3)
            return
        val lastNormalRow = keysInRows[keysInRows.lastIndex - 1]
        val rowAboveLastNormalRow = keysInRows[keysInRows.lastIndex - 2]
        if (lastNormalRow.any { it.isSpacer } || rowAboveLastNormalRow.any { it.isSpacer })
            return // annoying to deal with, and probably no resize needed anyway
        val lastNormalRowKeyWidth = lastNormalRow.first { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }.mRelativeWidth
        val rowAboveLastNormalRowKeyWidth = rowAboveLastNormalRow.first { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }.mRelativeWidth
        if (lastNormalRowKeyWidth <= rowAboveLastNormalRowKeyWidth + 0.0001f)
            return // no need
        if (lastNormalRow.any { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL && it.mRelativeWidth != lastNormalRowKeyWidth })
            return // normal keys have different width, don't deal with this
        val numberOfNormalKeys = lastNormalRow.count { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }
        val widthBefore = numberOfNormalKeys * lastNormalRowKeyWidth
        val widthAfter = numberOfNormalKeys * rowAboveLastNormalRowKeyWidth
        val spacerWidth = (widthBefore - widthAfter) / 2
        // resize keys and add spacers
        lastNormalRow.forEach { if (it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL) it.mRelativeWidth = rowAboveLastNormalRowKeyWidth }
        lastNormalRow.add(lastNormalRow.indexOfFirst { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL }, KeyParams.newSpacer(params, spacerWidth))
        lastNormalRow.add(lastNormalRow.indexOfLast { it.mBackgroundType == Key.BACKGROUND_TYPE_NORMAL } + 1, KeyParams.newSpacer(params, spacerWidth))
    }

    private fun parseFunctionalKeys(): List<Pair<List<String>, List<String>>> =
        context.getString(R.string.key_def_functional).split("\n").mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(";")
            splitFunctionalKeyDefs(p.first()) to splitFunctionalKeyDefs(p.last())
        }

    private fun splitFunctionalKeyDefs(def: String): List<String> {
        if (def.isBlank()) return emptyList()
        return def.split(",").filter { infos.hasShiftKey || !it.startsWith("shift") }
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
            val keyParams = getFunctionalKeyParams(it, adjustKey?.label, adjustKey?.popup?.toMoreKeys(params))
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
                        adjustedKeys?.get(1)?.popup?.toMoreKeys(params)
                    ))
                } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
                    bottomRow.add(KeyParams(
                        (adjustedKeys?.get(1)?.label ?: "<").rtlLabel(params),
                        params,
                        params.mDefaultRelativeKeyWidth,
                        defaultLabelFlags,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.popup?.toMoreKeys(params)
                    ))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(
                        (adjustedKeys?.get(2)?.label ?: ">").rtlLabel(params),
                        params,
                        params.mDefaultRelativeKeyWidth,
                        defaultLabelFlags,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(2)?.popup?.toMoreKeys(params)
                    ))
                } else { // alphabet
                    if (params.mId.mLanguageSwitchKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.LANGUAGE_SWITCH))
                    if (params.mId.mEmojiKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.EMOJI))
                    bottomRow.add(keyParams)
                    if (infos.hasZwnjKey)
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
        params.mLocaleKeyTexts.getNumberRow().mapTo(ArrayList()) {
            it.toKeyParams(params, additionalLabelFlags = Key.LABEL_FLAGS_DISABLE_HINT_LABEL or defaultLabelFlags)
        }

    private fun getFunctionalKeyParams(def: String, label: String? = null, moreKeys: Array<String>? = null): KeyParams {
        val split = def.trim().splitOnWhitespace()
        val key = FunctionalKey.valueOf(split[0].uppercase())
        val width = if (split.size == 2) split[1].substringBefore("%").toFloat() / 100f
            else params.mDefaultRelativeKeyWidth
        return getFunctionalKeyParams(key, width, label, moreKeys)
    }

    private fun getFunctionalKeyParams(key: FunctionalKey, relativeWidth: Float? = null, label: String? = null, moreKeys: Array<String>? = null): KeyParams {
        // for comma and period: label will override default, moreKeys will be appended
        val width = relativeWidth ?: params.mDefaultRelativeKeyWidth
        return when (key) {
            FunctionalKey.SYMBOL -> KeyParams(
                getToSymbolLabel(),
                getToSymbolCode(),
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.ALPHA -> KeyParams(
                params.mLocaleKeyTexts.labelAlphabet,
                getToAlphaCode(),
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
                moreKeys?.let { getCommaMoreKeys() + it } ?: getCommaMoreKeys()
            )
            FunctionalKey.PERIOD -> KeyParams(
                label ?: params.mLocaleKeyTexts.labelPeriod,
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT
                        // todo (later): check what LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT does, maybe remove the flag here
                        or if (params.mId.isAlphabetKeyboard) Key.LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT else 0
                        or defaultLabelFlags,
                if (label?.first()?.isLetter() == true) Key.BACKGROUND_TYPE_NORMAL
                    else Key.BACKGROUND_TYPE_FUNCTIONAL,
                moreKeys?.let { getPunctuationMoreKeys() + it } ?: getPunctuationMoreKeys()
            )
            FunctionalKey.SPACE -> KeyParams(
                "!icon/space_key|!code/key_space", // !icon/space_key_for_number_layout in number layout, but not on tablet
                params,
                width, // will not be used for normal space (only in number layouts)
                0, // todo (later): alignIconToBottom for non-tablet number layout -> check what it does / whether it's necessary
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
                getActionKeyMoreKeys()
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
                // todo (later): possibly the whole stickOn/Off stuff can be removed, currently it should only have a very slight effect in holo
                if (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED)
                    Key.BACKGROUND_TYPE_STICKY_ON
                else Key.BACKGROUND_TYPE_STICKY_OFF,
                arrayOf("!noPanelAutoMoreKey!", " |!code/key_capslock")
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
                // todo (later): label and moreKeys could be in localeKeyTexts, handled like currency key
                //  better not in the text files, because it should be handled in a more fine grained way
                ".com",
                params,
                width,
                Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                arrayOf("!hasLabels!", ".net", ".org", ".gov", ".edu")
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
                Key.BACKGROUND_TYPE_SPACEBAR,
                arrayOf("!icon/zwj_key|\u200D")
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

    private fun getActionKeyMoreKeys(): Array<String>? {
        val action = params.mId.imeAction()
        val navigatePrev = params.mId.navigatePrevious()
        val navigateNext = params.mId.navigateNext()
        return when {
            params.mId.passwordInput() -> when {
                navigatePrev && action == EditorInfo.IME_ACTION_NEXT -> createMoreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                navigateNext && action == EditorInfo.IME_ACTION_PREVIOUS -> createMoreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> createMoreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            // could change definition of numbers to query a range, or have a pre-defined list, but not that crucial
            params.mId.mMode in listOf(KeyboardId.MODE_URL, KeyboardId.MODE_EMAIL, KeyboardId.ELEMENT_PHONE, KeyboardId.ELEMENT_NUMBER, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME) -> when {
                action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> createMoreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> createMoreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS)
            action == EditorInfo.IME_ACTION_NEXT -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI)
            action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_NEXT)
            action == EditorInfo.IME_ACTION_PREVIOUS -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI)
            navigateNext && navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS_NEXT)
            navigateNext -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_NEXT)
            navigatePrev -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS)
            else -> createMoreKeysArray(MORE_KEYS_NAVIGATE_EMOJI)
        }
    }

    private fun createMoreKeysArray(moreKeysDef: String): Array<String> {
        val moreKeys = mutableListOf<String>()
        for (moreKey in moreKeysDef.split(",")) {
            val iconPrefixRemoved = moreKey.substringAfter("!icon/")
            if (iconPrefixRemoved == moreKey) { // i.e. there is no !icon/
                moreKeys.add(moreKey)
                continue
            }
            val iconName = iconPrefixRemoved.substringBefore("|")
            val replacementText = iconName.replaceIconWithLabelIfNoDrawable()
            if (replacementText == iconName) { // i.e. we have the drawable
                moreKeys.add(moreKey)
            } else {
                moreKeys.add("!hasLabels!")
                moreKeys.add(replacementText)
            }
        }
        return moreKeys.toTypedArray()
    }

    private fun String.replaceIconWithLabelIfNoDrawable(): String {
        if (params.mIconsSet.getIconDrawable(KeyboardIconsSet.getIconId(this)) != null) return this
        val id = context.resources.getIdentifier("label_$this", "string", context.packageName)
        if (id == 0) {
            val message = "no resource for label $this in ${params.mId}"
            Log.w(this::class.simpleName, message)
            if (DebugFlags.DEBUG_ENABLED)
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
            return this
        }
        val ril = object : RunInLocale<String>() { // todo (later): simpler way of doing this in a single line?
            override fun job(res: Resources) = res.getString(id)
        }
        return ril.runInLocale(context.resources, params.mId.locale)
    }

    private fun getToSymbolLabel() =
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            params.mLocaleKeyTexts.labelAlphabet
        else params.mLocaleKeyTexts.labelSymbol

    private fun getToSymbolCode() =
        if (params.mId.mElementId == KeyboardId.ELEMENT_NUMPAD)
            Constants.CODE_SYMBOL_FROM_NUMPAD
        else Constants.CODE_SWITCH_ALPHA_SYMBOL

    private fun getToAlphaCode() =
        if (params.mId.mElementId == KeyboardId.ELEMENT_NUMPAD)
            Constants.CODE_ALPHA_FROM_NUMPAD
        else Constants.CODE_SWITCH_ALPHA_SYMBOL

    private fun getShiftLabel(): String {
        val elementId = params.mId.mElementId
        if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return params.mLocaleKeyTexts.labelSymbol
        if (elementId == KeyboardId.ELEMENT_SYMBOLS)
            return params.mLocaleKeyTexts.labelShiftSymbol
        if (elementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
            || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
            return "!icon/shift_key_shifted"
        return "!icon/shift_key"
    }

    private fun getCommaLabel(): String {
        if (params.mId.mMode == KeyboardId.MODE_URL && params.mId.isAlphabetKeyboard)
            return "/"
        if (params.mId.mMode == KeyboardId.MODE_EMAIL && params.mId.isAlphabetKeyboard)
            return "\\@"
        return params.mLocaleKeyTexts.labelComma
    }

    private fun getCommaMoreKeys(): Array<String> {
        val keys = mutableListOf<String>()
        if (!params.mId.mDeviceLocked)
            keys.add("!icon/clipboard_normal_key|!code/key_clipboard")
        if (!params.mId.mEmojiKeyEnabled)
            keys.add("!icon/emoji_normal_key|!code/key_emoji")
        if (!params.mId.mLanguageSwitchKeyEnabled)
            keys.add("!icon/language_switch_key|!code/key_language_switch")
        if (!params.mId.mOneHandedModeEnabled)
            keys.add("!icon/start_onehanded_mode_key|!code/key_start_onehanded")
        if (!params.mId.mDeviceLocked)
            keys.add("!icon/settings_key|!code/key_settings")
        return keys.toTypedArray()
    }

    private fun getPunctuationMoreKeys(): Array<String> {
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return arrayOf("…")
        val moreKeys = params.mLocaleKeyTexts.getMoreKeys("punctuation") ?:
            arrayOf("${Key.MORE_KEYS_AUTO_COLUMN_ORDER}8", "\\,", "?", "!", "#", ")", "(", "/", ";", "'", "@", ":", "-", "\"", "+", "\\%", "&")
        if (context.resources.getInteger(R.integer.config_screen_metrics) >= 3 && moreKeys.contains("!") && moreKeys.contains("?")) {
            // we have a tablet, remove ! and ? keys and reduce number in autoColumnOrder
            // this makes use of removal of empty moreKeys in MoreKeySpec.insertAdditionalMoreKeys
            moreKeys[moreKeys.indexOf("!")] = ""
            moreKeys[moreKeys.indexOf("?")] = ""
            val columns = moreKeys[0].substringAfter(Key.MORE_KEYS_AUTO_COLUMN_ORDER).toIntOrNull()
            if (columns != null)
                moreKeys[0] = "${Key.MORE_KEYS_AUTO_COLUMN_ORDER}${columns - 1}"
        }
        return moreKeys
    }

    companion object {
        fun parseFromAssets(params: KeyboardParams, context: Context): ArrayList<ArrayList<KeyParams>>? {
            val id = params.mId
            val layoutName = params.mId.mSubtype.keyboardLayoutSetName
            val layoutFileNames = context.assets.list("layouts") ?: return null
            return when {
                id.mElementId == KeyboardId.ELEMENT_SYMBOLS && ScriptUtils.getScriptFromSpellCheckerLocale(params.mId.locale) == ScriptUtils.SCRIPT_ARABIC
                    -> SimpleKeyboardParser(params, context).parseLayoutFromAssets("symbols_arabic")
                id.mElementId == KeyboardId.ELEMENT_SYMBOLS -> SimpleKeyboardParser(params, context).parseLayoutFromAssets("symbols")
                id.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED
                    -> SimpleKeyboardParser(params, context).parseLayoutFromAssets("symbols_shifted")
                !id.isAlphabetKeyboard -> null
                layoutFileNames.contains("$layoutName.json") -> JsonKeyboardParser(params, context).parseLayoutFromAssets(layoutName)
                layoutFileNames.contains("${getSimpleLayoutName(layoutName, params)}.txt")
                    -> SimpleKeyboardParser(params, context).parseLayoutFromAssets(layoutName)
                else -> null
            }
        }

        @JvmStatic // unsupported without JvmStatic
        // todo: should be removed in the end (after removing old parser), and the internal layout names changed for easier finding
        //  currently it's spread out everywhere... method.xml, locale_and_extra_value_to_keyboard_layout_set_map, getKeyboardLayoutNameForLocale, ...
        protected fun getSimpleLayoutName(layoutName: String, params: KeyboardParams) = when (layoutName) {
                "swiss", "german", "serbian_qwertz" -> "qwertz"
                "nordic", "spanish" -> if (params.mId.locale.language == "eo") "eo" else "qwerty"
                "south_slavic", "east_slavic" -> params.mId.locale.language // layouts are split per language now, much less convoluted
                else -> layoutName
            }

        // todo: layoutInfos should be stored in method.xml (imeSubtypeExtraValue)
        //  or somewhere else... some replacement for keyboard_layout_set xml maybe
        //  move it after old parser is removed
        // currently only labelFlags are used
        // touchPositionCorrectionData needs to be loaded, currently always holo is applied in readAttributes
        private fun layoutInfos(params: KeyboardParams): LayoutInfos {
            val name = params.mId.mSubtype.keyboardLayoutSetName
            val labelFlags = if (!params.mId.isAlphabetKeyboard) 0 else when (name) {
                "armenian_phonetic", "arabic", "arabic_pc", "bengali", "bengali_akkhor", "bengali_unijoy",
                "farsi", "hindi", "hindi_compact", "lao", "marathi", "nepali_romanized", "nepali_traditional",
                "thai", "urdu" -> Key.LABEL_FLAGS_FONT_NORMAL
                "kannada", "khmer", "malayalam", "sinhala", "tamil", "telugu" -> Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_AUTO_X_SCALE
                else -> 0
            }
            // only for alphabet, but some exceptions for shift layouts
            val enableProximityCharsCorrection = params.mId.isAlphabetKeyboard && when (name) {
                // todo: test effect on correction (just add qwerty to the list for testing)
                "akkhor", "georgian", "hindi", "lao", "nepali_romanized", "nepali_traditional", "sinhala", "thai" ->
                    params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET
                else -> true
            }
            val allowRedundantMoreKeys = name != "nordic" && name != "serbian_qwertz"
            // essentially this is default for 4 row and non-alphabet layouts, maybe this could be determined automatically instead of using a list
            // todo: check the difference between default (i.e. none) and holo (test behavior on keyboard)
            // todo: null for MoreKeysKeyboard only
            val touchPositionCorrectionData = if (params.mId.isAlphabetKeyboard && name in listOf("armenian_phonetic", "khmer", "lao", "malayalam", "pcqwerty", "thai"))
                    R.array.touch_position_correction_data_default
                else R.array.touch_position_correction_data_holo
            val hasZwnjKey = params.mId.locale.language in listOf("fa", "ne", "kn", "te") // determine from language, user might have custom layout
            val hasShiftKey = name !in listOf("hindi_compact", "bengali", "arabic", "arabic_pc", "hebrew", "kannada", "malayalam", "marathi", "farsi", "tamil", "telugu")
            return LayoutInfos(labelFlags, enableProximityCharsCorrection, allowRedundantMoreKeys, touchPositionCorrectionData, hasZwnjKey, hasShiftKey)
        }
    }

    protected enum class FunctionalKey {
        EMOJI, LANGUAGE_SWITCH, COM, EMOJI_COM, ACTION, DELETE, PERIOD, COMMA, SPACE, SHIFT, NUMPAD, SYMBOL, ALPHA, ZWNJ
    }

}

data class LayoutInfos(
    val defaultLabelFlags: Int = 0,
    // disabled by default, but enabled for all alphabet layouts
    // currently set in keyboardLayoutSet
    val enableProximityCharsCorrection: Boolean = false,
    val allowRedundantMoreKeys: Boolean = true, // only false for nordic and serbian_qwertz
    // there is holo, default and null
    // null only for moreKeys keyboard
    // currently read as part of readAttributes, and thus wrong with the new parser
    val touchPositionCorrectionData: Int? = null,
    val hasZwnjKey: Boolean = false,
    val hasShiftKey: Boolean = true,
)

fun String.rtlLabel(params: KeyboardParams): String {
    if (!params.mId.mSubtype.isRtlSubtype) return this
    return when (this) {
        "{" -> "{|}"
        "}" -> "}|{"
        "(" -> "(|)"
        ")" -> ")|("
        "[" -> "{|]"
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

// could make arrays right away, but they need to be copied anyway as moreKeys arrays are modified when creating KeyParams
private const val MORE_KEYS_NAVIGATE_PREVIOUS = "!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard"
private const val MORE_KEYS_NAVIGATE_NEXT = "!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_PREVIOUS_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val MORE_KEYS_NAVIGATE_EMOJI = "!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val MORE_KEYS_NAVIGATE_EMOJI_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS_NEXT = "!fixedColumnOrder!4,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
