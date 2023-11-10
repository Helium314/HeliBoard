package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.view.inputmethod.EditorInfo
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.Key.KeyParams
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.sumOf
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils

// todo:
//  no plan yet about the format
//  rename, e.g. to SimpleKeyboardParser or JsonKeyboardParser depending on what is done
//  though i guess simple and json may have a lot in common... sort that out later
class NewKeyboardParser(private val params: KeyboardParams, private val context: Context) {

    // todo:
    //  decide scope: will this be for main keyboard style layouts only? i.e. alphabet and symbol?
    //   or should it be capable of creating numpad-style layouts too?
    //  functional keys and bottom row should be merged, and definitions put to some other place (probably resources)
    //  some todos in key.java

    // todo: labelFlags should be set correctly (keep this todo until at least latin layouts are migrated)
    //  alignHintLabelToBottom: on lxx and rounded themes
    //  alignIconToBottom: space_key_for_number_layout
    //  alignLabelOffCenter: number keys in phone layout
    //  fontNormal: turkish (rows 1 and 2 only), .com, emojis, numModeKeyStyle, a bunch of non-latin languages
    //  fontMonoSpace: unused (not really: fontDefault is monospace + normal)
    //  fontDefault: keyExclamationQuestion, a bunch of "normal" keys in fontNormal layouts like thai
    //  followKeyLargeLetterRatio: number keys in number/phone/numpad layouts
    //  followKeyLetterRatio: mode keys in number layouts, some keys in some non-latin layouts
    //  followKeyLabelRatio: enter key, some keys in phone layout (same as followKeyLetterRatio + followKeyLargeLetterRatio)
    //  followKeyHintLabelRatio: unused (but includes some others)
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

