/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.graphics.Typeface;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.ColorType;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

public final class KeyVisualAttributes {
    @Nullable
    public final Typeface mTypeface;

    public final float mLetterRatio;
    public final int mLetterSize;
    public final float mLabelRatio;
    public final int mLabelSize;
    public final float mLargeLetterRatio;
    public final float mHintLetterRatio;
    public final float mShiftedLetterHintRatio;
    public final float mHintLabelRatio;
    public final float mPreviewTextRatio;

    public final int mTextColor;
    public final int mTextInactivatedColor;
    public final int mTextShadowColor;
    public final int mFunctionalTextColor;
    public final int mHintLetterColor;
    public final int mHintLabelColor;
    public final int mShiftedLetterHintInactivatedColor;
    public final int mShiftedLetterHintActivatedColor;
    public final int mPreviewTextColor;

    public final float mHintLabelVerticalAdjustment;
    public final float mLabelOffCenterRatio;
    public final float mHintLabelOffCenterRatio;

    private static final int[] VISUAL_ATTRIBUTE_IDS = {
        R.styleable.Keyboard_Key_keyTypeface,
        R.styleable.Keyboard_Key_keyLetterSize,
        R.styleable.Keyboard_Key_keyLabelSize,
        R.styleable.Keyboard_Key_keyLargeLetterRatio,
        R.styleable.Keyboard_Key_keyHintLetterRatio,
        R.styleable.Keyboard_Key_keyShiftedLetterHintRatio,
        R.styleable.Keyboard_Key_keyHintLabelRatio,
        R.styleable.Keyboard_Key_keyPreviewTextRatio,
        R.styleable.Keyboard_Key_keyTextColor,
        R.styleable.Keyboard_Key_keyTextInactivatedColor,
        R.styleable.Keyboard_Key_keyTextShadowColor,
        R.styleable.Keyboard_Key_functionalTextColor,
        R.styleable.Keyboard_Key_keyHintLetterColor,
        R.styleable.Keyboard_Key_keyHintLabelColor,
        R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor,
        R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor,
        R.styleable.Keyboard_Key_keyPreviewTextColor,
        R.styleable.Keyboard_Key_keyHintLabelVerticalAdjustment,
        R.styleable.Keyboard_Key_keyLabelOffCenterRatio,
        R.styleable.Keyboard_Key_keyHintLabelOffCenterRatio
    };
    private static final SparseIntArray sVisualAttributeIds = new SparseIntArray();
    private static final int ATTR_DEFINED = 1;
    private static final int ATTR_NOT_FOUND = 0;
    static {
        for (final int attrId : VISUAL_ATTRIBUTE_IDS) {
            sVisualAttributeIds.put(attrId, ATTR_DEFINED);
        }
    }

    @Nullable
    public static KeyVisualAttributes newInstance(@NonNull final TypedArray keyAttr) {
        final int indexCount = keyAttr.getIndexCount();
        for (int i = 0; i < indexCount; i++) {
            final int attrId = keyAttr.getIndex(i);
            if (sVisualAttributeIds.get(attrId, ATTR_NOT_FOUND) == ATTR_NOT_FOUND) {
                continue;
            }
            return new KeyVisualAttributes(keyAttr);
        }
        return null;
    }

    private KeyVisualAttributes(@NonNull final TypedArray keyAttr) {
        if (keyAttr.hasValue(R.styleable.Keyboard_Key_keyTypeface)) {
            mTypeface = Typeface.defaultFromStyle(
                    keyAttr.getInt(R.styleable.Keyboard_Key_keyTypeface, Typeface.NORMAL));
        } else {
            mTypeface = null;
        }

        mLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLetterSize);
        mLetterSize = ResourceUtils.getDimensionPixelSize(keyAttr,
                R.styleable.Keyboard_Key_keyLetterSize);
        mLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLabelSize);
        mLabelSize = ResourceUtils.getDimensionPixelSize(keyAttr,
                R.styleable.Keyboard_Key_keyLabelSize);
        mLargeLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLargeLetterRatio);
        mHintLetterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLetterRatio);
        mShiftedLetterHintRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyShiftedLetterHintRatio);
        mHintLabelRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLabelRatio);
        mPreviewTextRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyPreviewTextRatio);

        final Colors colors = Settings.getInstance().getCurrent().mColors;
        mTextColor = colors.get(ColorType.KEY_TEXT); //keyAttr.getColor(R.styleable.Keyboard_Key_keyTextColor, 0);
        mTextInactivatedColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyTextInactivatedColor, 0);
        mTextShadowColor = keyAttr.getColor(R.styleable.Keyboard_Key_keyTextShadowColor, 0);
        // todo: maybe a separate color?
        mFunctionalTextColor = colors.get(ColorType.KEY_TEXT); //keyAttr.getColor(R.styleable.Keyboard_Key_functionalTextColor, 0);
        mHintLetterColor = colors.get(ColorType.KEY_HINT_TEXT); //keyAttr.getColor(R.styleable.Keyboard_Key_keyHintLetterColor, 0);
        mHintLabelColor = colors.get(ColorType.KEY_TEXT); //keyAttr.getColor(R.styleable.Keyboard_Key_keyHintLabelColor, 0);
        mShiftedLetterHintInactivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyShiftedLetterHintInactivatedColor, 0);
        mShiftedLetterHintActivatedColor = keyAttr.getColor(
                R.styleable.Keyboard_Key_keyShiftedLetterHintActivatedColor, 0);
        // todo: maybe a separate color?
        mPreviewTextColor = colors.get(ColorType.KEY_TEXT); //keyAttr.getColor(R.styleable.Keyboard_Key_keyPreviewTextColor, 0);

        mHintLabelVerticalAdjustment = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLabelVerticalAdjustment, 0.0f);
        mLabelOffCenterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyLabelOffCenterRatio, 0.0f);
        mHintLabelOffCenterRatio = ResourceUtils.getFraction(keyAttr,
                R.styleable.Keyboard_Key_keyHintLabelOffCenterRatio, 0.0f);
    }
}
