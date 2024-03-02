/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */
package helium314.keyboard.keyboard.internal.keyboard_parser.floris

// taken from FlorisBoard and modified
object KeyCode {
    object Spec {
        const val CHARACTERS_MIN = 1
        const val CHARACTERS_MAX = 65535
        val CHARACTERS = CHARACTERS_MIN..CHARACTERS_MAX

        const val INTERNAL_FLORIS_MIN = -9999
        const val INTERNAL_FLORIS_MAX = -1
        val INTERNAL_FLORIS = INTERNAL_FLORIS_MIN..INTERNAL_FLORIS_MAX
        val INTERNAL_HELI = -19999..-10000 // for keys exclusive to this app
    }

    const val UNSPECIFIED =                    0

    const val CTRL =                          -1
    const val CTRL_LOCK =                     -2
    const val ALT =                           -3
    const val ALT_LOCK =                      -4
    const val FN =                            -5
    const val FN_LOCK =                       -6
    const val DELETE =                        -7
    const val DELETE_WORD =                   -8
    const val FORWARD_DELETE =                -9
    const val FORWARD_DELETE_WORD =          -10
    const val SHIFT =                        -11
    const val CAPS_LOCK =                    -13

    const val ARROW_LEFT =                   -21
    const val ARROW_RIGHT =                  -22
    const val ARROW_UP =                     -23
    const val ARROW_DOWN =                   -24
    const val MOVE_START_OF_PAGE =           -25
    const val MOVE_END_OF_PAGE =             -26
    const val MOVE_START_OF_LINE =           -27
    const val MOVE_END_OF_LINE =             -28

    const val CLIPBOARD_COPY =               -31
    const val CLIPBOARD_CUT =                -32
    const val CLIPBOARD_PASTE =              -33
    const val CLIPBOARD_SELECT_WORD =        -34 // CLIPBOARD_SELECT
    const val CLIPBOARD_SELECT_ALL =         -35
    const val CLIPBOARD_CLEAR_HISTORY =      -36
    const val CLIPBOARD_CLEAR_FULL_HISTORY = -37
    const val CLIPBOARD_CLEAR_PRIMARY_CLIP = -38

    const val COMPACT_LAYOUT_TO_LEFT =      -111
    const val COMPACT_LAYOUT_TO_RIGHT =     -112
    const val SPLIT_LAYOUT =                -113
    const val MERGE_LAYOUT =                -114

    const val UNDO =                        -131
    const val REDO =                        -132

    const val ALPHA =                       -201 // VIEW_CHARACTERS
    const val SYMBOL =                      -202 // VIEW_SYMBOLS
    const val VIEW_SYMBOLS2 =               -203
    const val VIEW_NUMERIC =                -204
    const val NUMPAD =                      -205 // VIEW_NUMERIC_ADVANCED
    const val VIEW_PHONE =                  -206
    const val VIEW_PHONE2 =                 -207

    const val IME_UI_MODE_TEXT =            -211
    const val EMOJI =                       -212 // IME_UI_MODE_MEDIA
    const val CLIPBOARD =                   -213 // IME_UI_MODE_CLIPBOARD

    const val SYSTEM_INPUT_METHOD_PICKER =  -221
    const val SYSTEM_PREV_INPUT_METHOD =    -222
    const val SYSTEM_NEXT_INPUT_METHOD =    -223
    const val IME_SUBTYPE_PICKER =          -224
    const val IME_PREV_SUBTYPE =            -225
    const val IME_NEXT_SUBTYPE =            -226
    const val LANGUAGE_SWITCH =             -227

    const val IME_SHOW_UI =                 -231
    const val IME_HIDE_UI =                 -232
    const val VOICE_INPUT =                 -233

    const val TOGGLE_SMARTBAR_VISIBILITY =  -241
    const val TOGGLE_ACTIONS_OVERFLOW =     -242
    const val TOGGLE_ACTIONS_EDITOR =       -243
    const val TOGGLE_INCOGNITO_MODE =       -244
    const val TOGGLE_AUTOCORRECT =          -245

    const val URI_COMPONENT_TLD =           -255

    const val SETTINGS =                    -301

    const val CURRENCY_SLOT_1 =             -801
    const val CURRENCY_SLOT_2 =             -802
    const val CURRENCY_SLOT_3 =             -803
    const val CURRENCY_SLOT_4 =             -804
    const val CURRENCY_SLOT_5 =             -805
    const val CURRENCY_SLOT_6 =             -806

