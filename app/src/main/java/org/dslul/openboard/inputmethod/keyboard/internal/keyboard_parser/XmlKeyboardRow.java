/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.Xml;

import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;
import org.xmlpull.v1.XmlPullParser;

import java.util.ArrayDeque;

/**
 * Container for keys in the keyboard. All keys in a row are at the same Y-coordinate.
 * Some of the key size defaults can be overridden per row from what the {@link Keyboard}
 * defines.
 */
public final class XmlKeyboardRow {
    // keyWidth enum constants
    private static final int KEYWIDTH_NOT_ENUM = 0;
    private static final int KEYWIDTH_FILL_RIGHT = -1;

    private final KeyboardParams mParams;
    /** The height of this row. */
    private final int mRowHeight;
    final public float mRelativeRowHeight;

    private final ArrayDeque<RowAttributes> mRowAttributesStack = new ArrayDeque<>();

    // TODO: Add keyActionFlags.
    private static class RowAttributes {
        /** Default width of a key in this row. */
        public final float mDefaultKeyWidth;
        /** Default keyLabelFlags in this row. */
        public final int mDefaultKeyLabelFlags;
        /** Default backgroundType for this row */
        public final int mDefaultBackgroundType;

        /**
         * Parse and create key attributes. This constructor is used to parse Row tag.
         *
         * @param keyAttr an attributes array of Row tag.
         * @param defaultKeyWidth a default key width.
         * @param keyboardWidth the keyboard width that is required to calculate keyWidth attribute.
         */
        public RowAttributes(final TypedArray keyAttr, final float defaultKeyWidth,
                final int keyboardWidth) {
            mDefaultKeyWidth = keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                    keyboardWidth, keyboardWidth, defaultKeyWidth);
            mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0);
            mDefaultBackgroundType = keyAttr.getInt(R.styleable.Keyboard_Key_backgroundType,
                    Key.BACKGROUND_TYPE_NORMAL);
        }

        /**
         * Parse and update key attributes using default attributes. This constructor is used
         * to parse include tag.
         *
         * @param keyAttr an attributes array of include tag.
         * @param defaultRowAttr default Row attributes.
         * @param keyboardWidth the keyboard width that is required to calculate keyWidth attribute.
         */
        public RowAttributes(final TypedArray keyAttr, final RowAttributes defaultRowAttr,
                final int keyboardWidth) {
            mDefaultKeyWidth = keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                    keyboardWidth, keyboardWidth, defaultRowAttr.mDefaultKeyWidth);
            mDefaultKeyLabelFlags = keyAttr.getInt(R.styleable.Keyboard_Key_keyLabelFlags, 0)
                    | defaultRowAttr.mDefaultKeyLabelFlags;
            mDefaultBackgroundType = keyAttr.getInt(R.styleable.Keyboard_Key_backgroundType,
                    defaultRowAttr.mDefaultBackgroundType);
        }
    }

    private final int mCurrentY;
    // Will be updated by {@link Key}'s constructor.
    private float mCurrentX;

    public XmlKeyboardRow(final Resources res, final KeyboardParams params,
                          final XmlPullParser parser, final int y) {
        mParams = params;
        final TypedArray keyboardAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard);
        float relativeRowHeight = ResourceUtils.getDimensionOrFraction(keyboardAttr,
                R.styleable.Keyboard_rowHeight, 1, params.mDefaultRelativeRowHeight);
        keyboardAttr.recycle();
        if (relativeRowHeight > 1) {
            mRelativeRowHeight = -relativeRowHeight;
            mRowHeight = (int) relativeRowHeight;
        } else {
            mRelativeRowHeight = relativeRowHeight;
            mRowHeight = (int) (relativeRowHeight * params.mBaseHeight);
        }
        final TypedArray keyAttr = res.obtainAttributes(Xml.asAttributeSet(parser),
                R.styleable.Keyboard_Key);
        mRowAttributesStack.push(new RowAttributes(
                keyAttr, params.mDefaultKeyWidth, params.mBaseWidth));
        keyAttr.recycle();

        mCurrentY = y;
        mCurrentX = 0.0f;
    }

    public int getRowHeight() {
        return mRowHeight;
    }

    public void pushRowAttributes(final TypedArray keyAttr) {
        final RowAttributes newAttributes = new RowAttributes(
                keyAttr, mRowAttributesStack.peek(), mParams.mBaseWidth);
        mRowAttributesStack.push(newAttributes);
    }

    public void popRowAttributes() {
        mRowAttributesStack.pop();
    }

    public float getDefaultKeyWidth() {
        return mRowAttributesStack.peek().mDefaultKeyWidth;
    }

    public int getDefaultKeyLabelFlags() {
        return mRowAttributesStack.peek().mDefaultKeyLabelFlags;
    }

    public int getDefaultBackgroundType() {
        return mRowAttributesStack.peek().mDefaultBackgroundType;
    }

    public void setXPos(final float keyXPos) {
        mCurrentX = keyXPos;
    }

    public void advanceXPos(final float width) {
        mCurrentX += width;
    }

    public int getKeyY() {
        return mCurrentY;
    }

    public float getKeyX(final TypedArray keyAttr) {
        if (keyAttr == null || !keyAttr.hasValue(R.styleable.Keyboard_Key_keyXPos)) {
            return mCurrentX;
        }
        final float keyXPos = keyAttr.getFraction(R.styleable.Keyboard_Key_keyXPos,
                mParams.mBaseWidth, mParams.mBaseWidth, 0);
        if (keyXPos >= 0) {
            return keyXPos + mParams.mLeftPadding;
        }
        // If keyXPos is negative, the actual x-coordinate will be
        // keyboardWidth + keyXPos.
        // keyXPos shouldn't be less than mCurrentX because drawable area for this
        // key starts at mCurrentX. Or, this key will overlaps the adjacent key on
        // its left hand side.
        final int keyboardRightEdge = mParams.mOccupiedWidth - mParams.mRightPadding;
        return Math.max(keyXPos + keyboardRightEdge, mCurrentX);
    }

    public float getRelativeKeyWidth(final TypedArray keyAttr) {
        if (keyAttr == null)
            return mParams.mDefaultRelativeKeyWidth;
        final int widthType = ResourceUtils.getEnumValue(keyAttr,
                R.styleable.Keyboard_Key_keyWidth, KEYWIDTH_NOT_ENUM);
        if (widthType == KEYWIDTH_FILL_RIGHT)
            return -1;
        return keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                1, 1, mParams.mDefaultRelativeKeyWidth);
    }

    public float getKeyWidth(final TypedArray keyAttr, final float keyXPos) {
        if (keyAttr == null) {
            return getDefaultKeyWidth();
        }
        final int widthType = ResourceUtils.getEnumValue(keyAttr,
                R.styleable.Keyboard_Key_keyWidth, KEYWIDTH_NOT_ENUM);
        switch (widthType) {
        case KEYWIDTH_FILL_RIGHT:
            // If keyWidth is fillRight, the actual key width will be determined to fill
            // out the area up to the right edge of the keyboard.
            final int keyboardRightEdge = mParams.mOccupiedWidth - mParams.mRightPadding;
            return keyboardRightEdge - keyXPos;
        default: // KEYWIDTH_NOT_ENUM
            return keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                    mParams.mBaseWidth, mParams.mBaseWidth, getDefaultKeyWidth());
        }
    }
}
