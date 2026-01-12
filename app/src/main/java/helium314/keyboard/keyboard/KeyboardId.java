/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import android.text.InputType;
import android.text.TextUtils;
import android.view.inputmethod.EditorInfo;

import helium314.keyboard.compat.EditorInfoCompatUtils;
import helium314.keyboard.latin.RichInputMethodSubtype;
import helium314.keyboard.latin.WordComposer;
import helium314.keyboard.latin.utils.InputTypeUtils;

import java.util.Arrays;
import java.util.Locale;
import java.util.Objects;

import static helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

/**
 * Unique identifier for each keyboard type.
 */
public final class KeyboardId {
    public static final int MODE_TEXT = 0;
    public static final int MODE_URL = 1;
    public static final int MODE_EMAIL = 2;
    public static final int MODE_IM = 3;
    public static final int MODE_PHONE = 4;
    public static final int MODE_NUMBER = 5;
    public static final int MODE_DATE = 6;
    public static final int MODE_TIME = 7;
    public static final int MODE_DATETIME = 8;
    public static final int MODE_NUMPAD = 9;

    public static final int ELEMENT_ALPHABET = 0;
    public static final int ELEMENT_ALPHABET_MANUAL_SHIFTED = 1;
    public static final int ELEMENT_ALPHABET_AUTOMATIC_SHIFTED = 2;
    public static final int ELEMENT_ALPHABET_SHIFT_LOCKED = 3;
    public static final int ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED = 4;
    public static final int ELEMENT_SYMBOLS = 5;
    public static final int ELEMENT_SYMBOLS_SHIFTED = 6;
    public static final int ELEMENT_PHONE = 7;
    public static final int ELEMENT_PHONE_SYMBOLS = 8;
    public static final int ELEMENT_NUMBER = 9;
    public static final int ELEMENT_EMOJI_RECENTS = 10;
    public static final int ELEMENT_EMOJI_CATEGORY1 = 11;
    public static final int ELEMENT_EMOJI_CATEGORY2 = 12;
    public static final int ELEMENT_EMOJI_CATEGORY3 = 13;
    public static final int ELEMENT_EMOJI_CATEGORY4 = 14;
    public static final int ELEMENT_EMOJI_CATEGORY5 = 15;
    public static final int ELEMENT_EMOJI_CATEGORY6 = 16;
    public static final int ELEMENT_EMOJI_CATEGORY7 = 17;
    public static final int ELEMENT_EMOJI_CATEGORY8 = 18;
    public static final int ELEMENT_EMOJI_CATEGORY9 = 19;
    public static final int ELEMENT_EMOJI_CATEGORY10 = 20;
    public static final int ELEMENT_EMOJI_CATEGORY11 = 21;
    public static final int ELEMENT_EMOJI_CATEGORY12 = 22;
    public static final int ELEMENT_EMOJI_CATEGORY13 = 23;
    public static final int ELEMENT_EMOJI_CATEGORY14 = 24;
    public static final int ELEMENT_EMOJI_CATEGORY15 = 25;
    public static final int ELEMENT_EMOJI_CATEGORY16 = 26;
    public static final int ELEMENT_CLIPBOARD = 27;
    public static final int ELEMENT_NUMPAD = 28;
    public static final int ELEMENT_EMOJI_BOTTOM_ROW = 29;
    public static final int ELEMENT_CLIPBOARD_BOTTOM_ROW = 30;

    public final RichInputMethodSubtype subtype;
    public final int width;
    public final int height;
    public final int mode;
    public final int elementId;
    public final EditorInfo editorInfo;
    public final boolean deviceLocked;
    public final boolean numberRowEnabled;
    public final boolean numberRowInSymbols;
    public final boolean languageSwitchKeyEnabled;
    public final boolean emojiKeyEnabled;
    public final String customActionLabel;
    public final boolean hasShortcutKey;
    public final boolean isSplitLayout;
    public final boolean oneHandedModeEnabled;
    public final KeyboardLayoutSet.InternalAction internalAction;

    private final int mHashCode;

