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
import helium314.keyboard.latin.utils.InputTypeUtils;

import java.util.Locale;
import java.util.Objects;

import static helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;

/**
 * Unique identifier for each keyboard type.
 */
public final class KeyboardId {
    public final RichInputMethodSubtype subtype;
    public final int width;
    public final int height;
    public final KeyboardMode mode;
    public final KeyboardElement element;
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

    public KeyboardId(KeyboardElement element, KeyboardLayoutSet.Params params) {
        subtype = params.mSubtype;
        width = params.mKeyboardWidth;
        height = params.mKeyboardHeight;
        mode = params.mMode;
        this.element = element;
        editorInfo = params.mEditorInfo;
        deviceLocked = params.mDeviceLocked;
        numberRowEnabled = params.mNumberRowEnabled;
        numberRowInSymbols = params.mNumberRowInSymbols;
        languageSwitchKeyEnabled = params.mLanguageSwitchKeyEnabled;
        emojiKeyEnabled = params.mEmojiKeyEnabled;
        customActionLabel = editorInfo.actionLabel != null
                ? editorInfo.actionLabel.toString() : null;
        hasShortcutKey = params.mVoiceInputKeyEnabled;
        isSplitLayout = params.mIsSplitLayoutEnabled;
        oneHandedModeEnabled = params.mOneHandedModeEnabled;
        internalAction = params.mInternalAction;

        mHashCode = computeHashCode(this);
    }

    private static int computeHashCode(KeyboardId id) {
        return Objects.hash(
                id.element,
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

    public int imeAction() {
        return InputTypeUtils.getImeOptionsActionIdFromEditorInfo(editorInfo);
    }

    public Locale getLocale() {
        return subtype.getLocale();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof KeyboardId id
            && id.element == element
            && id.mode == mode
            && id.width == width
            && id.height == height
            && id.passwordInput() == passwordInput()
            && id.deviceLocked == deviceLocked
            && id.hasShortcutKey == hasShortcutKey
            && id.numberRowEnabled == numberRowEnabled
            && id.languageSwitchKeyEnabled == languageSwitchKeyEnabled
            && id.emojiKeyEnabled == emojiKeyEnabled
            && id.isMultiLine() == isMultiLine()
            && id.imeAction() == imeAction()
            && TextUtils.equals(id.customActionLabel, customActionLabel)
            && id.navigateNext() == navigateNext()
            && id.navigatePrevious() == navigatePrevious()
            && id.subtype.equals(subtype)
            && id.isSplitLayout == isSplitLayout
            && Objects.equals(id.internalAction, internalAction);
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public String toString() {
        return String.format(Locale.ROOT, "[%s %s:%s %dx%d %s %s%s%s%s%s%s%s%s%s%s%s]",
                element,
                subtype.getLocale(),
                subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET),
                width,
                height,
                mode,
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

    public static String actionName(int actionId) {
        return (actionId == InputTypeUtils.IME_ACTION_CUSTOM_LABEL) ? "actionCustomLabel"
                : EditorInfoCompatUtils.imeActionName(actionId);
    }
}
