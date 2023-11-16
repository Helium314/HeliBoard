// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Resources
import android.util.Log
import android.view.inputmethod.EditorInfo
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils
import org.dslul.openboard.inputmethod.latin.utils.RunInLocale
import org.dslul.openboard.inputmethod.latin.utils.sumOf

/**
 *  Parser for simple layouts like qwerty or symbol, defined only as rows of (normal) keys with moreKeys.
 *  Functional keys are pre-defined and can't be changed, with exception of comma, period and similar
 *  keys in symbol layouts.
 *  There may be a short "extra row" for the configurable keys in the bottom row. This is two keys
 *  for alphabet, 3 keys for symbols and 4 keys for shift symbols. MoreKeys on period and comma get
 *  merged with defaults.
 *  All normal keys have the same width and flags, which likely makes the simple layout definitions
 *  incompatible with the requirements of certain (non-latin) languages. These languages need to use
 *  a different (more configurable) layout definition style, and therefore a different parser.
 *  Also number, phone and numpad layouts are not compatible with this parser.
 */
class SimpleKeyboardParser(private val params: KeyboardParams, private val context: Context) {

    private var addExtraKeys = false
    fun parseFromAssets(layoutName: String): ArrayList<ArrayList<KeyParams>> {
        val layoutFile = when (layoutName) {
            "nordic" -> { addExtraKeys = true; "qwerty" }
            "spanish" -> {
                if (params.mId.locale.language == "eo") "eo" // this behaves a bit different than before, but probably still fine
                else { addExtraKeys = true; "qwerty" }
            }
            "german", "swiss", "serbian_qwertz" -> { addExtraKeys = true; "qwertz" }
            else -> layoutName
        }
        return parse(context.assets.open("layouts/$layoutFile.txt").reader().readText())
    }

