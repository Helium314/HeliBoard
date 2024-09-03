/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package com.oscar.aikeyboard.keyboard.internal.keyboard_parser.floris

import android.view.inputmethod.EditorInfo
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import com.oscar.aikeyboard.keyboard.Key
import com.oscar.aikeyboard.keyboard.KeyboardId
import com.oscar.aikeyboard.keyboard.KeyboardTheme
import com.oscar.aikeyboard.keyboard.internal.KeyboardIconsSet
import com.oscar.aikeyboard.keyboard.internal.KeyboardParams
import com.oscar.aikeyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import com.oscar.aikeyboard.keyboard.internal.keyboard_parser.floris.KeyLabel.convertFlorisLabel
import com.oscar.aikeyboard.keyboard.internal.keyboard_parser.floris.KeyLabel.rtlLabel
import com.oscar.aikeyboard.latin.RichInputMethodManager
import com.oscar.aikeyboard.latin.common.Constants
import com.oscar.aikeyboard.latin.common.LocaleUtils.constructLocale
import com.oscar.aikeyboard.latin.common.StringUtils
import com.oscar.aikeyboard.latin.settings.Settings
import com.oscar.aikeyboard.latin.spellcheck.AndroidSpellCheckerService
import com.oscar.aikeyboard.latin.utils.InputTypeUtils
import com.oscar.aikeyboard.latin.utils.Log
import com.oscar.aikeyboard.latin.utils.ToolbarKey
import com.oscar.aikeyboard.latin.utils.getCodeForToolbarKey
import com.oscar.aikeyboard.latin.utils.toolbarKeyStrings
import java.util.Locale

// taken from FlorisBoard, small modifications (see also KeyData)
//  internal keys removed (currently no plan to support them)
//  added String.toTextKey
//  currency key handling (see todo below...)

/**
 * Interface describing a basic key which can carry a character, an emoji, a special function etc. while being as
 * abstract as possible.
 *
 * @property type The type of the key.
 * @property code The Unicode code point of this key, or a special code from [KeyCode].
 * @property label The label of the key. This should always be a representative string for [code].
 * @property groupId The group which this key belongs to (currently only allows [GROUP_DEFAULT]).
 * @property popup The popups for ths key. Can also dynamically be provided via popup extensions.
 */
sealed interface KeyData : AbstractKeyData {
    val type: KeyType?
    val code: Int
    val label: String
    val groupId: Int
    val popup: PopupSet<out AbstractKeyData> // not nullable because can't add number otherwise
    val width: Float // in percent of keyboard width, 0 is default (depends on key), -1 is fill (like space bar)
    val labelFlags: Int

    fun copy(newType: KeyType? = type, newCode: Int = code, newLabel: String = label, newGroupId: Int = groupId,
             newPopup: PopupSet<out AbstractKeyData> = popup, newWidth: Float = width, newLabelFlags: Int = labelFlags): KeyData

