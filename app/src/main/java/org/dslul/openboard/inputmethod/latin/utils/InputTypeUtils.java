/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.text.InputType;
import android.view.inputmethod.EditorInfo;

public final class InputTypeUtils implements InputType {
    private static final int WEB_TEXT_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_WEB_PASSWORD;
    private static final int NUMBER_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_NUMBER | TYPE_NUMBER_VARIATION_PASSWORD;
    private static final int TEXT_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_PASSWORD;
    private static final int TEXT_VISIBLE_PASSWORD_INPUT_TYPE =
            TYPE_CLASS_TEXT | TYPE_TEXT_VARIATION_VISIBLE_PASSWORD;
    private static final int[] SUPPRESSING_AUTO_SPACES_FIELD_VARIATION = {
        InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
        InputType.TYPE_TEXT_VARIATION_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
        InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD };
    public static final int IME_ACTION_CUSTOM_LABEL = EditorInfo.IME_MASK_ACTION + 1;

    private InputTypeUtils() {
        // This utility class is not publicly instantiable.
    }

    private static boolean isWebPasswordInputType(final int inputType) {
        return inputType == WEB_TEXT_PASSWORD_INPUT_TYPE;
    }

    private static boolean isNumberPasswordInputType(final int inputType) {
        return inputType == NUMBER_PASSWORD_INPUT_TYPE;
    }

    private static boolean isTextPasswordInputType(final int inputType) {
        return inputType == TEXT_PASSWORD_INPUT_TYPE;
    }

    private static boolean isWebEmailAddressVariation(int variation) {
        return variation == TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS;
    }

    public static boolean isEmailVariation(final int variation) {
        return variation == TYPE_TEXT_VARIATION_EMAIL_ADDRESS
                || isWebEmailAddressVariation(variation);
    }

    public static boolean isUriOrEmailType(final int inputType) {
        if ((inputType & TYPE_MASK_CLASS) != TYPE_CLASS_TEXT) return false;
        final int maskedInputType = inputType & TYPE_MASK_VARIATION;
        return maskedInputType == TYPE_TEXT_VARIATION_URI || isEmailVariation(maskedInputType);
    }

    // Please refer to TextView.isPasswordInputType
    public static boolean isPasswordInputType(final int inputType) {
        final int maskedInputType = inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return isTextPasswordInputType(maskedInputType) || isWebPasswordInputType(maskedInputType)
                || isNumberPasswordInputType(maskedInputType);
    }

    // Please refer to TextView.isVisiblePasswordInputType
    public static boolean isVisiblePasswordInputType(final int inputType) {
        final int maskedInputType = inputType & (TYPE_MASK_CLASS | TYPE_MASK_VARIATION);
        return maskedInputType == TEXT_VISIBLE_PASSWORD_INPUT_TYPE;
    }

    public static boolean isAutoSpaceFriendlyType(final int inputType) {
        if (TYPE_CLASS_TEXT != (TYPE_MASK_CLASS & inputType)) return false;
        final int variation = TYPE_MASK_VARIATION & inputType;
        for (final int fieldVariation : SUPPRESSING_AUTO_SPACES_FIELD_VARIATION) {
            if (variation == fieldVariation) return false;
        }
        return true;
    }

    public static int getImeOptionsActionIdFromEditorInfo(final EditorInfo editorInfo) {
        if ((editorInfo.imeOptions & EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return EditorInfo.IME_ACTION_NONE;
        } else if (editorInfo.actionLabel != null) {
            return IME_ACTION_CUSTOM_LABEL;
        } else {
            // Note: this is different from editorInfo.actionId, hence "ImeOptionsActionId"
            return editorInfo.imeOptions & EditorInfo.IME_MASK_ACTION;
        }
    }
}