    public KeyboardId(int elementId, KeyboardLayoutSet.Params params) {
        subtype = params.mSubtype;
        width = params.mKeyboardWidth;
        height = params.mKeyboardHeight;
        mode = params.mMode;
        this.elementId = elementId;
        editorInfo = params.mEditorInfo;
        deviceLocked = params.mDeviceLocked;
        numberRowEnabled = params.mNumberRowEnabled;
        numberRowInSymbols = params.mNumberRowInSymbols;
        languageSwitchKeyEnabled = params.mLanguageSwitchKeyEnabled;
        emojiKeyEnabled = params.mEmojiKeyEnabled;
        customActionLabel = (editorInfo.actionLabel != null)
                ? editorInfo.actionLabel.toString() : null;
        hasShortcutKey = params.mVoiceInputKeyEnabled;
        isSplitLayout = params.mIsSplitLayoutEnabled;
        oneHandedModeEnabled = params.mOneHandedModeEnabled;
        internalAction = params.mInternalAction;

        mHashCode = computeHashCode(this);
    }

    private static int computeHashCode(KeyboardId id) {
        return Objects.hash(
                id.elementId,
                id.mode,
                id.width,
                id.height,
                id.passwordInput(),
                id.deviceLocked,
                id.hasShortcutKey,
                id.numberRowEnabled,
                id.languageSwitchKeyEnabled,
                id.emojiKeyEnabled,
                id.isMultiLine(),
                id.imeAction(),
                id.customActionLabel,
                id.navigateNext(),
                id.navigatePrevious(),
                id.subtype,
                id.isSplitLayout,
                id.internalAction
        );
    }

    private boolean equals(KeyboardId other) {
        if (other == this)
            return true;
        return other.elementId == elementId
                && other.mode == mode
                && other.width == width
                && other.height == height
                && other.passwordInput() == passwordInput()
                && other.deviceLocked == deviceLocked
                && other.hasShortcutKey == hasShortcutKey
                && other.numberRowEnabled == numberRowEnabled
                && other.languageSwitchKeyEnabled == languageSwitchKeyEnabled
                && other.emojiKeyEnabled == emojiKeyEnabled
                && other.isMultiLine() == isMultiLine()
                && other.imeAction() == imeAction()
                && TextUtils.equals(other.customActionLabel, customActionLabel)
                && other.navigateNext() == navigateNext()
                && other.navigatePrevious() == navigatePrevious()
                && other.subtype.equals(subtype)
                && other.isSplitLayout == isSplitLayout
                && Objects.equals(other.internalAction, internalAction);
    }

    private static boolean isAlphabetKeyboard(int elementId) {
        return elementId < ELEMENT_SYMBOLS;
    }

    public boolean isAlphaOrSymbolKeyboard() {
        return elementId <= ELEMENT_SYMBOLS_SHIFTED;
    }

    public boolean isAlphabetKeyboard() {
        return isAlphabetKeyboard(elementId);
    }

    public boolean navigateNext() {
        return (editorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_NEXT) != 0
                || imeAction() == EditorInfo.IME_ACTION_NEXT;
    }

    public boolean navigatePrevious() {
        return (editorInfo.imeOptions & EditorInfo.IME_FLAG_NAVIGATE_PREVIOUS) != 0
                || imeAction() == EditorInfo.IME_ACTION_PREVIOUS;
    }

    public boolean passwordInput() {
        int inputType = editorInfo.inputType;
        return InputTypeUtils.isAnyPasswordInputType(inputType);
    }

    public boolean isMultiLine() {
        return (editorInfo.inputType & InputType.TYPE_TEXT_FLAG_MULTI_LINE) != 0;
    }

    public boolean isAlphabetShifted() {
        return elementId == ELEMENT_ALPHABET_SHIFT_LOCKED || elementId == ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
                || elementId == ELEMENT_ALPHABET_AUTOMATIC_SHIFTED || elementId == ELEMENT_ALPHABET_MANUAL_SHIFTED;
    }

    public boolean isAlphabetShiftedManually() {
        return elementId == ELEMENT_ALPHABET_SHIFT_LOCKED || elementId == ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED
            || elementId == ELEMENT_ALPHABET_MANUAL_SHIFTED;
    }

    public boolean isNumberLayout() {
        return elementId == ELEMENT_NUMBER || elementId == ELEMENT_NUMPAD
                || elementId == ELEMENT_PHONE || elementId == ELEMENT_PHONE_SYMBOLS;
    }

    public boolean isEmojiKeyboard() {
        return elementId >= ELEMENT_EMOJI_RECENTS && elementId <= ELEMENT_EMOJI_CATEGORY16;
    }

    public boolean isEmojiClipBottomRow() {
        return elementId == ELEMENT_CLIPBOARD_BOTTOM_ROW || elementId == ELEMENT_EMOJI_BOTTOM_ROW;
    }

