/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.util.SparseIntArray;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.KeyboardId;
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.LocaleKeyTexts;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

public class KeyboardParams {
    private static final int DEFAULT_KEYBOARD_COLUMNS = 10;
    private static final int DEFAULT_KEYBOARD_ROWS = 4;

    public KeyboardId mId;
    public int mThemeId;

    /** Total height and width of the keyboard, including the paddings and keys */
    public int mOccupiedHeight;
    public int mOccupiedWidth;

    /** Base height and width of the keyboard used to calculate rows' or keys' heights and
     *  widths
     */
    public int mBaseHeight;
    public int mBaseWidth;

    public int mTopPadding;
    public int mBottomPadding;
    public int mLeftPadding;
    public int mRightPadding;

    @Nullable
    public KeyVisualAttributes mKeyVisualAttributes;

    public float mDefaultRelativeRowHeight;
    public float mDefaultRelativeKeyWidth;
    public float mRelativeHorizontalGap;
    public float mRelativeVerticalGap;
    // relative values multiplied with baseHeight / baseWidth
    public int mDefaultRowHeight;
    public int mDefaultKeyWidth;
    public int mHorizontalGap;
    public int mVerticalGap;

    public int mMoreKeysTemplate;
    public int mMaxMoreKeysKeyboardColumn;

    public int GRID_WIDTH;
    public int GRID_HEIGHT;

    // Keys are sorted from top-left to bottom-right order.
    @NonNull
    public final SortedSet<Key> mSortedKeys = new TreeSet<>(ROW_COLUMN_COMPARATOR);
    @NonNull
    public final ArrayList<Key> mShiftKeys = new ArrayList<>();
    @NonNull
    public final ArrayList<Key> mAltCodeKeysWhileTyping = new ArrayList<>();
    @NonNull
    public final KeyboardIconsSet mIconsSet = new KeyboardIconsSet();
    @NonNull
    public final KeyboardTextsSet mTextsSet = new KeyboardTextsSet();
    @NonNull
    public final KeyStylesSet mKeyStyles = new KeyStylesSet(mTextsSet);

    @NonNull
    private final UniqueKeysCache mUniqueKeysCache;
    public boolean mAllowRedundantMoreKeys;
    @NonNull
    public LocaleKeyTexts mLocaleKeyTexts;

    public int mMostCommonKeyHeight = 0;
    public int mMostCommonKeyWidth = 0;

    public boolean mProximityCharsCorrectionEnabled;

    @NonNull
    public final TouchPositionCorrection mTouchPositionCorrection =
            new TouchPositionCorrection();

    // Comparator to sort {@link Key}s from top-left to bottom-right order.
    private static final Comparator<Key> ROW_COLUMN_COMPARATOR = (lhs, rhs) -> {
        if (lhs.getY() < rhs.getY()) return -1;
        if (lhs.getY() > rhs.getY()) return 1;
        if (lhs.getX() < rhs.getX()) return -1;
        if (lhs.getX() > rhs.getX()) return 1;
        return 0;
    };

    public KeyboardParams() {
        this(UniqueKeysCache.NO_CACHE);
    }

    public KeyboardParams(@NonNull final UniqueKeysCache keysCache) {
        mUniqueKeysCache = keysCache;
    }

    protected void clearKeys() {
        mSortedKeys.clear();
        mShiftKeys.clear();
        clearHistogram();
    }

    public void onAddKey(@NonNull final Key newKey) {
        final Key key = mUniqueKeysCache.getUniqueKey(newKey);
        final boolean isSpacer = key.isSpacer();
        if (isSpacer && key.getWidth() == 0) {
            // Ignore zero width {@link Spacer}.
            return;
        }
        mSortedKeys.add(key);
        if (isSpacer) {
            return;
        }
        updateHistogram(key);
        if (key.getCode() == Constants.CODE_SHIFT) {
            mShiftKeys.add(key);
        }
        if (key.altCodeWhileTyping()) {
            mAltCodeKeysWhileTyping.add(key);
        }
    }

    public void removeRedundantMoreKeys() {
        if (mAllowRedundantMoreKeys) {
            return;
        }
        final MoreKeySpec.LettersOnBaseLayout lettersOnBaseLayout =
                new MoreKeySpec.LettersOnBaseLayout();
        for (final Key key : mSortedKeys) {
            lettersOnBaseLayout.addLetter(key);
        }
        final ArrayList<Key> allKeys = new ArrayList<>(mSortedKeys);
        mSortedKeys.clear();
        for (final Key key : allKeys) {
            final Key filteredKey = Key.removeRedundantMoreKeys(key, lettersOnBaseLayout);
            mSortedKeys.add(mUniqueKeysCache.getUniqueKey(filteredKey));
        }
    }

    private int mMaxHeightCount = 0;
    private int mMaxWidthCount = 0;
    private final SparseIntArray mHeightHistogram = new SparseIntArray();
    private final SparseIntArray mWidthHistogram = new SparseIntArray();

    private void clearHistogram() {
        mMostCommonKeyHeight = 0;
        mMaxHeightCount = 0;
        mHeightHistogram.clear();

        mMaxWidthCount = 0;
        mMostCommonKeyWidth = 0;
        mWidthHistogram.clear();
    }

    private static int updateHistogramCounter(final SparseIntArray histogram, final int key) {
        final int index = histogram.indexOfKey(key);
        final int count = (index >= 0 ? histogram.get(key) : 0) + 1;
        histogram.put(key, count);
        return count;
    }