    fun parse(): ArrayList<ArrayList<KeyParams>> {
        params.readAttributes(context, null)
        val keysInRows = ArrayList<ArrayList<KeyParams>>()

        // todo: the whole process here is weird, improve it once most things are working
        val baseKeys = keyboardFileContents.split("\n\n").map { row -> row.split("\n").map {
            val split = it.split(" ")
            BaseKey(split.first(), split.drop(1))
        } }.toMutableList()
        if (!params.mId.mNumberRowEnabled) {
            // todo: not all layouts have numbers on first row, so maybe have some layout flag to switch it off (or an option)
            //  but for latin it's fine
            val newFirstRow = baseKeys.first().mapIndexed { index, baseKey ->
                if (index < numbers.size)
                    BaseKey(baseKey.label, listOf(numbers[index]) + baseKey.moreKeys)
                else baseKey
            }
            baseKeys[0] = newFirstRow
        }
        val functionalKeysReversed = functionalKeyDef.split("\n").map { it.split(",") }.reversed()

        if (baseKeys.last().size == 2) {
            keysInRows.add(getBottomRow(spaceRowDef, baseKeys.last()))
            baseKeys.removeLast()
        } else {
            keysInRows.add(getBottomRow(spaceRowDef, null))
        }


        baseKeys.reversed().forEachIndexed { i, row ->  // go from bottom to top, because that's easier to combine with functionalKeys
            // todo: though i could also pad the functional keys list, and add space row in the end... more understandable a maybe a tiny bit more performant, but whatever for now
            val functionalKeysInRow = if (i < functionalKeysReversed.size) functionalKeysReversed[i]
            else listOf("", "")
            val functionalKeyLeft = if (functionalKeysInRow.first().isEmpty()) null
            else getFunctionalKeyParams(functionalKeysInRow.first())
            val functionalKeyRight = if (functionalKeysInRow.last().isEmpty()) null
            else getFunctionalKeyParams(functionalKeysInRow.last())
            val paramsRow = ArrayList<KeyParams>()

            // determine key width, maybe scale factor for keys, and spacers to add
            val usedKeyWidth = params.mDefaultRelativeKeyWidth * row.size
            val availableWidth = 1f - (functionalKeyLeft?.mRelativeWidth ?: 0f) - (functionalKeyRight?.mRelativeWidth ?: 0f)
            functionalKeyLeft?.let { paramsRow.add(it) }
            val width: Float
            val spacerWidth: Float
            if (availableWidth - usedKeyWidth > 0.001f) {
                // width available, add spacer
                width = params.mDefaultRelativeKeyWidth
                spacerWidth = (availableWidth - usedKeyWidth) / 2
            } else {
                // need more width, re-scale (may leave width essentially unchanged...)
                spacerWidth = 0f
                width = availableWidth / row.size
            }
            if (spacerWidth != 0f) {
                paramsRow.add(Key.KeyParams.newSpacer(params).apply { mRelativeWidth = spacerWidth })
            }
            // some checks?
            //  last row should have 2 keys, then it will replace the comma keys
            //  may also have 0, or be omitted (or not?)
            //   how to check for omission?
            //  only allow 4 rows max, and the 2 key bottom row?
            for (key in row) {
                paramsRow.add(KeyParams(
                    key.label,
                    params,
                    width, // any reasonable way to scale width if there is a long text? might be allowed in user-defined layout
                    0, // todo: maybe autoScale / autoXScale if label has more than 2 characters (exception for emojis?)
                    Key.BACKGROUND_TYPE_NORMAL,
                    key.moreKeys.toTypedArray()
                ))
            }
            if (spacerWidth != 0f) {
                paramsRow.add(KeyParams.newSpacer(params).apply { mRelativeWidth = spacerWidth })
            }
            functionalKeyRight?.let { paramsRow.add(it) }
            keysInRows.add(0, paramsRow) // we're doing it backwards, so add on top
        }
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

    private fun getNumberRow(): ArrayList<KeyParams> {
        val row = ArrayList<KeyParams>()
        numbers.forEachIndexed { i, n ->
            row.add(KeyParams(
                n,
                params,
                params.mDefaultRelativeKeyWidth,
                Key.LABEL_FLAGS_DISABLE_HINT_LABEL, // todo: maybe optional?
                Key.BACKGROUND_TYPE_NORMAL,
                numbersMoreKeys[i] // todo (non-latin): language may add some (either alt numbers, or latin numbers if they are replaced above)
            ))
        }
        return row
    }

    // todo: bottomBaseKeys for symbol and shift-symbol?
    //  here more flexibility should be allowed, e.g. only having 1 or 2 keys, but a longer space bar
    private fun getBottomRow(def: String, bottomBaseKeys: List<BaseKey>?): ArrayList<KeyParams> {
        if (bottomBaseKeys != null && bottomBaseKeys.size != 2)
            throw IllegalArgumentException("need exactly two bottomBaseKeys")
        val commaLabel = bottomBaseKeys?.first()?.label
        val periodLabel = bottomBaseKeys?.last()?.label
        val bottomRow = ArrayList<KeyParams>()
        def.split(",").forEach {
            val key = it.trim().split(" ").first()
            val label = when (key) {
                KEY_COMMA -> commaLabel
                KEY_PERIOD -> periodLabel
                else -> null
            }
            val keyParams = getFunctionalKeyParams(it, label)
            if (key == KEY_SPACE) { // add the extra keys around space
                if (params.mId.isAlphabetKeyboard) {
                    if (params.mId.mLanguageSwitchKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(KEY_LANGUAGE_SWITCH))
                    if (params.mId.mEmojiKeyEnabled)
                        bottomRow.add(getFunctionalKeyParams(KEY_EMOJI))
                    bottomRow.add(keyParams)
                    // todo (later): add zwnj if necessary (where to get that info? layout file?)
                } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS) {
                    bottomRow.add(getFunctionalKeyParams(KEY_NUMPAD))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams("/", params, params.mDefaultRelativeKeyWidth, 0, Key.BACKGROUND_TYPE_FUNCTIONAL, null))
                } else if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
                    bottomRow.add(KeyParams("<", params, params.mDefaultRelativeKeyWidth, 0, Key.BACKGROUND_TYPE_FUNCTIONAL, null))
                    bottomRow.add(keyParams)
                    bottomRow.add(KeyParams(">", params, params.mDefaultRelativeKeyWidth, 0, Key.BACKGROUND_TYPE_FUNCTIONAL, null))
                } else { // number layouts always have a normal space key
                    bottomRow.add(keyParams)
                }
            } else {
                bottomRow.add(keyParams)
            }
        }
        // set space width on symbol and alphabet keyboards
        if (params.mId.mElementId < KeyboardId.ELEMENT_PHONE) {
            val space = bottomRow.first { it.mBackgroundType == Key.BACKGROUND_TYPE_SPACEBAR } // could be optimized
            space.mRelativeWidth = 1f - bottomRow.filter { it != space }.sumOf { it.mRelativeWidth }
        }
        return bottomRow
    }

    // label will override default for comma and period
    private fun getFunctionalKeyParams(def: String, label: String? = null): KeyParams {
        val split = def.trim().split(" ")
        val key = split[0]
        val width = if (split.size == 2) split[1].substringBefore("%").toFloat() / 100f
            else params.mDefaultRelativeKeyWidth
        return when (key) {
            KEY_SYMBOL -> KeyParams(
                "${getSymbolLabel()}|!code/key_switch_alpha_symbol", // todo: for some reason in numpad the code is key_symbolNumpad -> is this necessary?
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
                getCommaMoreKeys()
            )
            KEY_SPACE -> KeyParams(
                "!icon/space_key|!code/key_space", // !icon/space_key_for_number_layout in number layout, but not on tablet
                params,
                width, // will not be used for normal space (only in number layouts)
                0, // todo: alignIconToBottom for non-tablet number layout
                Key.BACKGROUND_TYPE_SPACEBAR,
                null
            )
            KEY_PERIOD -> KeyParams(
                label ?: ".",
                params,
                width,
                Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT, // todo: check what LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT does
                if (label?.first()?.isLetter() == true) Key.BACKGROUND_TYPE_NORMAL else Key.BACKGROUND_TYPE_FUNCTIONAL,
                getPeriodMoreKeys()
            )
            KEY_ACTION -> KeyParams(
                "${getActionKeyLabel()}|${getActionKeyCode()}",
                params,
                width,
                Key.LABEL_FLAGS_PRESERVE_CASE
                    or Key.LABEL_FLAGS_AUTO_X_SCALE
                    or Key.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
                    or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR
                    // todo: theme-dependent stuff should probably go somewhere into KeyboardTheme, maybe sth like getThemeActionKeyFlags(themeId)
                    or if (params.mThemeId == KeyboardTheme.THEME_ID_LXX_BASE || params.mThemeId == KeyboardTheme.THEME_ID_ROUNDED_BASE) Key.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO else 0,
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
                Key.BACKGROUND_TYPE_FUNCTIONAL, // todo: stickyOn, stickyOff -> check if it's still used, possibly in holo (maybe it's already removed anyway, then fully remove it)
                arrayOf("!noPanelAutoMoreKey!", " |!code/key_capslock")
            )
            KEY_EMOJI -> KeyParams(
                "!icon/emoji_normal_key|!code/key_emoji",
                params,
                width,
                // todo: see action key comment
                if (params.mThemeId == KeyboardTheme.THEME_ID_LXX_BASE || params.mThemeId == KeyboardTheme.THEME_ID_ROUNDED_BASE) Key.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO else 0,
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
                "${getAlphabetLabel()}|!code/key_switch_alpha_symbol", // todo: for some reason in numpad the code is key_alphaNumpad -> is this necessary?
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
            else -> throw IllegalArgumentException("unknown key definition $key")
        }
    }

    private fun getActionKeyLabel(): String {
        if (params.mId.isMultiLine && (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED))
            return "!icon/enter_key"
        // could be more concise, but better not get resource by name
        return when (params.mId.imeAction()) {
            EditorInfo.IME_ACTION_GO -> { if (hasIcon(KeyboardIconsSet.NAME_GO_KEY))
                    "!icon/go_key"
                else context.getString(R.string.label_go_key)
            }
            EditorInfo.IME_ACTION_SEARCH -> { if (hasIcon(KeyboardIconsSet.NAME_SEARCH_KEY))
                    "!icon/search_key"
                else context.getString(R.string.label_search_key)
            }
            EditorInfo.IME_ACTION_SEND -> { if (hasIcon(KeyboardIconsSet.NAME_SEND_KEY))
                    "!icon/send_key"
                else context.getString(R.string.label_send_key)
            }
            EditorInfo.IME_ACTION_NEXT -> { if (hasIcon(KeyboardIconsSet.NAME_NEXT_KEY))
                    "!icon/next_key"
                else context.getString(R.string.label_next_key)
            }
            EditorInfo.IME_ACTION_DONE -> { if (hasIcon(KeyboardIconsSet.NAME_DONE_KEY))
                    "!icon/done_key"
                else context.getString(R.string.label_done_key)
            }
            EditorInfo.IME_ACTION_PREVIOUS -> { if (hasIcon(KeyboardIconsSet.NAME_PREVIOUS_KEY))
                    "!icon/previous_key"
                else context.getString(R.string.label_previous_key)
            }
            InputTypeUtils.IME_ACTION_CUSTOM_LABEL -> params.mId.mCustomActionLabel
            else -> "!icon/enter_key"
        }
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
                navigatePrev && action == EditorInfo.IME_ACTION_NEXT -> moreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                navigateNext && action == EditorInfo.IME_ACTION_PREVIOUS -> moreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> moreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            // could change definition of numbers to query a range, or have a pre-defined list, but not that crucial
            params.mId.mMode in listOf(KeyboardId.MODE_URL, KeyboardId.MODE_EMAIL, KeyboardId.ELEMENT_PHONE, KeyboardId.ELEMENT_NUMBER, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME) -> when {
                action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                action == EditorInfo.IME_ACTION_NEXT -> null
                action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> moreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                action == EditorInfo.IME_ACTION_PREVIOUS -> null
                navigateNext && navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS_NEXT)
                navigateNext -> moreKeysArray(MORE_KEYS_NAVIGATE_NEXT)
                navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_PREVIOUS)
                else -> null
            }
            action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS)
            action == EditorInfo.IME_ACTION_NEXT -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI)
            action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_NEXT)
            action == EditorInfo.IME_ACTION_PREVIOUS -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI)
            navigateNext && navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS_NEXT)
            navigateNext -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_NEXT)
            navigatePrev -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI_PREVIOUS)
            else -> moreKeysArray(MORE_KEYS_NAVIGATE_EMOJI)
        }
    }

    private fun moreKeysArray(def: String): Array<String> {
        // this is ... not so great, should be optimized
        return def.split(",").flatMap {
            if (it.contains("!icon/")) {
                it.replaceIconWithLabel("!hasLabels!,").split(",")
            } else {
                listOf(it)
            }
        }.toTypedArray()
    }

    // could it also be used for getActionKeyLabel?
    private fun String.replaceIconWithLabel(replacementPrefix: String): String {
        val iconName = substringAfter("!icon/").substringBefore("|")
        if (hasIcon(iconName))
            return this
        val id = context.resources.getIdentifier("label_$iconName", "string", context.packageName)
        return replacementPrefix + context.getString(id)
    }

    private fun hasIcon(iconName: String) = params.mIconsSet.getIconDrawable(KeyboardIconsSet.getIconId(iconName)) != null

    // todo: may depend on language
    private fun getAlphabetLabel(): String {
        return "ABC"
    }

    // todo: may depend on language
    private fun getSymbolLabel(): String {
        return "\\?123"
    }

    private fun getShiftLabel(): String {
        val elementId = params.mId.mElementId
        if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
            return "=\\<" // todo: may depend on language
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
        return arrayOf("!autoColumnOrder!8", "\\,", "?", "!", "#", ")", "(", "/", ";", "'", "@", ":", "-", "\"", "+", "\\%", "&")
    }

}