    // groups (currently) not supported
    companion object {
        /**
         * Constant for the default group. If not otherwise specified, any key is automatically
         * assigned to this group.
         */
        const val GROUP_DEFAULT: Int = 0

        /**
         * Constant for the Left modifier key group. Any key belonging to this group will get the
         * popups specified for the comma key.
         */
        const val GROUP_COMMA: Int = 1

        /**
         * Constant for the right modifier key group. Any key belonging to this group will get the
         * popups specified for the period key.
         */
        const val GROUP_PERIOD: Int = 2

        /**
         * Constant for the enter modifier key group. Any key belonging to this group will get the
         * popups specified for "~enter" in the popup mapping.
         */
        const val GROUP_ENTER: Int = 3

        /**
         * Constant for the enter modifier key group. Any key belonging to this group will get the
         * popups specified for "~kana" in the popup mapping.
         */
        const val GROUP_KANA: Int = 97

        private fun getShiftLabel(params: KeyboardParams) = when (params.mId.mElementId) {
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> params.mLocaleKeyboardInfos.labelSymbol
            KeyboardId.ELEMENT_SYMBOLS -> params.mLocaleKeyboardInfos.getShiftSymbolLabel(Settings.getInstance().isTablet)
            KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> "!icon/${KeyboardIconsSet.NAME_SHIFT_KEY_SHIFTED}"
            KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> "!icon/${KeyboardIconsSet.NAME_SHIFT_KEY_LOCKED}"

            else -> "!icon/${KeyboardIconsSet.NAME_SHIFT_KEY}"
        }

        // todo (later): try avoiding this weirdness
        //  maybe just remove it and if users want it they can use custom functional layouts?
        //  but it has been like this "forever" and actually seems to make sense
        private fun getPeriodLabel(params: KeyboardParams): String {
            if (params.mId.isNumberLayout) return "."
            if (params.mId.isAlphabetKeyboard || params.mId.locale.language in listOf("ar", "fa"))
                return params.mLocaleKeyboardInfos.labelPeriod
            return "."
        }

        private fun getSpaceLabel(params: KeyboardParams): String =
            if (params.mId.isAlphaOrSymbolKeyboard || params.mId.isEmojiClipBottomRow)
                "!icon/space_key|!code/key_space"
            else "!icon/space_key_for_number_layout|!code/key_space"

        // todo: emoji and language switch popups should actually disappear depending on current layout (including functional keys)
        //  keys could be replaced with toolbar keys, but parsing needs to be adjusted (should happen anyway...)
        private fun getCommaPopupKeys(params: KeyboardParams): List<String> {
            val keys = mutableListOf<String>()
            if (!params.mId.mDeviceLocked)
                keys.add("!icon/clipboard_normal_key|!code/key_clipboard")
            if (!params.mId.mEmojiKeyEnabled && !params.mId.isNumberLayout)
                keys.add("!icon/emoji_normal_key|!code/key_emoji")
            if (!params.mId.mLanguageSwitchKeyEnabled && !params.mId.isNumberLayout && RichInputMethodManager.canSwitchLanguage())
                keys.add("!icon/language_switch_key|!code/key_language_switch")
            if (!params.mId.mOneHandedModeEnabled)
                keys.add("!icon/start_onehanded_mode_key|!code/key_start_onehanded")
            if (!params.mId.mDeviceLocked)
                keys.add("!icon/settings_key|!code/key_settings")
            return keys
        }

        private fun getPunctuationPopupKeys(params: KeyboardParams): List<String> {
            if (params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
                return listOf("…")
            if (params.mId.isNumberLayout)
                return listOf(":", "…", ";", "∞", "π", "√", "°", "^")
            val popupKeys = params.mLocaleKeyboardInfos.getPopupKeys("punctuation")!!.toMutableList()
            if (params.mId.mSubtype.isRtlSubtype) {
                for (i in popupKeys.indices)
                    popupKeys[i] = popupKeys[i].rtlLabel(params) // for parentheses
            }
            if (Settings.getInstance().isTablet && popupKeys.contains("!") && popupKeys.contains("?")) {
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

        private fun String.resolveStringLabel(params: KeyboardParams): String {
            if (length < 9 || !startsWith("!string/")) return this
            val id = Settings.getInstance().getStringResIdByName(substringAfter("!string/"))
            if (id == 0) return this
            return getStringInLocale(id, params)
        }

        private fun getStringInLocale(id: Int, params: KeyboardParams): String {
            // todo: hi-Latn strings instead of this workaround?
            val locale = if (params.mId.locale.toLanguageTag() == "hi-Latn") "en_IN".constructLocale()
            else params.mId.locale
            return Settings.getInstance().getInLocale(id, locale)
        }

        // action key stuff below

        // todo (later): should this be handled with metaState? but metaState shift would require LOTS of changes...
        private fun getActionKeyCode(params: KeyboardParams) =
            if (params.mId.isMultiLine && (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED))
                "!code/key_shift_enter"
            else "!code/key_enter"

        private fun getActionKeyLabel(params: KeyboardParams): String {
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
            val replacement = iconName.replaceIconWithLabelIfNoDrawable(params)
            return if (iconName == replacement) // i.e. icon exists
                "!icon/$iconName"
            else
                replacement
        }

        private fun getActionKeyPopupKeys(params: KeyboardParams): SimplePopups? =
            getActionKeyPopupKeyString(params.mId)?.let { createActionPopupKeys(it, params) }

        private fun getActionKeyPopupKeyString(keyboardId: KeyboardId): String? {
            val action = keyboardId.imeAction()
            val navigatePrev = keyboardId.navigatePrevious()
            val navigateNext = keyboardId.navigateNext()
            return when {
                keyboardId.passwordInput() -> when {
                    navigatePrev && action == EditorInfo.IME_ACTION_NEXT -> POPUP_EYS_NAVIGATE_PREVIOUS
                    action == EditorInfo.IME_ACTION_NEXT -> null
                    navigateNext && action == EditorInfo.IME_ACTION_PREVIOUS -> POPUP_EYS_NAVIGATE_NEXT
                    action == EditorInfo.IME_ACTION_PREVIOUS -> null
                    navigateNext && navigatePrev -> POPUP_EYS_NAVIGATE_PREVIOUS_NEXT
                    navigateNext -> POPUP_EYS_NAVIGATE_NEXT
                    navigatePrev -> POPUP_EYS_NAVIGATE_PREVIOUS
                    else -> null
                }
                // could change definition of numbers to query a range, or have a pre-defined list, but not that crucial
                keyboardId.isNumberLayout || keyboardId.mMode in listOf(KeyboardId.MODE_EMAIL, KeyboardId.MODE_DATE, KeyboardId.MODE_TIME, KeyboardId.MODE_DATETIME) -> when {
                    action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> POPUP_EYS_NAVIGATE_PREVIOUS
                    action == EditorInfo.IME_ACTION_NEXT -> null
                    action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> POPUP_EYS_NAVIGATE_NEXT
                    action == EditorInfo.IME_ACTION_PREVIOUS -> null
                    navigateNext && navigatePrev -> POPUP_EYS_NAVIGATE_PREVIOUS_NEXT
                    navigateNext -> POPUP_EYS_NAVIGATE_NEXT
                    navigatePrev -> POPUP_EYS_NAVIGATE_PREVIOUS
                    else -> null
                }
                action == EditorInfo.IME_ACTION_NEXT && navigatePrev -> POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS
                action == EditorInfo.IME_ACTION_NEXT -> POPUP_EYS_NAVIGATE_EMOJI
                action == EditorInfo.IME_ACTION_PREVIOUS && navigateNext -> POPUP_EYS_NAVIGATE_EMOJI_NEXT
                action == EditorInfo.IME_ACTION_PREVIOUS -> POPUP_EYS_NAVIGATE_EMOJI
                navigateNext && navigatePrev -> POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS_NEXT
                navigateNext -> POPUP_EYS_NAVIGATE_EMOJI_NEXT
                navigatePrev -> POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS
                else -> POPUP_EYS_NAVIGATE_EMOJI
            }
        }

        private fun createActionPopupKeys(popupKeysDef: String, params: KeyboardParams): SimplePopups {
            val popupKeys = mutableListOf<String>()
            for (popupKey in popupKeysDef.split(",")) {
                val iconPrefixRemoved = popupKey.substringAfter("!icon/")
                if (iconPrefixRemoved == popupKey) { // i.e. there is no !icon/
                    popupKeys.add(popupKey)
                    continue
                }
                val iconName = iconPrefixRemoved.substringBefore("|")
                val replacementText = iconName.replaceIconWithLabelIfNoDrawable(params)
                if (replacementText == iconName) { // i.e. we have the drawable
                    popupKeys.add(popupKey)
                } else {
                    popupKeys.add(Key.POPUP_KEYS_HAS_LABELS)
                    popupKeys.add("$replacementText|${iconPrefixRemoved.substringAfter("|")}")
                }
            }
            // remove emoji shortcut on enter in tablet mode (like original, because bottom row always has an emoji key)
            // (probably not necessary, but whatever)
            if (Settings.getInstance().isTablet && popupKeys.remove("!icon/emoji_action_key|!code/key_emoji")) {
                val i = popupKeys.indexOfFirst { it.startsWith(Key.POPUP_KEYS_FIXED_COLUMN_ORDER) }
                if (i > -1) {
                    val n = popupKeys[i].substringAfter(Key.POPUP_KEYS_FIXED_COLUMN_ORDER).toIntOrNull()
                    if (n != null)
                        popupKeys[i] = popupKeys[i].replace(n.toString(), (n - 1).toString())
                }
            }
            return SimplePopups(popupKeys)
        }

        private fun String.replaceIconWithLabelIfNoDrawable(params: KeyboardParams): String {
            if (params.mIconsSet.getIconDrawable(this) != null) return this
            if (params.mId.mWidth == AndroidSpellCheckerService.SPELLCHECKER_DUMMY_KEYBOARD_WIDTH
                && params.mId.mHeight == AndroidSpellCheckerService.SPELLCHECKER_DUMMY_KEYBOARD_HEIGHT
                && !params.mId.mSubtype.hasExtraValue(Constants.Subtype.ExtraValue.EMOJI_CAPABLE)
            )
            // fake keyboard that is used by spell checker (for key coordinates), but not shown to the user
            // often this doesn't have any icons loaded, and there is no need to bother with this
                return this
            val id = Settings.getInstance().getStringResIdByName("label_$this")
            if (id == 0) {
                Log.w("TextKeyData", "no resource for label $this in ${params.mId}")
                return this
            }
            return getStringInLocale(id, params)
        }

        // could make arrays right away, but they need to be copied anyway as popupKeys arrays are modified when creating KeyParams
        private const val POPUP_EYS_NAVIGATE_PREVIOUS = "!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard"
        private const val POPUP_EYS_NAVIGATE_NEXT = "!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
        private const val POPUP_EYS_NAVIGATE_PREVIOUS_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/next_key|!code/key_action_next"
        private const val POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS = "!fixedColumnOrder!3,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
        private const val POPUP_EYS_NAVIGATE_EMOJI = "!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji"
        private const val POPUP_EYS_NAVIGATE_EMOJI_NEXT = "!fixedColumnOrder!3,!needsDividers!,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
        private const val POPUP_EYS_NAVIGATE_EMOJI_PREVIOUS_NEXT = "!fixedColumnOrder!4,!needsDividers!,!icon/previous_key|!code/key_action_previous,!icon/clipboard_action_key|!code/key_clipboard,!icon/emoji_action_key|!code/key_emoji,!icon/next_key|!code/key_action_next"
    }

    /** get the label, but also considers code, which can't be set separately for popup keys and thus goes into the label */
    // this mashes the code into the popup label to make it work
    // actually that's a bad approach, but at the same time doing things properly and with reasonable performance requires much more work
    // so better only do it in case the popup stuff needs more improvements
    // idea: directly create PopupKeySpec, but need to deal with needsToUpcase and popupKeysColumnAndFlags
    fun getPopupLabel(params: KeyboardParams): String {
        val newLabel = processLabel(params)
        if (code == KeyCode.UNSPECIFIED) {
            if (newLabel == label) return label
            val newCode = processCode()
            if (newLabel.endsWith("|")) return "${newLabel}!code/$newCode" // for toolbar keys
            return if (newCode == code) newLabel else "${newLabel}|!code/$newCode"
        }
        if (code >= 32) {
            if (newLabel.startsWith(KeyboardIconsSet.PREFIX_ICON)) {
                // we ignore everything after the first |
                // todo (later): for now this is fine, but it should rather be done when creating the popup key,
                //  and it should be consistent with other popups and also with normal keys
                return "${newLabel.substringBefore("|")}|${StringUtils.newSingleCodePointString(code)}"
            }
            return "$newLabel|${StringUtils.newSingleCodePointString(code)}"

        }
        if (code in KeyCode.Spec.CURRENCY) {
            return getCurrencyLabel(params)
        }
        return if (newLabel.endsWith("|")) "$newLabel!code/${processCode()}" // for toolbar keys
        else "$newLabel|!code/${processCode()}"
    }

    fun getCurrencyLabel(params: KeyboardParams): String {
        val newLabel = processLabel(params)
        return when (code) {
            // consider currency codes for label
            KeyCode.CURRENCY_SLOT_1 -> "$newLabel|${params.mLocaleKeyboardInfos.currencyKey.first}"
            KeyCode.CURRENCY_SLOT_2 -> "$newLabel|${params.mLocaleKeyboardInfos.currencyKey.second[0]}"
            KeyCode.CURRENCY_SLOT_3 -> "$newLabel|${params.mLocaleKeyboardInfos.currencyKey.second[1]}"
            KeyCode.CURRENCY_SLOT_4 -> "$newLabel|${params.mLocaleKeyboardInfos.currencyKey.second[2]}"
            KeyCode.CURRENCY_SLOT_5 -> "$newLabel|${params.mLocaleKeyboardInfos.currencyKey.second[3]}"
            KeyCode.CURRENCY_SLOT_6 -> "$newLabel|${params.mLocaleKeyboardInfos.currencyKey.second[4]}"
            else -> throw IllegalStateException("code in currency range, but not in currency range?")
        }
    }

    override fun compute(params: KeyboardParams): KeyData? {
        require(groupId in 0..GROUP_ENTER) { "only positive groupIds up to GROUP_ENTER are supported" }
        require(label.isNotEmpty() || type == KeyType.PLACEHOLDER || code != KeyCode.UNSPECIFIED) { "non-placeholder key has no code and no label" }
        require(width >= 0f || width == -1f) { "illegal width $width" }
        val newLabel = label.convertFlorisLabel().resolveStringLabel(params)
        if (newLabel == KeyLabel.SHIFT && params.mId.isAlphabetKeyboard
                && params.mId.mSubtype.hasExtraValue(Constants.Subtype.ExtraValue.NO_SHIFT_KEY)) {
            return null
        }
        val newCode = code.checkAndConvertCode()
        val newLabelFlags = if (labelFlags == 0 && params.mId.isNumberLayout) {
            if (type == KeyType.NUMERIC) {
                when (params.mId.mElementId) {
                    KeyboardId.ELEMENT_PHONE -> Key.LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER or Key.LABEL_FLAGS_HAS_HINT_LABEL or Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO
                    KeyboardId.ELEMENT_PHONE_SYMBOLS -> 0
                    else -> Key.LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO
                }
            } else 0
        } else labelFlags

        if (newCode != code || newLabel != label || labelFlags != newLabelFlags)
            return copy(newCode = newCode, newLabel = newLabel, newLabelFlags = newLabelFlags)
        return this
    }


    fun isSpaceKey(): Boolean {
        return code == Constants.CODE_SPACE || code == KeyCode.CJK_SPACE || code == KeyCode.ZWNJ || code == KeyCode.KESHIDA
    }

    fun isKeyPlaceholder() = type == KeyType.PLACEHOLDER && code == KeyCode.UNSPECIFIED && width == 0f

    /** this expects that codes and labels are already converted from FlorisBoard values, usually through compute */
    fun toKeyParams(params: KeyboardParams, additionalLabelFlags: Int = 0): Key.KeyParams {
        val newWidth = if (width == 0f) getDefaultWidth(params) else width
        if (type == KeyType.PLACEHOLDER) return Key.KeyParams.newSpacer(params, newWidth)

        val newCode: Int
        val newLabel: String
        if (code in KeyCode.Spec.CURRENCY) {
            // special treatment necessary, because we may need to encode it in the label
            // (currency is a string, so might have more than 1 codepoint, e.g. for Nepal)
            newCode = 0
            newLabel = getCurrencyLabel(params)
        } else {
            newCode = processCode()
            newLabel = processLabel(params)
        }
        val newLabelFlags = labelFlags or additionalLabelFlags or getAdditionalLabelFlags(params)
        val newPopupKeys = popup.merge(getAdditionalPopupKeys(params))

        val background = when (type) {
            KeyType.CHARACTER, KeyType.NUMERIC -> Key.BACKGROUND_TYPE_NORMAL
            KeyType.FUNCTION, KeyType.MODIFIER, KeyType.SYSTEM_GUI -> Key.BACKGROUND_TYPE_FUNCTIONAL
            KeyType.PLACEHOLDER, KeyType.UNSPECIFIED -> Key.BACKGROUND_TYPE_EMPTY
            KeyType.NAVIGATION -> Key.BACKGROUND_TYPE_SPACEBAR
            KeyType.ENTER_EDITING -> Key.BACKGROUND_TYPE_ACTION
            KeyType.LOCK -> getShiftBackground(params)
            null -> getDefaultBackground(params)
        }

        return if (newCode == KeyCode.UNSPECIFIED || newCode == KeyCode.MULTIPLE_CODE_POINTS) {
            // code will be determined from label if possible (i.e. label is single code point)
            // but also longer labels should work without issues, also for MultiTextKeyData
            if (this is MultiTextKeyData) {
                val outputText = String(codePoints, 0, codePoints.size)
                Key.KeyParams(
                    "$newLabel|$outputText",
                    newCode,
                    params,
                    newWidth,
                    newLabelFlags,
                    background,
                    newPopupKeys,
                )
            } else {
                Key.KeyParams(
                    newLabel.rtlLabel(params),
                    params,
                    newWidth,
                    newLabelFlags,
                    background,
                    newPopupKeys,
                )
            }
        } else {
            Key.KeyParams(
                newLabel.ifEmpty { StringUtils.newSingleCodePointString(newCode) },
                newCode,
                params,
                newWidth,
                newLabelFlags,
                background,
                newPopupKeys,
            )
        }
    }

    private fun getDefaultBackground(params: KeyboardParams): Int {
        // functional keys
        when (label) { // or use code?
            KeyLabel.SYMBOL_ALPHA, KeyLabel.SYMBOL, KeyLabel.ALPHA, KeyLabel.COMMA, KeyLabel.PERIOD, KeyLabel.DELETE,
            KeyLabel.COM, KeyLabel.LANGUAGE_SWITCH, KeyLabel.NUMPAD, KeyLabel.CTRL, KeyLabel.ALT,
            KeyLabel.FN, KeyLabel.META, toolbarKeyStrings[ToolbarKey.EMOJI] -> return Key.BACKGROUND_TYPE_FUNCTIONAL
            KeyLabel.SPACE, KeyLabel.ZWNJ -> return Key.BACKGROUND_TYPE_SPACEBAR
            KeyLabel.ACTION -> return Key.BACKGROUND_TYPE_ACTION
            KeyLabel.SHIFT -> return getShiftBackground(params)
        }
        if (type == KeyType.PLACEHOLDER) return Key.BACKGROUND_TYPE_EMPTY
        if ((params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS || params.mId.mElementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED)
                && (groupId == GROUP_COMMA || groupId == GROUP_PERIOD))
            return Key.BACKGROUND_TYPE_FUNCTIONAL
        return Key.BACKGROUND_TYPE_NORMAL
    }

    // todo (later): possibly the whole stickyOn/Off stuff can be removed, currently it should only have a very slight effect in holo
    //  but iirc there is some attempt in reviving the sticky thing, right?
    private fun getShiftBackground(params: KeyboardParams): Int {
        return if (params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
            || params.mId.mElementId == KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED) Key.BACKGROUND_TYPE_STICKY_ON
        else Key.BACKGROUND_TYPE_STICKY_OFF
    }

    private fun getDefaultWidth(params: KeyboardParams): Float {
        return if (label == KeyLabel.SPACE && params.mId.isAlphaOrSymbolKeyboard) -1f
        else if (type == KeyType.NUMERIC && params.mId.isNumberLayout) -1f
        else params.mDefaultKeyWidth
    }

    // todo (later): encoding the code in the label should be avoided, because we know it already
    private fun processLabel(params: KeyboardParams): String = when (label) {
        KeyLabel.SYMBOL_ALPHA -> if (params.mId.isAlphabetKeyboard) params.mLocaleKeyboardInfos.labelSymbol else params.mLocaleKeyboardInfos.labelAlphabet
        KeyLabel.SYMBOL -> params.mLocaleKeyboardInfos.labelSymbol
        KeyLabel.ALPHA -> params.mLocaleKeyboardInfos.labelAlphabet
        KeyLabel.COMMA -> params.mLocaleKeyboardInfos.labelComma
        KeyLabel.PERIOD -> getPeriodLabel(params)
        KeyLabel.SPACE -> getSpaceLabel(params)
        KeyLabel.ACTION -> "${getActionKeyLabel(params)}|${getActionKeyCode(params)}"
        KeyLabel.DELETE -> "!icon/delete_key|!code/key_delete"
        KeyLabel.SHIFT -> "${getShiftLabel(params)}|!code/key_shift"
//        KeyLabel.EMOJI -> "!icon/emoji_normal_key|!code/key_emoji"
        // todo (later): label and popupKeys for .com should be in localeKeyTexts, handled similar to currency key
        KeyLabel.COM -> ".com"
        KeyLabel.LANGUAGE_SWITCH -> "!icon/language_switch_key|!code/key_language_switch"
        KeyLabel.ZWNJ -> "!icon/zwnj_key|\u200C"
        KeyLabel.CURRENCY -> params.mLocaleKeyboardInfos.currencyKey.first
        KeyLabel.CURRENCY1 -> params.mLocaleKeyboardInfos.currencyKey.second[0]
        KeyLabel.CURRENCY2 -> params.mLocaleKeyboardInfos.currencyKey.second[1]
        KeyLabel.CURRENCY3 -> params.mLocaleKeyboardInfos.currencyKey.second[2]
        KeyLabel.CURRENCY4 -> params.mLocaleKeyboardInfos.currencyKey.second[3]
        KeyLabel.CURRENCY5 -> params.mLocaleKeyboardInfos.currencyKey.second[4]
        KeyLabel.CTRL, KeyLabel.ALT, KeyLabel.FN, KeyLabel.META , KeyLabel.ESCAPE -> label.uppercase(Locale.US)
        KeyLabel.TAB -> "!icon/tab_key|"
        else -> {
            if (label in toolbarKeyStrings.values) {
                "!icon/$label|"
            } else label
        }
    }

    private fun processCode(): Int {
        if (code != KeyCode.UNSPECIFIED) return code
        return when (label) {
            KeyLabel.SYMBOL_ALPHA -> KeyCode.SYMBOL_ALPHA
            KeyLabel.SYMBOL -> KeyCode.SYMBOL
            KeyLabel.ALPHA -> KeyCode.ALPHA
            KeyLabel.CTRL -> KeyCode.CTRL
            KeyLabel.ALT -> KeyCode.ALT
            KeyLabel.FN -> KeyCode.FN
            KeyLabel.META -> KeyCode.META
            KeyLabel.TAB -> KeyCode.TAB
            KeyLabel.ESCAPE -> KeyCode.ESCAPE
            else -> {
                if (label in toolbarKeyStrings.values) {
                    getCodeForToolbarKey(ToolbarKey.valueOf(label.uppercase(Locale.US)))
                } else code
            }
        }
    }

    // todo (later): add explanations / reasoning, often this is just taken from conversion from OpenBoard / AOSP layouts
    private fun getAdditionalLabelFlags(params: KeyboardParams): Int {
        return when (label) {
            KeyLabel.ALPHA, KeyLabel.SYMBOL_ALPHA, KeyLabel.SYMBOL -> Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR
            KeyLabel.COMMA -> Key.LABEL_FLAGS_HAS_POPUP_HINT
            // essentially this only changes the appearance of the armenian period key in holo theme
            KeyLabel.PERIOD -> Key.LABEL_FLAGS_HAS_POPUP_HINT and if (params.mId.isAlphabetKeyboard) params.mLocaleKeyboardInfos.labelFlags else 0
            KeyLabel.ACTION -> {
                Key.LABEL_FLAGS_PRESERVE_CASE or Key.LABEL_FLAGS_AUTO_X_SCALE or
                        Key.LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO or Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR or
                        Key.LABEL_FLAGS_HAS_POPUP_HINT or KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId)
            }
            KeyLabel.SPACE -> if (params.mId.isNumberLayout) Key.LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM else 0
            KeyLabel.SHIFT -> Key.LABEL_FLAGS_PRESERVE_CASE or if (!params.mId.isAlphabetKeyboard) Key.LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR else 0
            toolbarKeyStrings[ToolbarKey.EMOJI] -> KeyboardTheme.getThemeActionAndEmojiKeyLabelFlags(params.mThemeId)
            KeyLabel.COM -> Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_HAS_POPUP_HINT or Key.LABEL_FLAGS_PRESERVE_CASE
            KeyLabel.ZWNJ -> Key.LABEL_FLAGS_HAS_POPUP_HINT
            KeyLabel.CURRENCY -> Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
            KeyLabel.CTRL, KeyLabel.ALT, KeyLabel.FN, KeyLabel.META -> Key.LABEL_FLAGS_PRESERVE_CASE
            else -> 0
        }
    }

    private fun getAdditionalPopupKeys(params: KeyboardParams): PopupSet<AbstractKeyData>? {
        if (groupId == GROUP_COMMA) return SimplePopups(getCommaPopupKeys(params))
        if (groupId == GROUP_PERIOD) return SimplePopups(getPunctuationPopupKeys(params))
        if (groupId == GROUP_ENTER) return getActionKeyPopupKeys(params)
        return when (label) {
            KeyLabel.COMMA -> SimplePopups(getCommaPopupKeys(params))
            KeyLabel.PERIOD -> SimplePopups(getPunctuationPopupKeys(params))
            KeyLabel.ACTION -> getActionKeyPopupKeys(params)
            KeyLabel.SHIFT -> {
                if (params.mId.isAlphabetKeyboard) SimplePopups(
                    listOf(
                        "!noPanelAutoPopupKey!",
                        " |!code/key_capslock"
                    )
                ) else null // why the alphabet popup keys actually?
            }
            KeyLabel.COM -> SimplePopups(listOf(Key.POPUP_KEYS_HAS_LABELS, ".net", ".org", ".gov", ".edu"))
            KeyLabel.ZWNJ -> SimplePopups(listOf("!icon/zwj_key|\u200D"))
            // only add currency popups if there are none defined on the key
            KeyLabel.CURRENCY -> if (popup.isEmpty()) SimplePopups(params.mLocaleKeyboardInfos.currencyKey.second) else null
            else -> null
        }
    }
}

/**
 * Data class which describes a single key and its attributes.
 *
 * @property type The type of the key. Some actions require both [code] and [type] to match in order
 *  to be successfully executed. Defaults to null.
 * @property code The UTF-8 encoded code of the character. The code defined here is used as the
 *  data passed to the system. Defaults to 0.
 * @property label The string used to display the key in the UI. Is not used for the actual data
 *  passed to the system. Should normally be the exact same as the [code]. Defaults to an empty
 *  string.
 */
@Serializable
@SerialName("text_key")
class TextKeyData(
    override val type: KeyType? = null,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<out AbstractKeyData> = SimplePopups(null),
    override val width: Float = 0f,
    override val labelFlags: Int = 0
) : KeyData {
    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay || code == KeyCode.URI_COMPONENT_TLD || code < Constants.CODE_SPACE) {
                if (Unicode.isNonSpacingMark(code) && !label.startsWith("◌")) {
                    append("◌")
                }
                append(label)
            } else {
                try { appendCodePoint(code) } catch (_: Throwable) { }
            }
        }
    }

    override fun toString(): String {
        return "${TextKeyData::class.simpleName} { type=$type code=$code label=\"$label\" groupId=$groupId }"
    }

    override fun copy(
        newType: KeyType?,
        newCode: Int,
        newLabel: String,
        newGroupId: Int,
        newPopup: PopupSet<out AbstractKeyData>,
        newWidth: Float,
        newLabelFlags: Int
    ) = TextKeyData(newType, newCode, newLabel, newGroupId, newPopup, newWidth, newLabelFlags)

}