    const val MULTIPLE_CODE_POINTS =        -902
    const val DRAG_MARKER =                 -991
    const val NOOP =                        -999

    const val CHAR_WIDTH_SWITCHER =        -9701
    const val CHAR_WIDTH_FULL =            -9702
    const val CHAR_WIDTH_HALF =            -9703

    const val KANA_SMALL =                 12307
    const val KANA_SWITCHER =              -9710
    const val KANA_HIRA =                  -9711
    const val KANA_KATA =                  -9712
    const val KANA_HALF_KATA =             -9713

    const val KESHIDA =                     1600
    const val HALF_SPACE =                  8204

    const val CJK_SPACE =                  12288

    // heliboard only codes
    const val ALPHA_SYMBOL =              -10001
    const val START_ONE_HANDED_MODE =     -10002
    const val STOP_ONE_HANDED_MODE =      -10003
    const val SWITCH_ONE_HANDED_MODE =    -10004
    const val SHIFT_ENTER =               -10005
    const val ACTION_NEXT =               -10006
    const val ACTION_PREVIOUS =           -10007
    const val SYMBOL_SHIFT =              -10008 // todo: check, maybe can be removed
    // Code value representing the code is not specified.
    const val NOT_SPECIFIED =             -10009 // todo: not sure if there is need to have the "old" unspecified keyCode different, just test it and maybe merge

    /** to make sure a FlorisBoard code works when reading a JSON layout */
    private fun Int.checkOrConvertCode(): Int = when (this) {
        // todo: should work, but not yet
        // CURRENCY_SLOT_1, CURRENCY_SLOT_2, CURRENCY_SLOT_3, CURRENCY_SLOT_4, CURRENCY_SLOT_5, CURRENCY_SLOT_6,

        // working
        VOICE_INPUT, LANGUAGE_SWITCH, SETTINGS, DELETE, ALPHA, SYMBOL, EMOJI, CLIPBOARD,
        UNDO, REDO, ARROW_DOWN, ARROW_UP, ARROW_RIGHT, ARROW_LEFT, CLIPBOARD_COPY, CLIPBOARD_SELECT_ALL,
        CLIPBOARD_SELECT_WORD, TOGGLE_INCOGNITO_MODE, TOGGLE_AUTOCORRECT, MOVE_START_OF_LINE, MOVE_END_OF_LINE,
        SHIFT, CAPS_LOCK, MULTIPLE_CODE_POINTS, UNSPECIFIED,

        // heliboard only
        ALPHA_SYMBOL, START_ONE_HANDED_MODE, STOP_ONE_HANDED_MODE, SWITCH_ONE_HANDED_MODE, SHIFT_ENTER,
        ACTION_NEXT, ACTION_PREVIOUS, SYMBOL_SHIFT, NOT_SPECIFIED
        -> this

        // conversion
        IME_UI_MODE_TEXT -> ALPHA

        else -> throw IllegalStateException("key code $this not yet supported")
    }

    /** to make sure a FlorisBoard label works when reading a JSON layout */
    private fun String.convertFlorisLabel(): String = when (this) {
        "view_characters" -> "alpha"
        "view_symbols" -> "symbol"
        "view_numeric_advanced" -> "numpad"
        "view_phone" -> "alpha"
        "view_phone2" -> "symbols"
        "ime_ui_mode_media" -> "emoji"
        "ime_ui_mode_clipboard" -> "clipboard"
        "ime_ui_mode_text" -> "alpha"
        "currency_slot_1" -> "$$$"
        "currency_slot_2" -> "$$$1"
        "currency_slot_3" -> "$$$2"
        "currency_slot_4" -> "$$$3"
        "currency_slot_5" -> "$$$4"
        "currency_slot_6" -> "$$$5"
        else -> this
    }

    fun KeyData.convertFloris() = when (this) {
        is TextKeyData -> { TextKeyData(type, code.checkOrConvertCode(), label.convertFlorisLabel(), groupId, popup, labelFlags) }
        is AutoTextKeyData -> { AutoTextKeyData(type, code.checkOrConvertCode(), label.convertFlorisLabel(), groupId, popup, labelFlags) }
        is MultiTextKeyData -> { MultiTextKeyData(type, codePoints, label.convertFlorisLabel(), groupId, popup, labelFlags) }
    }
}