// for now at least, maybe adjust or remove yet another temporary class
private data class BaseKey(
    val label: String,
    val moreKeys: List<String> = emptyList(), // for now, maybe also array if necessary (language dependent moreKeys are determined later in key creation
)

private val numbers = (1..9).map { it.toString() } + "0" // todo: may depend on language for non-latin layouts... or should the number row always be latin?

/** moreKeys for numbers, same order as [numbers] */
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

// remove later, this is just one idea how it could be defined in a very simple way
// alternative very simple way:
//q w e r t y u i o p
//a s d f g h j k l
//z x c v b n m
// and repeat for the symbols
// looks better, but only allows a single symbol
// idea: allow setting layout depending on orientation?
// idea: allow some special symbol for currency key, like $$?
private val keyboardFileContents = """q %
w \
e |
r =
t [
y ]
u < ü
i >
o { ö
p }

a @ ä
s # ß
d $ €
f _ 😜
g &
h -
j +
k (
l )

z *
x "
c '
v :
b ;
n !
m ?"""

// possible simple string definition of functional keys
// possibly should be in resources because it depends on screen size
private val functionalKeyDef = """,
,
shift 15%, delete 15%"""

private val functionalKeyDefTablet = """, delete 10%
, action 10%
shift 10%, shift""" // todo: in tablet layout, exclamation and question keys are in the last row on the right (all layouts, or just latin?)

private val spaceRowDef = "symbol 15%, comma, space, period, action 15%" // in web, the comma gets replaced with slash, but that should just affect the key code, not the position
private val spaceRowDefTablet = "symbol, comma, space, period, com_emoji"
private val spaceRowDefSymbol = "alphabet, comma, numpad, space, slash, period, action" // when shift, numpad and slash are replaced with < and >

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
