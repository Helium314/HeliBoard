package org.oscar.kb.keyboard.internal

import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import org.oscar.kb.R
import org.oscar.kb.latin.utils.Log
import org.oscar.kb.latin.utils.ToolbarKey
import org.oscar.kb.latin.utils.getStyleableIconId

import java.util.Locale

class KeyboardIconsSet {
    private val iconsByName = HashMap<String, Drawable>(styleableIdByName.size)

    fun loadIcons(keyboardAttrs: TypedArray) {
        styleableIdByName.forEach { (name, id) ->
            try {
                val icon = keyboardAttrs.getDrawable(id) ?: return@forEach
                icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                iconsByName[name] = icon
            } catch (e: Resources.NotFoundException) {
                Log.w(TAG, "Drawable resource for icon #${keyboardAttrs.resources.getResourceEntryName(id)} not found")
            }
        }
    }

    fun getIconDrawable(name: String?) = iconsByName[name]

    companion object {
        private val TAG = KeyboardIconsSet::class.simpleName
        const val PREFIX_ICON = "!icon/"

        const val NAME_SHIFT_KEY = "shift_key"
        const val NAME_SHIFT_KEY_SHIFTED = "shift_key_shifted"
        const val NAME_SHIFT_KEY_LOCKED = "shift_key_locked"
        const val NAME_DELETE_KEY = "delete_key"
        const val NAME_SETTINGS_KEY = "settings_key"
        const val NAME_SPACE_KEY = "space_key"
        const val NAME_SPACE_KEY_FOR_NUMBER_LAYOUT = "space_key_for_number_layout"
        const val NAME_ENTER_KEY = "enter_key"
        const val NAME_GO_KEY = "go_key"
        const val NAME_SEARCH_KEY = "search_key"
        const val NAME_SEND_KEY = "send_key"
        const val NAME_NEXT_KEY = "next_key"
        const val NAME_DONE_KEY = "done_key"
        const val NAME_PREVIOUS_KEY = "previous_key"
        const val NAME_TAB_KEY = "tab_key"
        const val NAME_SHORTCUT_KEY = "shortcut_key"
        const val NAME_INCOGNITO_KEY = "incognito_key"
        const val NAME_OSCAR_AI = "oscar_ai"
        const val NAME_SHORTCUT_KEY_DISABLED = "shortcut_key_disabled"
        const val NAME_LANGUAGE_SWITCH_KEY = "language_switch_key"
        const val NAME_ZWNJ_KEY = "zwnj_key"
        const val NAME_ZWJ_KEY = "zwj_key"
        const val NAME_EMOJI_ACTION_KEY = "emoji_action_key"
        const val NAME_EMOJI_NORMAL_KEY = "emoji_normal_key"
        const val NAME_CLIPBOARD_ACTION_KEY = "clipboard_action_key"
        const val NAME_CLIPBOARD_NORMAL_KEY = "clipboard_normal_key"
        const val NAME_CLEAR_CLIPBOARD_KEY = "clear_clipboard_key"
        const val NAME_CUT_KEY = "cut_key"
        const val NAME_START_ONEHANDED_KEY = "start_onehanded_mode_key"
        const val NAME_STOP_ONEHANDED_KEY = "stop_onehanded_mode_key"
        const val NAME_SWITCH_ONEHANDED_KEY = "switch_onehanded_key"

        private val styleableIdByName = hashMapOf(
            NAME_SHIFT_KEY to                   R.styleable.Keyboard_iconShiftKey,
            NAME_DELETE_KEY to                  R.styleable.Keyboard_iconDeleteKey,
            NAME_SETTINGS_KEY to                R.styleable.Keyboard_iconSettingsKey,
            NAME_SPACE_KEY to                   R.styleable.Keyboard_iconSpaceKey,
            NAME_ENTER_KEY to                   R.styleable.Keyboard_iconEnterKey,
            NAME_GO_KEY to                      R.styleable.Keyboard_iconGoKey,
            NAME_SEARCH_KEY to                  R.styleable.Keyboard_iconSearchKey,
            NAME_SEND_KEY to                    R.styleable.Keyboard_iconSendKey,
            NAME_NEXT_KEY to                    R.styleable.Keyboard_iconNextKey,
            NAME_DONE_KEY to                    R.styleable.Keyboard_iconDoneKey,
            NAME_PREVIOUS_KEY to                R.styleable.Keyboard_iconPreviousKey,
            NAME_TAB_KEY to                     R.styleable.Keyboard_iconTabKey,
            NAME_SHORTCUT_KEY to                R.styleable.Keyboard_iconShortcutKey,
            NAME_OSCAR_AI to                    R.styleable.Keyboard_iconOscarAI,
            NAME_INCOGNITO_KEY to               R.styleable.Keyboard_iconIncognitoKey,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.styleable.Keyboard_iconSpaceKeyForNumberLayout,
            NAME_SHIFT_KEY_SHIFTED to           R.styleable.Keyboard_iconShiftKeyShifted,
            NAME_SHIFT_KEY_LOCKED to            R.styleable.Keyboard_iconShiftKeyLocked,
            NAME_SHORTCUT_KEY_DISABLED to       R.styleable.Keyboard_iconShortcutKeyDisabled,
            NAME_LANGUAGE_SWITCH_KEY to         R.styleable.Keyboard_iconLanguageSwitchKey,
            NAME_ZWNJ_KEY to                    R.styleable.Keyboard_iconZwnjKey,
            NAME_ZWJ_KEY to                     R.styleable.Keyboard_iconZwjKey,
            NAME_EMOJI_ACTION_KEY to            R.styleable.Keyboard_iconEmojiActionKey,
            NAME_EMOJI_NORMAL_KEY to            R.styleable.Keyboard_iconEmojiNormalKey,
            NAME_CLIPBOARD_ACTION_KEY to        R.styleable.Keyboard_iconClipboardActionKey,
            NAME_CLIPBOARD_NORMAL_KEY to        R.styleable.Keyboard_iconClipboardNormalKey,
            NAME_CLEAR_CLIPBOARD_KEY to         R.styleable.Keyboard_iconClearClipboardKey,
            NAME_CUT_KEY to                     R.styleable.Keyboard_iconCutKey,
            NAME_START_ONEHANDED_KEY to         R.styleable.Keyboard_iconStartOneHandedMode,
            NAME_STOP_ONEHANDED_KEY to          R.styleable.Keyboard_iconStopOneHandedMode,
            NAME_SWITCH_ONEHANDED_KEY to        R.styleable.Keyboard_iconSwitchOneHandedMode,
        ).apply { ToolbarKey.entries.forEach { put(it.name.lowercase(Locale.US), getStyleableIconId(it)) } }
    }
}
