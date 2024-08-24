package helium314.keyboard.keyboard.internal

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ToolbarKey
import java.util.Locale

class KeyboardIconsSet private constructor() {
    private var iconIds = emptyMap<String, Int>()
    private val iconsByName = HashMap<String, Drawable>(80)

    fun loadIcons(context: Context) {
        val prefs = DeviceProtectedUtils.getSharedPreferences(context)
        val theme = prefs.getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL)
        val ids = when (theme) {
            KeyboardTheme.STYLE_HOLO -> keyboardIconsHolo
            KeyboardTheme.STYLE_ROUNDED -> keyboardIconsRounded
            else -> keyboardIconsMaterial
        }
        if (ids == iconIds) return
        iconIds = ids
        iconsByName.clear()
        ids.forEach { (name, id) ->
            try {
                val icon = ContextCompat.getDrawable(context, id) ?: return@forEach
                icon.setBounds(0, 0, icon.intrinsicWidth, icon.intrinsicHeight)
                iconsByName[name] = icon
            } catch (e: Resources.NotFoundException) {
                Log.w(TAG, "Drawable resource for icon $name not found")
            }
        }
    }

    fun getIconDrawable(name: String?): Drawable? = iconsByName[name?.lowercase(Locale.US)]
    /** gets drawable from resources, with mutate (might be necessary to avoid coloring issues...) */
    fun getNewDrawable(name: String?, context: Context): Drawable? =
        iconIds[name?.lowercase(Locale.US)]?.let { ContextCompat.getDrawable(context, it)?.mutate() }

    // sometimes there are 2 names for the same icon for historic reasons,
    // and removing needs to be handled with care to not break custom themes
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
        const val NAME_RESIZE_ONEHANDED_KEY = "resize_onehanded_key"
        const val NAME_TOOLBAR_KEY = "toolbar_key"
        const val NAME_BIN = "bin"
