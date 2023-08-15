/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.Log;
import android.util.SparseIntArray;

import org.dslul.openboard.inputmethod.latin.R;

import java.util.HashMap;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

public final class KeyboardIconsSet {
    private static final String TAG = KeyboardIconsSet.class.getSimpleName();

    public static final String PREFIX_ICON = "!icon/";
    public static final int ICON_UNDEFINED = 0;
    private static final int ATTR_UNDEFINED = 0;

    private static final String NAME_UNDEFINED = "undefined";
    public static final String NAME_SHIFT_KEY = "shift_key";
    public static final String NAME_SHIFT_KEY_SHIFTED = "shift_key_shifted";
    public static final String NAME_DELETE_KEY = "delete_key";
    public static final String NAME_SETTINGS_KEY = "settings_key";
    public static final String NAME_SPACE_KEY = "space_key";
    public static final String NAME_SPACE_KEY_FOR_NUMBER_LAYOUT = "space_key_for_number_layout";
    public static final String NAME_ENTER_KEY = "enter_key";
    public static final String NAME_GO_KEY = "go_key";
    public static final String NAME_SEARCH_KEY = "search_key";
    public static final String NAME_SEND_KEY = "send_key";
    public static final String NAME_NEXT_KEY = "next_key";
    public static final String NAME_DONE_KEY = "done_key";
    public static final String NAME_PREVIOUS_KEY = "previous_key";
    public static final String NAME_TAB_KEY = "tab_key";
    public static final String NAME_SHORTCUT_KEY = "shortcut_key";
    public static final String NAME_INCOGNITO_KEY = "incognito_key";
    public static final String NAME_SHORTCUT_KEY_DISABLED = "shortcut_key_disabled";
    public static final String NAME_LANGUAGE_SWITCH_KEY = "language_switch_key";
    public static final String NAME_ZWNJ_KEY = "zwnj_key";
    public static final String NAME_ZWJ_KEY = "zwj_key";
    public static final String NAME_EMOJI_ACTION_KEY = "emoji_action_key";
    public static final String NAME_EMOJI_NORMAL_KEY = "emoji_normal_key";
    public static final String NAME_CLIPBOARD_ACTION_KEY = "clipboard_action_key";
    public static final String NAME_CLIPBOARD_NORMAL_KEY = "clipboard_normal_key";
    public static final String NAME_CLEAR_CLIPBOARD_KEY = "clear_clipboard_key";
    public static final String NAME_NUMPAD_KEY = "numpad_key";
    public static final String NAME_START_ONEHANDED_KEY = "start_onehanded_mode_key";
    public static final String NAME_STOP_ONEHANDED_KEY = "stop_onehanded_mode_key";
    public static final String NAME_SWITCH_ONEHANDED_KEY = "switch_onehanded_key";

    private static final SparseIntArray ATTR_ID_TO_ICON_ID = new SparseIntArray();

    // Icon name to icon id map.
    private static final HashMap<String, Integer> sNameToIdsMap = new HashMap<>();