    public int imeAction() {
        return InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);
    }

    public Locale getLocale() {
        return subtype.getLocale();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KeyboardId && equals((KeyboardId) other);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "[%s %s:%s %dx%d %s %s%s%s%s%s%s%s%s%s%s%s]",
                elementIdToName(elementId),
                subtype.getLocale(),
                subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                width,
                height,
                modeName(mode),
                actionName(imeAction()),
                navigateNext() ? " navigateNext" : "",
                navigatePrevious() ? " navigatePrevious" : "",
                deviceLocked ? " deviceLocked" : "",
                passwordInput() ? " passwordInput" : "",
                hasShortcutKey ? " hasShortcutKey" : "",
                numberRowEnabled ? " numberRowEnabled" : "",
                languageSwitchKeyEnabled ? " languageSwitchKeyEnabled" : "",
                emojiKeyEnabled ? " emojiKeyEnabled" : "",
                isMultiLine() ? " isMultiLine" : "",
                isSplitLayout ? " isSplitLayout" : ""
        );
    }

    public static boolean equivalentEditorInfoForKeyboard(EditorInfo a, EditorInfo b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.inputType == b.inputType
                && a.imeOptions == b.imeOptions
                && TextUtils.equals(a.privateImeOptions, b.privateImeOptions);
    }

    public static String elementIdToName(int elementId) {
        return switch (elementId) {
            case ELEMENT_ALPHABET -> "alphabet";
            case ELEMENT_ALPHABET_MANUAL_SHIFTED -> "alphabetManualShifted";
            case ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> "alphabetAutomaticShifted";
            case ELEMENT_ALPHABET_SHIFT_LOCKED -> "alphabetShiftLocked";
            case ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> "alphabetShiftLockShifted";
            case ELEMENT_SYMBOLS -> "symbols";
            case ELEMENT_SYMBOLS_SHIFTED -> "symbolsShifted";
            case ELEMENT_PHONE -> "phone";
            case ELEMENT_PHONE_SYMBOLS -> "phoneSymbols";
            case ELEMENT_NUMBER -> "number";
            case ELEMENT_EMOJI_RECENTS -> "emojiRecents";
            case ELEMENT_EMOJI_CATEGORY1 -> "emojiCategory1";
            case ELEMENT_EMOJI_CATEGORY2 -> "emojiCategory2";
            case ELEMENT_EMOJI_CATEGORY3 -> "emojiCategory3";
            case ELEMENT_EMOJI_CATEGORY4 -> "emojiCategory4";
            case ELEMENT_EMOJI_CATEGORY5 -> "emojiCategory5";
            case ELEMENT_EMOJI_CATEGORY6 -> "emojiCategory6";
            case ELEMENT_EMOJI_CATEGORY7 -> "emojiCategory7";
            case ELEMENT_EMOJI_CATEGORY8 -> "emojiCategory8";
            case ELEMENT_EMOJI_CATEGORY9 -> "emojiCategory9";
            case ELEMENT_EMOJI_CATEGORY10 -> "emojiCategory10";
            case ELEMENT_EMOJI_CATEGORY11 -> "emojiCategory11";
            case ELEMENT_EMOJI_CATEGORY12 -> "emojiCategory12";
            case ELEMENT_EMOJI_CATEGORY13 -> "emojiCategory13";
            case ELEMENT_EMOJI_CATEGORY14 -> "emojiCategory14";
            case ELEMENT_EMOJI_CATEGORY15 -> "emojiCategory15";
            case ELEMENT_EMOJI_CATEGORY16 -> "emojiCategory16";
            case ELEMENT_CLIPBOARD -> "clipboard";
            case ELEMENT_NUMPAD -> "numpad";
            default -> null;
        };
    }

    public static String modeName(int mode) {
        return switch (mode) {
            case MODE_TEXT -> "text";
            case MODE_URL -> "url";
            case MODE_EMAIL -> "email";
            case MODE_IM -> "im";
            case MODE_PHONE -> "phone";
            case MODE_NUMBER -> "number";
            case MODE_DATE -> "date";
            case MODE_TIME -> "time";
            case MODE_DATETIME -> "datetime";
            case MODE_NUMPAD -> "numpad";
            default -> null;
        };
    }

    public static String actionName(int actionId) {
        return (actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL) ? "actionCustomLabel"
                : EditorInfoCompatUtils.imeActionName(actionId);
    }

    public int getKeyboardCapsMode() {
        return switch (elementId) {
            case KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED ->
                WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED;
            case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED -> WordComposer.CAPS_MODE_MANUAL_SHIFTED;
            case KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED -> WordComposer.CAPS_MODE_AUTO_SHIFTED;
            default -> WordComposer.CAPS_MODE_OFF;
        };
    }
}