    fun parse(layoutContent: String): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)
        val keysInRows = ArrayList<ArrayList<KeyParams>>()

        val baseKeys: MutableList<List<BaseKey>> = parseAdjustablePartOfLayout(layoutContent)
        if (!params.mId.mNumberRowEnabled) {
            // todo (later): not all layouts have numbers on first row, so maybe have some layout flag to switch it off (or an option)
            //  but for latin it's fine, so don't care now
            val newFirstRow = baseKeys.first().mapIndexed { index, baseKey ->
                if (index < numbers.size)
                    BaseKey(baseKey.label, baseKey.moreKeys?.let { arrayOf(numbers[index], *it) })
                else baseKey
            }
            baseKeys[0] = newFirstRow
        }
        val functionalKeysReversed = parseFunctionalKeys().reversed()

        // keyboard parsed bottom-up because the number of rows is not fixed, but the functional keys
        // are always added to the rows near the bottom
        keysInRows.add(getBottomRowAndAdjustBaseKeys(baseKeys))

        baseKeys.reversed().forEachIndexed { i, row ->
            // parse functional keys for this row (if any)
            val functionalKeysDefs = if (i < functionalKeysReversed.size) functionalKeysReversed[i]
                else emptyList<String>() to emptyList()
            val functionalKeysLeft = functionalKeysDefs.first.map { getFunctionalKeyParams(it) }
            val functionalKeysRight = functionalKeysDefs.second.map { getFunctionalKeyParams(it) }
            val paramsRow = ArrayList<KeyParams>(functionalKeysLeft)

            // determine key width, maybe scale factor for keys, and spacers to add
            val usedKeyWidth = params.mDefaultRelativeKeyWidth * row.size
            val availableWidth = 1f - (functionalKeysLeft.sumOf { it.mRelativeWidth }) - (functionalKeysRight.sumOf { it.mRelativeWidth })
            val width: Float
            val spacerWidth: Float
            if (availableWidth - usedKeyWidth > 0.0001f) { // don't add spacers if only a tiny bit is empty
                // width available, add spacer
                width = params.mDefaultRelativeKeyWidth
                spacerWidth = (availableWidth - usedKeyWidth) / 2
            } else {
                // need more width, re-scale
                spacerWidth = 0f
                width = availableWidth / row.size
            }
            if (spacerWidth != 0f) {
                paramsRow.add(KeyParams.newSpacer(params).apply { mRelativeWidth = spacerWidth })
            }

            for (key in row) {
                paramsRow.add(KeyParams(
                    key.label,
                    params,
                    width, // any reasonable way to scale width if there is a long text? might be allowed in user-defined layout
                    0, // todo: maybe autoScale / autoXScale if label has more than 2 characters (exception for emojis?)
                    Key.BACKGROUND_TYPE_NORMAL,
                    key.moreKeys
                ))
            }
            if (spacerWidth != 0f) {
                paramsRow.add(KeyParams.newSpacer(params).apply { mRelativeWidth = spacerWidth })
            }
            functionalKeysRight.forEach { paramsRow.add(it) }
            keysInRows.add(0, paramsRow) // we're doing it backwards, so add on top
        }
        // rescale height if we have more than 4 rows
        val heightRescale = if (keysInRows.size > 4) 4f / keysInRows.size else 1f
        if (params.mId.mNumberRowEnabled)
            keysInRows.add(0, getNumberRow())
        if (heightRescale != 1f)
            // rescale all keys, so number row doesn't look weird (this is done like in current parsing)
            // todo: in symbols view, number row is not rescaled
            //  so the symbols keyboard is higher than the normal one
            //  not a new issue, but should be solved in this migration
            //  how? possibly scale all keyboards to height of main alphabet? (consider suggestion strip)
            keysInRows.forEach { it.forEach { it.mRelativeHeight *= heightRescale } }

        return keysInRows
    }

    private fun parseAdjustablePartOfLayout(layoutContent: String) =
        layoutContent.split("\n\n").mapIndexedTo(mutableListOf()) { i, row ->
            row.split("\n").mapNotNull {
                if (it.isBlank()) return@mapNotNull null
                val split = it.split(" ")
                val moreKeys = if (split.size == 1) {
                    null
                } else if (split.size == 2 && split.last() == "$$$") {
                    // todo (later): could improve handling and show more currency moreKeys
                    if (params.mId.passwordInput())
                        arrayOf("$")
                    else
                        arrayOf(getCurrencyKey(params.mId.locale).first)
                } else {
                    Array(split.size - 1) { split[it + 1] }
                }
                BaseKey(split.first(), moreKeys)
            } + if (addExtraKeys)
                    (params.mLocaleKeyTexts.getExtraKeys(i + 1)?.let { it.map { BaseKey(it.first, it.second) } } ?: emptyList())
                else listOf()
        }

    private fun parseFunctionalKeys(): List<Pair<List<String>, List<String>>> =
        context.getString(R.string.key_def_functional).split("\n").mapNotNull { line ->
            if (line.isBlank()) return@mapNotNull null
            val p = line.split(";")
            p.first().let { if (it.isBlank()) emptyList() else it.split(",") } to
                    p.last().let { if (it.isBlank()) emptyList() else it.split(",") }
        }

    private fun getBottomRowAndAdjustBaseKeys(baseKeys: MutableList<List<BaseKey>>): ArrayList<KeyParams> {
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
            val key = it.trim().split(" ").first()
            val adjustKey = when (key) {
                KEY_COMMA -> adjustedKeys?.first()
                KEY_PERIOD -> adjustedKeys?.last()
                else -> null
            }
            val keyParams = getFunctionalKeyParams(it, adjustKey?.label, adjustKey?.moreKeys)
            if (key == KEY_SPACE) { // add the extra keys around space
                if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
                    bottomRow.add(getFunctionalKeyParams(KEY_NUMPAD))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(
                        adjustedKeys?.get(1)?.label ?: "/",
                        params,
                        params.mDefaultRelativeKeyWidth,
                        0,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.moreKeys
                    ))
                } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
                    bottomRow.add(KeyParams(
                        adjustedKeys?.get(1)?.label ?: "<",
                        params,
                        params.mDefaultRelativeKeyWidth,
                        0,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(1)?.moreKeys
                    ))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(
                        adjustedKeys?.get(2)?.label ?: ">",
                        params,
                        params.mDefaultRelativeKeyWidth,
                        0,
                        Key.BACKGROUND_TYPE_FUNCTIONAL,
                        adjustedKeys?.get(2)?.moreKeys
                    ))
                } else { // alphabet
                    if (params.mId.mLanguageSwitchKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(KEY_LANGUAGE_SWITCH))
                    if (params.mId.mEmojiKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(KEY_EMOJI))
                    bottomRow.add(keyParams)
                    // todo (later): add zwnj if necessary (where to get that info? layout file? then likely will not happen in this parser)
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

    // todo: everything below here likely can and should be shared with the planned parser for more complicated layouts
    //  abstract class?
    //  interface?
    //  utils file?

    private fun getNumberRow(): ArrayList<KeyParams> {
        val row = ArrayList<KeyParams>()
        numbers.forEachIndexed { i, n ->
            row.add(KeyParams(
                n,
                params,
                params.mDefaultRelativeKeyWidth,
                Key.LABEL_FLAGS_DISABLE_HINT_LABEL, // todo (later): maybe optional or enable (but then all numbers should have hints)
                Key.BACKGROUND_TYPE_NORMAL,
                numbersMoreKeys[i] // todo (later, non-latin): language may add some (either alt numbers, or latin numbers if they are replaced above, see number todo)
            ))
        }
        return row
    }

    // for comma and period: label will override default, moreKeys will be appended
    private fun getFunctionalKeyParams(def: String, label: String? = null, moreKeys: Array<String>? = null): KeyParams {
        val split = def.trim().split(" ")
        val key = split[0]
        val width = if (split.size == 2) split[1].substringBefore("%").toFloat() / 100f
            else params.mDefaultRelativeKeyWidth
        return when (key) {
            KEY_SYMBOL -> KeyParams(
                "${getSymbolLabel()}|!code/key_switch_alpha_symbol", // todo (later): in numpad the code is key_symbolNumpad
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            KEY_COMMA -> KeyParams(
                label ?: getDefaultCommaLabel(),
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT, // previously only if normal comma, but always is more correct
                if (label?.first()?.isLetter() == true) Key.BACKGROUND_TYPE_NORMAL else Key.BACKGROUND_TYPE_FUNCTIONAL,
                moreKeys?.let { getCommaMoreKeys() + it } ?: getCommaMoreKeys()
            )
            KEY_SPACE -> KeyParams(
                "!icon/space_key|!code/key_space", // !icon/space_key_for_number_layout in number layout, but not on tablet
                params,
                width, // will not be used for normal space (only in number layouts)
                0, // todo (later): alignIconToBottom for non-tablet number layout
                Key.BACKGROUND_TYPE_SPACEBAR,
                null
            )
            KEY_PERIOD -> KeyParams(
                label ?: ".",
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT, // todo (later): check what LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT does, maybe remove the flag here
                if (label?.first()?.isLetter() == true) Key.BACKGROUND_TYPE_NORMAL else Key.BACKGROUND_TYPE_FUNCTIONAL,
                moreKeys?.let { getPeriodMoreKeys() + it } ?: getPeriodMoreKeys()
            )
            KEY_ACTION -> KeyParams(
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
            KEY_DELETE -> KeyParams(
                "!icon/delete_key|!code/key_delete",
                params,
                width,
                0,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            KEY_SHIFT -> KeyParams(
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
            KEY_EMOJI -> KeyParams(
                "!icon/emoji_normal_key|!code/key_emoji",
                params,
                width,
                KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId),
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            // tablet layout has an emoji key that changes to com key in url / mail
            KEY_EMOJI_COM -> if (params.mId.mMode == KeyboardId.MODE_URL || params.mId.mMode == KeyboardId.MODE_EMAIL)
                    getFunctionalKeyParams(KEY_COM)
                else getFunctionalKeyParams(KEY_EMOJI)
            KEY_COM -> KeyParams(
                ".com", // todo: should depend on language
                params,
                width,
                Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                arrayOf("!hasLabels!", ".net", ".org", ".gov", ".edu") // todo: maybe should be in languageMoreKeys
            )
            KEY_LANGUAGE_SWITCH -> KeyParams(
                "!icon/language_switch_key|!code/key_language_switch",
                params,
                width,
                0,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            KEY_ALPHA -> KeyParams(
                "${getAlphabetLabel()}|!code/key_switch_alpha_symbol", // todo (later): in numpad the code is key_alphaNumpad
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            KEY_NUMPAD -> KeyParams(
                "!icon/numpad_key|!code/key_numpad",
                params,
                width,
                0,
                Key.BACKGROUND_TYPE_FUNCTIONAL,
                null
            )
            KEY_EXCLAMATION -> KeyParams(
                "!",
                params,
                width,
                Key.LABEL_FLAGS_FONT_DEFAULT,
                Key.BACKGROUND_TYPE_NORMAL,
                arrayOf("¡") // todo (later) may depend on language (armenian)
            )
            KEY_QUESTION -> KeyParams(
                "\\?",
                params,
                width,
                Key.LABEL_FLAGS_FONT_DEFAULT,
                Key.BACKGROUND_TYPE_NORMAL,
                arrayOf("¿") // todo (later) may depend on language (armenian)
            )
            else -> throw IllegalArgumentException("unknown key definition \"$key\"")
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
                moreKeys.add("!hasLabels!") // test what it actually does, but it's probably necessary
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
        val keys = mutableListOf("!icon/clipboard_normal_key|!code/key_clipboard")
        if (!params.mId.mEmojiKeyEnabled)
            keys.add("!icon/emoji_normal_key|!code/key_emoji")
        if (!params.mId.mLanguageSwitchKeyEnabled)
            keys.add("!icon/language_switch_key|!code/key_language_switch")
        if (!params.mId.mOneHandedModeEnabled)
            keys.add("!icon/start_onehanded_mode_key|!code/key_start_onehanded")
        keys.add("!icon/settings_key|!code/key_settings")
        return keys.toTypedArray()
    }

    private fun getPeriodMoreKeys(): Array<String> {
        if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return arrayOf("…")
        // todo: language-dependent, also influences the number after autoColumnOrder
        //  there is a weird messup with morekeys_punctuation and morekeys_period
        //  by default, morekeys_period is taken from morekeys_punctuation, but some languages override this
        //  morekeys_period is also changed by some languages
        //  period key always uses morekeys_period, except for dvorak layout which is the only user of morekeys_punctuation
        //  -> clean it up when implementing the language-dependent moreKeys
        return params.mLocaleKeyTexts.getMoreKeys("punctuation") ?:
            arrayOf("!autoColumnOrder!8", "\\,", "?", "!", "#", ")", "(", "/", ";", "'", "@", ":", "-", "\"", "+", "\\%", "&")
    }

}

// class for holding a parsed key of the simple layout
private class BaseKey(
    val label: String,
    val moreKeys: Array<String>? = null,
)

// todo (later): may depend on language for non-latin layouts... or should the number row always be latin?
private val numbers = (1..9).map { it.toString() } + "0"

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

// could use 1 string per key, and make arrays right away
private const val MORE_KEYS_NAVIGATE_PREVIOUS = "!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard"
private const val MORE_KEYS_NAVIGATE_NEXT = "!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_PREVIOUS_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val MORE_KEYS_NAVIGATE_EMOJI = "!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
private const val MORE_KEYS_NAVIGATE_EMOJI_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
private const val MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS_NEXT = "!fixedColumnOrder!4,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"

private const val KEY_EMOJI = "emoji"
private const val KEY_LANGUAGE_SWITCH = "language"
private const val KEY_COM = "com"
private const val KEY_EMOJI_COM = "emoji_com"
private const val KEY_DELETE = "delete"
private const val KEY_ACTION = "action"
private const val KEY_PERIOD = "period"
private const val KEY_COMMA = "comma"
private const val KEY_SPACE = "space"
private const val KEY_SHIFT = "shift"
private const val KEY_NUMPAD = "numpad"
private const val KEY_SYMBOL = "symbol"
private const val KEY_ALPHA = "alphabet"
private const val KEY_QUESTION = "question"
private const val KEY_EXCLAMATION = "exclamation"