    private static final Object[] NAMES_AND_ATTR_IDS = {
        NAME_UNDEFINED,                   ATTR_UNDEFINED,
        NAME_SHIFT_KEY,                   R.styleable.Keyboard_iconShiftKey,
        NAME_DELETE_KEY,                  R.styleable.Keyboard_iconDeleteKey,
        NAME_SETTINGS_KEY,                R.styleable.Keyboard_iconSettingsKey,
        NAME_SPACE_KEY,                   R.styleable.Keyboard_iconSpaceKey,
        NAME_ENTER_KEY,                   R.styleable.Keyboard_iconEnterKey,
        NAME_GO_KEY,                      R.styleable.Keyboard_iconGoKey,
        NAME_SEARCH_KEY,                  R.styleable.Keyboard_iconSearchKey,
        NAME_SEND_KEY,                    R.styleable.Keyboard_iconSendKey,
        NAME_NEXT_KEY,                    R.styleable.Keyboard_iconNextKey,
        NAME_DONE_KEY,                    R.styleable.Keyboard_iconDoneKey,
        NAME_PREVIOUS_KEY,                R.styleable.Keyboard_iconPreviousKey,
        NAME_TAB_KEY,                     R.styleable.Keyboard_iconTabKey,
        NAME_SHORTCUT_KEY,                R.styleable.Keyboard_iconShortcutKey,
        NAME_INCOGNITO_KEY,               R.styleable.Keyboard_iconIncognitoKey,
        NAME_SPACE_KEY_FOR_NUMBER_LAYOUT, R.styleable.Keyboard_iconSpaceKeyForNumberLayout,
        NAME_SHIFT_KEY_SHIFTED,           R.styleable.Keyboard_iconShiftKeyShifted,
        NAME_SHORTCUT_KEY_DISABLED,       R.styleable.Keyboard_iconShortcutKeyDisabled,
        NAME_LANGUAGE_SWITCH_KEY,         R.styleable.Keyboard_iconLanguageSwitchKey,
        NAME_ZWNJ_KEY,                    R.styleable.Keyboard_iconZwnjKey,
        NAME_ZWJ_KEY,                     R.styleable.Keyboard_iconZwjKey,
        NAME_EMOJI_ACTION_KEY,            R.styleable.Keyboard_iconEmojiActionKey,
        NAME_EMOJI_NORMAL_KEY,            R.styleable.Keyboard_iconEmojiNormalKey,
        NAME_CLIPBOARD_ACTION_KEY,        R.styleable.Keyboard_iconClipboardActionKey,
        NAME_CLIPBOARD_NORMAL_KEY,        R.styleable.Keyboard_iconClipboardNormalKey,
        NAME_CLEAR_CLIPBOARD_KEY,         R.styleable.Keyboard_iconClearClipboardKey,
        NAME_NUMPAD_KEY,                  R.styleable.Keyboard_iconNumpadKey,
        NAME_START_ONEHANDED_KEY,         R.styleable.Keyboard_iconStartOneHandedMode,
        NAME_STOP_ONEHANDED_KEY,          R.styleable.Keyboard_iconStopOneHandedMode,
        NAME_SWITCH_ONEHANDED_KEY,        R.styleable.Keyboard_iconSwitchOneHandedMode,
    };

    private static int NUM_ICONS = NAMES_AND_ATTR_IDS.length / 2;
    private static final String[] ICON_NAMES = new String[NUM_ICONS];
    private final Drawable[] mIcons = new Drawable[NUM_ICONS];
    private final int[] mIconResourceIds = new int[NUM_ICONS];

    static {
        int iconId = ICON_UNDEFINED;
        for (int i = 0; i < NAMES_AND_ATTR_IDS.length; i += 2) {
            final String name = (String)NAMES_AND_ATTR_IDS[i];
            final Integer attrId = (Integer)NAMES_AND_ATTR_IDS[i + 1];
            if (attrId != ATTR_UNDEFINED) {
                ATTR_ID_TO_ICON_ID.put(attrId, iconId);
            }
            sNameToIdsMap.put(name, iconId);
            ICON_NAMES[iconId] = name;
            iconId++;
        }
    }

    public void loadIcons(final TypedArray keyboardAttrs) {
        final int size = ATTR_ID_TO_ICON_ID.size();
        for (int index = 0; index < size; index++) {
            final int attrId = ATTR_ID_TO_ICON_ID.keyAt(index);
            try {
                final Drawable icon = keyboardAttrs.getDrawable(attrId);
                setDefaultBounds(icon);
                final Integer iconId = ATTR_ID_TO_ICON_ID.get(attrId);
                mIcons[iconId] = icon;
                mIconResourceIds[iconId] = keyboardAttrs.getResourceId(attrId, 0);
            } catch (Resources.NotFoundException e) {
                Log.w(TAG, "Drawable resource for icon #"
                        + keyboardAttrs.getResources().getResourceEntryName(attrId)
                        + " not found");
            }
        }
    }

    private static boolean isValidIconId(final int iconId) {
        return iconId >= 0 && iconId < ICON_NAMES.length;
    }

    @Nonnull
    public static String getIconName(final int iconId) {
        return isValidIconId(iconId) ? ICON_NAMES[iconId] : "unknown<" + iconId + ">";
    }

    public static int getIconId(final String name) {
        Integer iconId = sNameToIdsMap.get(name);
        if (iconId != null) {
            return iconId;
        }
        throw new RuntimeException("unknown icon name: " + name);
    }

    public int getIconResourceId(final String name) {
        final int iconId = getIconId(name);
        if (isValidIconId(iconId)) {
            return mIconResourceIds[iconId];
        }
        throw new RuntimeException("unknown icon name: " + name);
    }

    @Nullable
    public Drawable getIconDrawable(final int iconId) {
        if (isValidIconId(iconId)) {
            return mIcons[iconId];
        }
        throw new RuntimeException("unknown icon id: " + getIconName(iconId));
    }

    private static void setDefaultBounds(final Drawable icon)  {
        if (icon != null) {
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
        }
    }
}
