/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import helium314.keyboard.latin.common.Constants;

import java.util.HashMap;

public final class KeyboardCodesSet {
    public static final String PREFIX_CODE = "!code/";

    private static final HashMap<String, Integer> sNameToIdMap = new HashMap<>();

    private KeyboardCodesSet() {
        // This utility class is not publicly instantiable.
    }

    public static int getCode(final String name) {
        Integer id = sNameToIdMap.get(name);
        if (id == null) throw new RuntimeException("Unknown key code: " + name);
        return DEFAULT[id];
    }

    private static final String[] ID_TO_NAME = {
        "key_tab",
        "key_enter",
        "key_space",
        "key_shift",
        "key_capslock",
        "key_switch_alpha_symbol",
        "key_output_text",
        "key_delete",
        "key_settings",
        "key_shortcut",
        "key_action_next",
        "key_action_previous",
        "key_shift_enter",
        "key_language_switch",
        "key_emoji",
        "key_alpha_from_emoji",
        "key_unspecified",
        "key_clipboard",
        "key_alpha_from_clipboard",
        "key_numpad",
        "key_alphaNumpad",
        "key_symbolNumpad",
        "key_start_onehanded",
        "key_stop_onehanded",
        "key_switch_onehanded"
    };

    private static final int[] DEFAULT = {
        Constants.CODE_TAB,
        Constants.CODE_ENTER,
        Constants.CODE_SPACE,
        Constants.CODE_SHIFT,
        Constants.CODE_CAPSLOCK,
        Constants.CODE_SWITCH_ALPHA_SYMBOL,
        Constants.CODE_OUTPUT_TEXT,
        Constants.CODE_DELETE,
        Constants.CODE_SETTINGS,
        Constants.CODE_SHORTCUT,
        Constants.CODE_ACTION_NEXT,
        Constants.CODE_ACTION_PREVIOUS,
        Constants.CODE_SHIFT_ENTER,
        Constants.CODE_LANGUAGE_SWITCH,
        Constants.CODE_EMOJI,
        Constants.CODE_ALPHA_FROM_EMOJI,
        Constants.CODE_UNSPECIFIED,
        Constants.CODE_CLIPBOARD,
        Constants.CODE_ALPHA_FROM_CLIPBOARD,
        Constants.CODE_NUMPAD,
        Constants.CODE_ALPHA_FROM_NUMPAD,
        Constants.CODE_SYMBOL_FROM_NUMPAD,
        Constants.CODE_START_ONE_HANDED_MODE,
        Constants.CODE_STOP_ONE_HANDED_MODE,
        Constants.CODE_SWITCH_ONE_HANDED_MODE
    };

    static {
        for (int i = 0; i < ID_TO_NAME.length; i++) {
            sNameToIdMap.put(ID_TO_NAME[i], i);
        }
    }
}