/*
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
*/
        private val keyboardIconsHolo by lazy { hashMapOf(
            NAME_SHIFT_KEY to                   R.drawable.sym_keyboard_shift_holo,
            NAME_SHIFT_KEY_SHIFTED to           R.drawable.sym_keyboard_shifted_holo,
            NAME_SHIFT_KEY_LOCKED to            R.drawable.sym_keyboard_shift_lock_holo,
            NAME_DELETE_KEY to                  R.drawable.sym_keyboard_delete_holo,
            NAME_SETTINGS_KEY to                R.drawable.sym_keyboard_settings_holo,
//            NAME_SPACE_KEY to                   null,
            NAME_ENTER_KEY to                   R.drawable.sym_keyboard_return_holo,
//            NAME_GO_KEY to                      null,
            NAME_SEARCH_KEY to                  R.drawable.sym_keyboard_search_holo,
//            NAME_SEND_KEY to                    null,
//            NAME_DONE_KEY to                    null,
//            NAME_NEXT_KEY to                    null,
//            NAME_PREVIOUS_KEY to                null,
            NAME_TAB_KEY to                     R.drawable.sym_keyboard_tab_holo,
            NAME_INCOGNITO_KEY to               R.drawable.sym_keyboard_incognito_holo,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.drawable.sym_keyboard_space_holo,
            NAME_SHORTCUT_KEY to                R.drawable.sym_keyboard_voice_holo,
            NAME_SHORTCUT_KEY_DISABLED to       R.drawable.sym_keyboard_voice_off_holo,
            NAME_LANGUAGE_SWITCH_KEY to         R.drawable.sym_keyboard_language_switch,
            NAME_ZWNJ_KEY to                    R.drawable.sym_keyboard_zwnj_holo,
            NAME_ZWJ_KEY to                     R.drawable.sym_keyboard_zwj_holo,
            NAME_EMOJI_ACTION_KEY to            R.drawable.sym_keyboard_smiley_holo,
            NAME_EMOJI_NORMAL_KEY to            R.drawable.sym_keyboard_smiley_holo,
            NAME_CLIPBOARD_ACTION_KEY to        R.drawable.sym_keyboard_clipboard_holo,
            NAME_CLIPBOARD_NORMAL_KEY to        R.drawable.sym_keyboard_clipboard_holo,
            NAME_CLEAR_CLIPBOARD_KEY to         R.drawable.sym_keyboard_clear_clipboard_holo,
            NAME_CUT_KEY to                     R.drawable.sym_keyboard_cut,
            NAME_START_ONEHANDED_KEY to         R.drawable.sym_keyboard_start_onehanded_holo,
            NAME_STOP_ONEHANDED_KEY to          R.drawable.sym_keyboard_stop_onehanded_holo,
            NAME_SWITCH_ONEHANDED_KEY to        R.drawable.ic_arrow_left,
            NAME_RESIZE_ONEHANDED_KEY to        R.drawable.ic_arrow_horizontal,
            NAME_TOOLBAR_KEY to                 R.drawable.ic_arrow_right,
            NAME_BIN to                         R.drawable.ic_delete,
        ).apply {
            ToolbarKey.entries.forEach {
                put(it.name.lowercase(Locale.US), when (it) {
                    ToolbarKey.VOICE -> R.drawable.sym_keyboard_voice_holo
                    ToolbarKey.CLIPBOARD -> R.drawable.sym_keyboard_clipboard_holo
                    ToolbarKey.NUMPAD -> R.drawable.sym_keyboard_numpad_key_holo
                    ToolbarKey.UNDO -> R.drawable.ic_undo
                    ToolbarKey.REDO -> R.drawable.ic_redo
                    ToolbarKey.SETTINGS -> R.drawable.sym_keyboard_settings_holo
                    ToolbarKey.SELECT_ALL -> R.drawable.ic_select_all
                    ToolbarKey.SELECT_WORD -> R.drawable.ic_select
                    ToolbarKey.COPY -> R.drawable.sym_keyboard_copy
                    ToolbarKey.CUT -> R.drawable.sym_keyboard_cut
                    ToolbarKey.PASTE -> R.drawable.sym_keyboard_paste
                    ToolbarKey.ONE_HANDED -> R.drawable.sym_keyboard_start_onehanded_holo
                    ToolbarKey.INCOGNITO -> R.drawable.sym_keyboard_incognito_holo
                    ToolbarKey.AUTOCORRECT -> R.drawable.ic_autocorrect
                    ToolbarKey.CLEAR_CLIPBOARD -> R.drawable.sym_keyboard_clear_clipboard_holo
                    ToolbarKey.CLOSE_HISTORY -> R.drawable.ic_close
                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_holo
                    ToolbarKey.LEFT -> R.drawable.ic_dpad_left
                    ToolbarKey.RIGHT -> R.drawable.ic_dpad_right
                    ToolbarKey.UP -> R.drawable.ic_dpad_up
                    ToolbarKey.DOWN -> R.drawable.ic_dpad_down
                    ToolbarKey.WORD_LEFT -> R.drawable.ic_word_left
                    ToolbarKey.WORD_RIGHT -> R.drawable.ic_word_right
                    ToolbarKey.PAGE_UP -> R.drawable.ic_page_up
                    ToolbarKey.PAGE_DOWN -> R.drawable.ic_page_down
                    ToolbarKey.FULL_LEFT -> R.drawable.ic_to_start
                    ToolbarKey.FULL_RIGHT -> R.drawable.ic_to_end
                    ToolbarKey.PAGE_START -> R.drawable.ic_page_start
                    ToolbarKey.PAGE_END -> R.drawable.ic_page_end
                })
            }
        } }

        private val keyboardIconsMaterial by lazy { hashMapOf(
            NAME_SHIFT_KEY to                   R.drawable.sym_keyboard_shift_lxx,
            NAME_SHIFT_KEY_SHIFTED to           R.drawable.sym_keyboard_shift_lxx,
            NAME_SHIFT_KEY_LOCKED to            R.drawable.sym_keyboard_shift_lock_lxx,
            NAME_DELETE_KEY to                  R.drawable.sym_keyboard_delete_lxx,
            NAME_SETTINGS_KEY to                R.drawable.sym_keyboard_settings_lxx,
//            NAME_SPACE_KEY to                   null,
            NAME_ENTER_KEY to                   R.drawable.sym_keyboard_return_lxx,
            NAME_GO_KEY to                      R.drawable.sym_keyboard_go_lxx,
            NAME_SEARCH_KEY to                  R.drawable.sym_keyboard_search_lxx,
            NAME_SEND_KEY to                    R.drawable.sym_keyboard_send_lxx,
            NAME_DONE_KEY to                    R.drawable.sym_keyboard_done_lxx,
            NAME_NEXT_KEY to                    R.drawable.ic_arrow_right,
            NAME_PREVIOUS_KEY to                R.drawable.ic_arrow_left,
            NAME_TAB_KEY to                     R.drawable.sym_keyboard_tab_lxx,
            NAME_INCOGNITO_KEY to               R.drawable.sym_keyboard_incognito_lxx,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.drawable.sym_keyboard_space_lxx,
            NAME_SHORTCUT_KEY to                R.drawable.sym_keyboard_voice_lxx,
            NAME_SHORTCUT_KEY_DISABLED to       R.drawable.sym_keyboard_voice_off_lxx,
            NAME_LANGUAGE_SWITCH_KEY to         R.drawable.sym_keyboard_language_switch_lxx,
            NAME_ZWNJ_KEY to                    R.drawable.sym_keyboard_zwnj_lxx,
            NAME_ZWJ_KEY to                     R.drawable.sym_keyboard_zwj_lxx,
            NAME_EMOJI_ACTION_KEY to            R.drawable.sym_keyboard_smiley_lxx,
            NAME_EMOJI_NORMAL_KEY to            R.drawable.sym_keyboard_smiley_lxx,
            NAME_CLIPBOARD_ACTION_KEY to        R.drawable.sym_keyboard_clipboard_lxx,
            NAME_CLIPBOARD_NORMAL_KEY to        R.drawable.sym_keyboard_clipboard_lxx,
            NAME_CLEAR_CLIPBOARD_KEY to         R.drawable.sym_keyboard_clear_clipboard_lxx,
            NAME_CUT_KEY to                     R.drawable.sym_keyboard_cut,
            NAME_START_ONEHANDED_KEY to         R.drawable.sym_keyboard_start_onehanded_lxx,
            NAME_STOP_ONEHANDED_KEY to          R.drawable.sym_keyboard_stop_onehanded_lxx,
            NAME_SWITCH_ONEHANDED_KEY to        R.drawable.ic_arrow_left,
            NAME_RESIZE_ONEHANDED_KEY to        R.drawable.ic_arrow_horizontal,
            NAME_TOOLBAR_KEY to                 R.drawable.ic_arrow_right,
            NAME_BIN to                         R.drawable.ic_delete,
        ).apply {
            ToolbarKey.entries.forEach {
                put(it.name.lowercase(Locale.US), when (it) {
                    ToolbarKey.VOICE -> R.drawable.sym_keyboard_voice_lxx
                    ToolbarKey.CLIPBOARD -> R.drawable.sym_keyboard_clipboard_lxx
                    ToolbarKey.NUMPAD -> R.drawable.sym_keyboard_numpad_key_lxx
                    ToolbarKey.UNDO -> R.drawable.ic_undo
                    ToolbarKey.REDO -> R.drawable.ic_redo
                    ToolbarKey.SETTINGS -> R.drawable.sym_keyboard_settings_lxx
                    ToolbarKey.SELECT_ALL -> R.drawable.ic_select_all
                    ToolbarKey.SELECT_WORD -> R.drawable.ic_select
                    ToolbarKey.COPY -> R.drawable.sym_keyboard_copy
                    ToolbarKey.CUT -> R.drawable.sym_keyboard_cut
                    ToolbarKey.PASTE -> R.drawable.sym_keyboard_paste
                    ToolbarKey.ONE_HANDED -> R.drawable.sym_keyboard_start_onehanded_lxx
                    ToolbarKey.INCOGNITO -> R.drawable.sym_keyboard_incognito_lxx
                    ToolbarKey.AUTOCORRECT -> R.drawable.ic_autocorrect
                    ToolbarKey.CLEAR_CLIPBOARD -> R.drawable.sym_keyboard_clear_clipboard_lxx
                    ToolbarKey.CLOSE_HISTORY -> R.drawable.ic_close
                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_lxx
                    ToolbarKey.LEFT -> R.drawable.ic_dpad_left
                    ToolbarKey.RIGHT -> R.drawable.ic_dpad_right
                    ToolbarKey.UP -> R.drawable.ic_dpad_up
                    ToolbarKey.DOWN -> R.drawable.ic_dpad_down
                    ToolbarKey.WORD_LEFT -> R.drawable.ic_word_left
                    ToolbarKey.WORD_RIGHT -> R.drawable.ic_word_right
                    ToolbarKey.PAGE_UP -> R.drawable.ic_page_up
                    ToolbarKey.PAGE_DOWN -> R.drawable.ic_page_down
                    ToolbarKey.FULL_LEFT -> R.drawable.ic_to_start
                    ToolbarKey.FULL_RIGHT -> R.drawable.ic_to_end
                    ToolbarKey.PAGE_START -> R.drawable.ic_page_start
                    ToolbarKey.PAGE_END -> R.drawable.ic_page_end
                })
            }
        } }

        private val keyboardIconsRounded by lazy { hashMapOf(
            NAME_SHIFT_KEY to                   R.drawable.sym_keyboard_shift_rounded,
            NAME_SHIFT_KEY_SHIFTED to           R.drawable.sym_keyboard_shift_rounded,
            NAME_SHIFT_KEY_LOCKED to            R.drawable.sym_keyboard_shift_lock_rounded,
            NAME_DELETE_KEY to                  R.drawable.sym_keyboard_delete_rounded,
            NAME_SETTINGS_KEY to                R.drawable.sym_keyboard_settings_rounded,
//            NAME_SPACE_KEY to                   null,
            NAME_ENTER_KEY to                   R.drawable.sym_keyboard_return_rounded,
            NAME_GO_KEY to                      R.drawable.sym_keyboard_go_rounded,
            NAME_SEARCH_KEY to                  R.drawable.sym_keyboard_search_rounded,
            NAME_SEND_KEY to                    R.drawable.sym_keyboard_send_rounded,
            NAME_DONE_KEY to                    R.drawable.sym_keyboard_done_rounded,
            NAME_NEXT_KEY to                    R.drawable.ic_arrow_right_rounded,
            NAME_PREVIOUS_KEY to                R.drawable.ic_arrow_left_rounded,
            NAME_TAB_KEY to                     R.drawable.sym_keyboard_tab_rounded,
            NAME_INCOGNITO_KEY to               R.drawable.sym_keyboard_incognito_lxx,
            NAME_SPACE_KEY_FOR_NUMBER_LAYOUT to R.drawable.sym_keyboard_space_rounded,
            NAME_SHORTCUT_KEY to                R.drawable.sym_keyboard_voice_rounded,
            NAME_SHORTCUT_KEY_DISABLED to       R.drawable.sym_keyboard_voice_off_rounded,
            NAME_LANGUAGE_SWITCH_KEY to         R.drawable.sym_keyboard_language_switch_lxx,
            NAME_ZWNJ_KEY to                    R.drawable.sym_keyboard_zwnj_lxx,
            NAME_ZWJ_KEY to                     R.drawable.sym_keyboard_zwj_lxx,
            NAME_EMOJI_ACTION_KEY to            R.drawable.sym_keyboard_smiley_rounded,
            NAME_EMOJI_NORMAL_KEY to            R.drawable.sym_keyboard_smiley_rounded,
            NAME_CLIPBOARD_ACTION_KEY to        R.drawable.sym_keyboard_clipboard_rounded,
            NAME_CLIPBOARD_NORMAL_KEY to        R.drawable.sym_keyboard_clipboard_rounded,
            NAME_CLEAR_CLIPBOARD_KEY to         R.drawable.sym_keyboard_clear_clipboard_rounded,
            NAME_CUT_KEY to                     R.drawable.sym_keyboard_cut_rounded,
            NAME_START_ONEHANDED_KEY to         R.drawable.sym_keyboard_start_onehanded_rounded,
            NAME_STOP_ONEHANDED_KEY to          R.drawable.sym_keyboard_stop_onehanded_rounded,
            NAME_SWITCH_ONEHANDED_KEY to        R.drawable.ic_arrow_left_rounded,
            NAME_RESIZE_ONEHANDED_KEY to        R.drawable.ic_arrow_horizontal_rounded,
            NAME_TOOLBAR_KEY to                 R.drawable.ic_arrow_right_rounded,
            NAME_BIN to                         R.drawable.ic_delete_rounded,
        ).apply {
            ToolbarKey.entries.forEach {
                put(it.name.lowercase(Locale.US), when (it) {
                    ToolbarKey.VOICE -> R.drawable.sym_keyboard_voice_rounded
                    ToolbarKey.CLIPBOARD -> R.drawable.sym_keyboard_clipboard_rounded
                    ToolbarKey.NUMPAD -> R.drawable.sym_keyboard_numpad_key_lxx
                    ToolbarKey.UNDO -> R.drawable.ic_undo_rounded
                    ToolbarKey.REDO -> R.drawable.ic_redo_rounded
                    ToolbarKey.SETTINGS -> R.drawable.sym_keyboard_settings_rounded
                    ToolbarKey.SELECT_ALL -> R.drawable.ic_select_all_rounded
                    ToolbarKey.SELECT_WORD -> R.drawable.ic_select_rounded
                    ToolbarKey.COPY -> R.drawable.sym_keyboard_copy_rounded
                    ToolbarKey.CUT -> R.drawable.sym_keyboard_cut_rounded
                    ToolbarKey.PASTE -> R.drawable.sym_keyboard_paste_rounded
                    ToolbarKey.ONE_HANDED -> R.drawable.sym_keyboard_start_onehanded_rounded
                    ToolbarKey.INCOGNITO -> R.drawable.sym_keyboard_incognito_lxx
                    ToolbarKey.AUTOCORRECT -> R.drawable.ic_autocorrect_rounded
                    ToolbarKey.CLEAR_CLIPBOARD -> R.drawable.sym_keyboard_clear_clipboard_rounded
                    ToolbarKey.CLOSE_HISTORY -> R.drawable.ic_close_rounded
                    ToolbarKey.EMOJI -> R.drawable.sym_keyboard_smiley_rounded
                    ToolbarKey.LEFT -> R.drawable.ic_dpad_left_rounded
                    ToolbarKey.RIGHT -> R.drawable.ic_dpad_right_rounded
                    ToolbarKey.UP -> R.drawable.ic_dpad_up_rounded
                    ToolbarKey.DOWN -> R.drawable.ic_dpad_down_rounded
                    ToolbarKey.WORD_LEFT -> R.drawable.ic_word_left_rounded
                    ToolbarKey.WORD_RIGHT -> R.drawable.ic_word_right_rounded
                    ToolbarKey.PAGE_UP -> R.drawable.ic_page_up_rounded
                    ToolbarKey.PAGE_DOWN -> R.drawable.ic_page_down_rounded
                    ToolbarKey.FULL_LEFT -> R.drawable.ic_to_start_rounded
                    ToolbarKey.FULL_RIGHT -> R.drawable.ic_to_end_rounded
                    ToolbarKey.PAGE_START -> R.drawable.ic_page_start_rounded
                    ToolbarKey.PAGE_END -> R.drawable.ic_page_end_rounded
                })
            }
        } }

        val instance = KeyboardIconsSet()
    }
}