// AutoTextKeyData is just for converting case with shift, which HeliBoard always does anyway
// (maybe change later if there is a use case)
@Serializable
@SerialName("auto_text_key")
class AutoTextKeyData(
    override val type: KeyType? = null,
    override val code: Int = KeyCode.UNSPECIFIED,
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<out AbstractKeyData> = SimplePopups(null),
    override val width: Float = 0f,
    override val labelFlags: Int = 0
) : KeyData {

    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay || code == KeyCode.URI_COMPONENT_TLD || code < Constants.CODE_SPACE) {
                if (Unicode.isNonSpacingMark(code) && !label.startsWith("◌")) {
                    append("◌")
                }
                append(label)
            } else {
                try { appendCodePoint(code) } catch (_: Throwable) { }
            }
        }
    }

    override fun toString(): String {
        return "${AutoTextKeyData::class.simpleName} { type=$type code=$code label=\"$label\" groupId=$groupId }"
    }

    override fun copy(
        newType: KeyType?,
        newCode: Int,
        newLabel: String,
        newGroupId: Int,
        newPopup: PopupSet<out AbstractKeyData>,
        newWidth: Float,
        newLabelFlags: Int
    ) = AutoTextKeyData(newType, newCode, newLabel, newGroupId, newPopup, newWidth, newLabelFlags)

}