    private void updateHistogram(final Key key) {
        final int height = key.getHeight() + mVerticalGap;
        final int heightCount = updateHistogramCounter(mHeightHistogram, height);
        if (heightCount > mMaxHeightCount) {
            mMaxHeightCount = heightCount;
            mMostCommonKeyHeight = height;
        }

        final int width = key.getWidth() + mHorizontalGap;
        final int widthCount = updateHistogramCounter(mWidthHistogram, width);
        if (widthCount > mMaxWidthCount) {
            mMaxWidthCount = widthCount;
            mMostCommonKeyWidth = width;
        }
    }

    // when attr is null, default attributes will be loaded
    //  these are good for basic keyboards already, but have wrong/unsuitable sizes e.g. for emojis,
    //  moreKeys and moreSuggestions
    public void readAttributes(final Context context, @Nullable final AttributeSet attr) {
        final TypedArray keyboardAttr = context.obtainStyledAttributes(
                attr, R.styleable.Keyboard, R.attr.keyboardStyle, R.style.Keyboard);
        final TypedArray keyAttr;
        if (attr == null)
            keyAttr = context.obtainStyledAttributes(attr, R.styleable.Keyboard_Key);
        else
            keyAttr = context.getResources().obtainAttributes(attr, R.styleable.Keyboard_Key);
        try {
            final int height = mId.mHeight;
            final int width = mId.mWidth;
            mOccupiedHeight = height;
            mOccupiedWidth = width;
            mTopPadding = (int) keyboardAttr.getFraction(
                    R.styleable.Keyboard_keyboardTopPadding, height, height, 0);
            mBottomPadding = (int) (keyboardAttr.getFraction(
                    R.styleable.Keyboard_keyboardBottomPadding, height, height, 0)
                    * Settings.getInstance().getCurrent().mBottomPaddingScale);
            mLeftPadding = (int) keyboardAttr.getFraction(
                    R.styleable.Keyboard_keyboardLeftPadding, width, width, 0);
            mRightPadding = (int) keyboardAttr.getFraction(
                    R.styleable.Keyboard_keyboardRightPadding, width, width, 0);

            mBaseWidth = mOccupiedWidth - mLeftPadding - mRightPadding;
            mDefaultRelativeKeyWidth = keyAttr.getFraction(R.styleable.Keyboard_Key_keyWidth,
                    1, 1, 1f / DEFAULT_KEYBOARD_COLUMNS);
            mDefaultKeyWidth = (int) (mDefaultRelativeKeyWidth * mBaseWidth);

            // todo: maybe settings should not be accessed from here?
            if (Settings.getInstance().getCurrent().mNarrowKeyGaps) {
                mRelativeHorizontalGap = keyboardAttr.getFraction(
                        R.styleable.Keyboard_horizontalGapNarrow, 1, 1, 0);
                mRelativeVerticalGap = keyboardAttr.getFraction(
                        R.styleable.Keyboard_verticalGapNarrow, 1, 1, 0);
            } else {
                mRelativeHorizontalGap = keyboardAttr.getFraction(
                        R.styleable.Keyboard_horizontalGap, 1, 1, 0);
                mRelativeVerticalGap = keyboardAttr.getFraction(
                        R.styleable.Keyboard_verticalGap, 1, 1, 0);
                // TODO: Fix keyboard geometry calculation clearer. Historically vertical gap between
                //  rows are determined based on the entire keyboard height including top and bottom
                //  paddings.
            }
            mHorizontalGap = (int) (mRelativeHorizontalGap * width);
            mVerticalGap = (int) (mRelativeVerticalGap * height);

            mBaseHeight = mOccupiedHeight - mTopPadding - mBottomPadding + mVerticalGap;
            mDefaultRelativeRowHeight = ResourceUtils.getDimensionOrFraction(keyboardAttr,
                    R.styleable.Keyboard_rowHeight, 1, 1f / DEFAULT_KEYBOARD_ROWS);
            if (mDefaultRelativeRowHeight > 1) { // can be absolute size, in that case will be > 1
                mDefaultRowHeight = (int) mDefaultRelativeRowHeight;
                mDefaultRelativeRowHeight *= -1; // make it negative when it's absolute
            } else {
                mDefaultRowHeight = (int) (mDefaultRelativeRowHeight * mBaseHeight);
            }

            mKeyVisualAttributes = KeyVisualAttributes.newInstance(keyAttr);

            mMoreKeysTemplate = keyboardAttr.getResourceId(R.styleable.Keyboard_moreKeysTemplate, 0);
            mMaxMoreKeysKeyboardColumn = keyAttr.getInt(R.styleable.Keyboard_Key_maxMoreKeysColumn, 5);

            mThemeId = keyboardAttr.getInt(R.styleable.Keyboard_themeId, 0);
            mIconsSet.loadIcons(keyboardAttr);
            mTextsSet.setLocale(mId.getLocale(), context);

            final int resourceId = keyboardAttr.getResourceId(R.styleable.Keyboard_touchPositionCorrectionData, 0);
            if (resourceId != 0) {
                final String[] data = context.getResources().getStringArray(resourceId);
                mTouchPositionCorrection.load(data);
            }
        } finally {
            keyAttr.recycle();
            keyboardAttr.recycle();
        }
    }
}
