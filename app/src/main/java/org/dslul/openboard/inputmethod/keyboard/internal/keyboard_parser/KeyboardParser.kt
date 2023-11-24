package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Resources
import android.view.inputmethod.EditorInfo
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.KeyData
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.toTextKey
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.splitOnWhitespace
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils
import org.dslul.openboard.inputmethod.latin.utils.RunInLocale
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

    protected abstract fun getLayoutFromAssets(layoutName: String): String

    protected abstract fun parseCoreLayout(layoutContent: String): MutableList<List<KeyData>>

    fun parseLayoutFromAssets(layoutName: String): ArrayList<ArrayList<KeyParams>> =
        parseLayoutString(getLayoutFromAssets(layoutName))

    fun parseLayoutString(layoutContent: String): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)
        val keysInRows = ArrayList<ArrayList<KeyParams>>()

        val baseKeys: MutableList<List<KeyData>> = parseCoreLayout(layoutContent)
        if (!params.mId.mNumberRowEnabled) {
            // todo (non-latin): not all layouts have numbers on first row, so maybe have some layout flag to switch it off (or an option)
            ((1..9) + 0).forEachIndexed { i, n -> baseKeys.first().getOrNull(i)?.popup?.number = n }
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
                val keyParams = key.compute(params)?.toKeyParams(params, keyWidth) ?: continue
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
            keysInRows.forEach { it.forEach { it.mRelativeHeight *= heightRescale } }
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
            p.first().let { if (it.isBlank()) emptyList() else it.split(",") } to
                    p.last().let { if (it.isBlank()) emptyList() else it.split(",") }
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
                        0,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.popup?.toMoreKeys(params)
                    ))
                } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
                    bottomRow.add(KeyParams(
                        adjustedKeys?.get(1)?.label ?: "<",
                        params,
                        params.mDefaultRelativeKeyWidth,
                        0,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.popup?.toMoreKeys(params)
                    ))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(
                        adjustedKeys?.get(2)?.label ?: ">",
                        params,
                        params.mDefaultRelativeKeyWidth,
                        0,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(2)?.popup?.toMoreKeys(params)
                    ))
                } else { // alphabet
                    if (params.mId.mLanguageSwitchKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.LANGUAGE_SWITCH))
                    if (params.mId.mEmojiKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.EMOJI))
                    bottomRow.add(keyParams)
                    if (params.mId.locale.language in languagesThatNeedZwnjKey)
                        bottomRow.add(getFunctionalKeyParams(FunctionalKey.ZWNJ)) // todo (non-latin): test it
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

    private fun getNumberRow(): ArrayList<KeyParams> {
        val row = ArrayList<KeyParams>()
        ((1..9) + 0).forEachIndexed { i, n ->
            row.add(KeyParams(
                    n.toString(), // todo (non-latin): use language more keys to adjust, possibly in combination with some setting
                    params,
                    params.mDefaultRelativeKeyWidth,
                    Key.LABEL_FLAGS_DISABLE_HINT_LABEL, // todo (later): maybe optional or enable (but then all numbers should have moreKeys)
                    Key.BACKGROUND_TYPE_NORMAL,
                    numbersMoreKeys[i] // todo (non-latin): alternative numbers should be in language more keys, which to put where needs to be decided
            ))
        }
        return row
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
                "${getSymbolLabel()}|!code/key_switch_alpha_symbol", // todo (later): in numpad the code is key_symbolNumpad
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            FunctionalKey.COMMA -> KeyParams(
                label ?: getDefaultCommaLabel(),
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT, // previously only if normal comma, but always is more correct
                if (label?.first()
                        ?.isLetter() == true
                ) Key.BACKGROUND_TYPE_NORMAL else Key.BACKGROUND_TYPE_FUNCTIONAL,
                moreKeys?.let { getCommaMoreKeys() + it } ?: getCommaMoreKeys()
            )
            FunctionalKey.SPACE -> KeyParams(
                "!icon/space_key|!code/key_space", // !icon/space_key_for_number_layout in number layout, but not on tablet
                params,
                width, // will not be used for normal space (only in number layouts)
                0, // todo (later): alignIconToBottom for non-tablet number layout
                Key.BACKGROUND_TYPE_SPACEBAR,
                null
            )
            FunctionalKey.PERIOD -> KeyParams(
                label ?: ".",
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT, // todo (later): check what LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT does, maybe remove the flag here
                if (label?.first()
                        ?.isLetter() == true
                ) Key.BACKGROUND_TYPE_NORMAL else Key.BACKGROUND_TYPE_FUNCTIONAL,
                moreKeys?.let { getPunctuationMoreKeys() + it } ?: getPunctuationMoreKeys()
            )
            FunctionalKey.ACTION -> KeyParams(
                "${getActionKeyLabel()}|${getActionKeyCode()}",
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE
                        or Key.LABEL_FLAGS_AUTO_X_SCALE
                        or Key.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
                        or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR
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
                Key.LABEL_FLAGS_PRESERVE_CASE,
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
            FunctionalKey.COM -> KeyParams( // todo: label and moreKeys could be in localeKeyTexts, handled like currency key
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
            FunctionalKey.ALPHA -> KeyParams(
                "${getAlphabetLabel()}|!code/key_switch_alpha_symbol", // todo (later): in numpad the code is key_alphaNumpad
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
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
        val ril = object : RunInLocale<String>() { // todo (later): simpler way of doing this in a single line?
            override fun job(res: Resources) = res.getString(id)
        }
        return ril.runInLocale(context.resources, params.mId.locale)
    }

    private fun getAlphabetLabel() = params.mLocaleKeyTexts.labelAlphabet

    private fun getSymbolLabel() = params.mLocaleKeyTexts.labelSymbols

    private fun getShiftLabel(): String {
        val elementId = params.mId.mElementId
        if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return params.mLocaleKeyTexts.labelShiftSymbols
        if (elementId == KeyboardId.ELEMENT_SYMBOLS)
            return getSymbolLabel()
        if (elementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || elementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED
            || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED || elementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED)
            return "!icon/shift_key_shifted"
        return "!icon/shift_key"
    }

    private fun getDefaultCommaLabel(): String {
        if (params.mId.mMode == KeyboardId.MODE_URL)
            return "/"
        if (params.mId.mMode == KeyboardId.MODE_EMAIL)
            return "\\@"
        return ","
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
        // todo: some (non-latin) languages have different parenthesis keys (maybe rtl has inverted?)
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
        fun createParserForLayout(params: KeyboardParams, context: Context): KeyboardParser? {
            val layoutName = params.mId.mSubtype.keyboardLayoutSetName
            val layoutFileNames = context.assets.list("layouts") ?: return null
            if (layoutFileNames.contains("$layoutName.json"))
                return JsonKeyboardParser(params, context)
            val simpleLayoutName = getSimpleLayoutName(layoutName)
            if (layoutFileNames.contains("$simpleLayoutName.txt"))
                return SimpleKeyboardParser(params, context)
            return null
        }

        @JvmStatic // unsupported without JvmStatic
        protected fun getSimpleLayoutName(layoutName: String)= when (layoutName) {
                "swiss", "german", "serbian_qwertz" -> "qwertz"
                "nordic", "spanish" -> "qwerty"
                else -> layoutName
            }
    }

    protected enum class FunctionalKey {
        EMOJI, LANGUAGE_SWITCH, COM, EMOJI_COM, ACTION, DELETE, PERIOD, COMMA, SPACE, SHIFT, NUMPAD, SYMBOL, ALPHA, ZWNJ
    }

}
// moreKeys for numbers, order is 1-9 and then 0
// todo (later): like numbers, for non-latin layouts this depends on language and therefore should not be in the parser
private val numbersMoreKeys = arrayOf(
    arrayOf("¹", "½", "⅓","¼", "⅛"),
    arrayOf("²", "⅔"),
    arrayOf("³", "¾", "⅜"),
    arrayOf("⁴"),
    arrayOf("⅝"),
    null,
    arrayOf("⅞"),
    null,
    null,
    arrayOf("ⁿ", "∅"),
)

// could make arrays right away, but they need to be copied anyway as moreKeys arrays are modified when creating KeyParams
private const val MORE_KEYS_NAVIGATE_PREVIOUS = "!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard"
private const val MORE_KEYS_NAVIGATE_NEXT = "!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_PREVIOUS_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val MORE_KEYS_NAVIGATE_EMOJI = "!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val MORE_KEYS_NAVIGATE_EMOJI_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS_NEXT = "!fixedColumnOrder!4,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"

// farsi|kannada|nepali_romanized|nepali_traditional|telugu"
private val languagesThatNeedZwnjKey = listOf("fa", "ne", "kn", "te")