@Serializable
@SerialName("multi_text_key")
class MultiTextKeyData(
    override val type: KeyType? = null,
    val codePoints: IntArray = intArrayOf(),
    override val label: String = "",
    override val groupId: Int = KeyData.GROUP_DEFAULT,
    override val popup: PopupSet<out AbstractKeyData> = SimplePopups(null),
    override val width: Float = 0f,
    override val labelFlags: Int = 0
) : KeyData {
    @Transient override val code: Int = KeyCode.MULTIPLE_CODE_POINTS

    override fun compute(params: KeyboardParams): KeyData {
        // todo: does this work? maybe convert label to | style?
        //  but if i allow negative codes, ctrl+z could be on a single key (but floris doesn't support this anyway)
        return this
    }

    override fun asString(isForDisplay: Boolean): String {
        return buildString {
            if (isForDisplay) {
                append(label)
            } else {
                for (codePoint in codePoints) {
                    try { appendCodePoint(codePoint) } catch (_: Throwable) { }
                }
            }
        }
    }

    override fun toString(): String {
        return "${MultiTextKeyData::class.simpleName} { type=$type code=$code label=\"$label\" groupId=$groupId }"
    }

    override fun copy(
        newType: KeyType?,
        newCode: Int,
        newLabel: String,
        newGroupId: Int,
        newPopup: PopupSet<out AbstractKeyData>,
        newWidth: Float,
        newLabelFlags: Int
    ) = MultiTextKeyData(newType, codePoints, newLabel, newGroupId, newPopup, newWidth, newLabelFlags)

}

fun String.toTextKey(popupKeys: Collection<String>? = null, labelFlags: Int = 0): TextKeyData =
    TextKeyData(
        label = this,
        labelFlags = labelFlags,
        popup = SimplePopups(popupKeys)
    )
