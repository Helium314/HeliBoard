/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard;

import android.content.Context;
import android.graphics.Paint;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardBuilder;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams;
import org.dslul.openboard.inputmethod.keyboard.internal.MoreKeySpec;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.utils.TypefaceUtils;

public final class MoreKeysKeyboard extends Keyboard {
    private final int mDefaultKeyCoordX;

    MoreKeysKeyboard(final MoreKeysKeyboardParams params) {
        super(params);
        mDefaultKeyCoordX = params.getDefaultKeyCoordX() + params.mDefaultKeyWidth / 2;
    }

    public int getDefaultCoordX() {
        return mDefaultKeyCoordX;
    }

    @UsedForTesting
    static class MoreKeysKeyboardParams extends KeyboardParams {
        public boolean mIsMoreKeysFixedOrder;
        /* package */int mTopRowAdjustment;
        public int mNumRows;
        public int mNumColumns;
        public int mTopKeys;
        public int mLeftKeys;
        public int mRightKeys; // includes default key.
        public int mDividerWidth;
        public int mColumnWidth;

        public MoreKeysKeyboardParams() {
            super();
        }

        /**
         * Set keyboard parameters of more keys keyboard.
         *
         * @param numKeys number of keys in this more keys keyboard.
         * @param numColumn number of columns of this more keys keyboard.
         * @param keyWidth more keys keyboard key width in pixel, including horizontal gap.
         * @param rowHeight more keys keyboard row height in pixel, including vertical gap.
         * @param coordXInParent coordinate x of the key preview in parent keyboard.
         * @param parentKeyboardWidth parent keyboard width in pixel.
         * @param isMoreKeysFixedColumn true if more keys keyboard should have
         *   <code>numColumn</code> columns. Otherwise more keys keyboard should have
         *   <code>numColumn</code> columns at most.
         * @param isMoreKeysFixedOrder true if the order of more keys is determined by the order in
         *   the more keys' specification. Otherwise the order of more keys is automatically
         *   determined.
         * @param dividerWidth width of divider, zero for no dividers.
         */
        public void setParameters(final int numKeys, final int numColumn, final int keyWidth,
                final int rowHeight, final int coordXInParent, final int parentKeyboardWidth,
                final boolean isMoreKeysFixedColumn, final boolean isMoreKeysFixedOrder,
                final int dividerWidth) {
            mIsMoreKeysFixedOrder = isMoreKeysFixedOrder;
            if (parentKeyboardWidth / keyWidth < Math.min(numKeys, numColumn)) {
                throw new IllegalArgumentException("Keyboard is too small to hold more keys: "
                        + parentKeyboardWidth + " " + keyWidth + " " + numKeys + " " + numColumn);
            }
            mDefaultKeyWidth = keyWidth;
            mDefaultRowHeight = rowHeight;

            mNumRows = (numKeys + numColumn - 1) / numColumn;
            final int numColumns = isMoreKeysFixedColumn ? Math.min(numKeys, numColumn)
                    : getOptimizedColumns(numKeys, numColumn);
            mNumColumns = numColumns;
            final int topKeys = numKeys % numColumns;
            mTopKeys = topKeys == 0 ? numColumns : topKeys;

            final int numLeftKeys = (numColumns - 1) / 2;
            final int numRightKeys = numColumns - numLeftKeys; // including default key.
            // Maximum number of keys we can layout both side of the parent key
            final int maxLeftKeys = coordXInParent / keyWidth;
            final int maxRightKeys = (parentKeyboardWidth - coordXInParent) / keyWidth;
            int leftKeys, rightKeys;
            if (numLeftKeys > maxLeftKeys) {
                leftKeys = maxLeftKeys;
                rightKeys = numColumns - leftKeys;
            } else if (numRightKeys > maxRightKeys + 1) {
                rightKeys = maxRightKeys + 1; // include default key
                leftKeys = numColumns - rightKeys;
            } else {
                leftKeys = numLeftKeys;
                rightKeys = numRightKeys;
            }
            // If the left keys fill the left side of the parent key, entire more keys keyboard
            // should be shifted to the right unless the parent key is on the left edge.
            if (maxLeftKeys == leftKeys && leftKeys > 0) {
                leftKeys--;
                rightKeys++;
            }
            // If the right keys fill the right side of the parent key, entire more keys
            // should be shifted to the left unless the parent key is on the right edge.
            if (maxRightKeys == rightKeys - 1 && rightKeys > 1) {
                leftKeys++;
                rightKeys--;
            }
            mLeftKeys = leftKeys;
            mRightKeys = rightKeys;

            // Adjustment of the top row.
            mTopRowAdjustment = isMoreKeysFixedOrder ? getFixedOrderTopRowAdjustment()
                    : getAutoOrderTopRowAdjustment();
            mDividerWidth = dividerWidth;
            mColumnWidth = mDefaultKeyWidth + mDividerWidth;
            mBaseWidth = mOccupiedWidth = mNumColumns * mColumnWidth - mDividerWidth;
            // Need to subtract the bottom row's gutter only.
            mBaseHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight - mVerticalGap
                    + mTopPadding + mBottomPadding;
        }

        private int getFixedOrderTopRowAdjustment() {
            if (mNumRows == 1 || mTopKeys % 2 == 1 || mTopKeys == mNumColumns
                    || mLeftKeys == 0  || mRightKeys == 1) {
                return 0;
            }
            return -1;
        }

        private int getAutoOrderTopRowAdjustment() {
            if (mNumRows == 1 || mTopKeys == 1 || mNumColumns % 2 == mTopKeys % 2
                    || mLeftKeys == 0 || mRightKeys == 1) {
                return 0;
            }
            return -1;
        }

        // Return key position according to column count (0 is default).
        /* package */int getColumnPos(final int n) {
            return mIsMoreKeysFixedOrder ? getFixedOrderColumnPos(n) : getAutomaticColumnPos(n);
        }

        private int getFixedOrderColumnPos(final int n) {
            final int col = n % mNumColumns;
            final int row = n / mNumColumns;
            if (!isTopRow(row)) {
                return col - mLeftKeys;
            }
            final int rightSideKeys = mTopKeys / 2;
            final int leftSideKeys = mTopKeys - (rightSideKeys + 1);
            final int pos = col - leftSideKeys;
            final int numLeftKeys = mLeftKeys + mTopRowAdjustment;
            final int numRightKeys = mRightKeys - 1;
            if (numRightKeys >= rightSideKeys && numLeftKeys >= leftSideKeys) {
                return pos;
            } else if (numRightKeys < rightSideKeys) {
                return pos - (rightSideKeys - numRightKeys);
            } else { // numLeftKeys < leftSideKeys
                return pos + (leftSideKeys - numLeftKeys);
            }
        }

        private int getAutomaticColumnPos(final int n) {
            final int col = n % mNumColumns;
            final int row = n / mNumColumns;
            int leftKeys = mLeftKeys;
            if (isTopRow(row)) {
                leftKeys += mTopRowAdjustment;
            }
            if (col == 0) {
                // default position.
                return 0;
            }

            int pos = 0;
            int right = 1; // include default position key.
            int left = 0;
            int i = 0;
            while (true) {
                // Assign right key if available.
                if (right < mRightKeys) {
                    pos = right;
                    right++;
                    i++;
                }
                if (i >= col)
                    break;
                // Assign left key if available.
                if (left < leftKeys) {
                    left++;
                    pos = -left;
                    i++;
                }
                if (i >= col)
                    break;
            }
            return pos;
        }

        private static int getTopRowEmptySlots(final int numKeys, final int numColumns) {
            final int remainings = numKeys % numColumns;
            return remainings == 0 ? 0 : numColumns - remainings;
        }

        private int getOptimizedColumns(final int numKeys, final int maxColumns) {
            int numColumns = Math.min(numKeys, maxColumns);
            while (getTopRowEmptySlots(numKeys, numColumns) >= mNumRows) {
                numColumns--;
            }
            return numColumns;
        }

        public int getDefaultKeyCoordX() {
            return mLeftKeys * mColumnWidth + mLeftPadding;
        }

        public int getX(final int n, final int row) {
            final int x = getColumnPos(n) * mColumnWidth + getDefaultKeyCoordX();
            if (isTopRow(row)) {
                return x + mTopRowAdjustment * (mColumnWidth / 2);
            }
            return x;
        }

        public int getY(final int row) {
            return (mNumRows - 1 - row) * mDefaultRowHeight + mTopPadding;
        }

        public void markAsEdgeKey(final Key key, final int row) {
            if (row == 0)
                key.markAsTopEdge(this);
            if (isTopRow(row))
                key.markAsBottomEdge(this);
        }

        private boolean isTopRow(final int rowCount) {
            return mNumRows > 1 && rowCount == mNumRows - 1;
        }
    }

    public static class Builder extends KeyboardBuilder<MoreKeysKeyboardParams> {
        private final Key mParentKey;

        private static final float LABEL_PADDING_RATIO = 0.2f;
        private static final float DIVIDER_RATIO = 0.2f;

        /**
         * The builder of MoreKeysKeyboard.
         * @param context the context of {@link MoreKeysKeyboardView}.
         * @param key the {@link Key} that invokes more keys keyboard.
         * @param keyboard the {@link Keyboard} that contains the parentKey.
         * @param isSingleMoreKeyWithPreview true if the <code>key</code> has just a single
         *        "more key" and its key popup preview is enabled.
         * @param keyPreviewVisibleWidth the width of visible part of key popup preview.
         * @param keyPreviewVisibleHeight the height of visible part of key popup preview
         * @param paintToMeasure the {@link Paint} object to measure a "more key" width
         */
        public Builder(final Context context, final Key key, final Keyboard keyboard,
                final boolean isSingleMoreKeyWithPreview, final int keyPreviewVisibleWidth,
                final int keyPreviewVisibleHeight, final Paint paintToMeasure) {
            super(context, new MoreKeysKeyboardParams());
            mParams.mId = keyboard.mId;
            readAttributes(keyboard.mMoreKeysTemplate);

            // TODO: More keys keyboard's vertical gap is currently calculated heuristically.
            // Should revise the algorithm.
            mParams.mVerticalGap = keyboard.mVerticalGap / 2;
            // This {@link MoreKeysKeyboard} is invoked from the <code>key</code>.
            mParentKey = key;

            final int keyWidth, rowHeight;
            if (isSingleMoreKeyWithPreview) {
                // Use pre-computed width and height if this more keys keyboard has only one key to
                // mitigate visual flicker between key preview and more keys keyboard.
                // Caveats for the visual assets: To achieve this effect, both the key preview
                // backgrounds and the more keys keyboard panel background have the exact same
                // left/right/top paddings. The bottom paddings of both backgrounds don't need to
                // be considered because the vertical positions of both backgrounds were already
                // adjusted with their bottom paddings deducted.
                keyWidth = keyPreviewVisibleWidth;
                rowHeight = keyPreviewVisibleHeight + mParams.mVerticalGap;
            } else {
                final float padding = context.getResources().getDimension(
                        R.dimen.config_more_keys_keyboard_key_horizontal_padding)
                        + (key.hasLabelsInMoreKeys()
                                ? mParams.mDefaultKeyWidth * LABEL_PADDING_RATIO : 0.0f);
                keyWidth = getMaxKeyWidth(key, mParams.mDefaultKeyWidth, padding, paintToMeasure);
                rowHeight = keyboard.mMostCommonKeyHeight;
            }
            final int dividerWidth;
            if (key.needsDividersInMoreKeys()) {
                dividerWidth = (int)(keyWidth * DIVIDER_RATIO);
            } else {
                dividerWidth = 0;
            }
            final MoreKeySpec[] moreKeys = key.getMoreKeys();
            final int defaultColumns = key.getMoreKeysColumnNumber();
            final int keysPerRpw = Math.min(moreKeys.length, defaultColumns);
            final int spaceForKeys = keyboard.mId.mWidth / keyWidth;
            final int finalNumColumns = spaceForKeys >= keysPerRpw
                    ? defaultColumns
                    : (spaceForKeys > 0 ? spaceForKeys : defaultColumns); // in last case setParameters will throw
            mParams.setParameters(moreKeys.length, finalNumColumns, keyWidth,
                    rowHeight, key.getX() + key.getWidth() / 2, keyboard.mId.mWidth,
                    key.isMoreKeysFixedColumn(), key.isMoreKeysFixedOrder(), dividerWidth);
        }

        private static int getMaxKeyWidth(final Key parentKey, final int minKeyWidth,
                final float padding, final Paint paint) {
            int maxWidth = minKeyWidth;
            for (final MoreKeySpec spec : parentKey.getMoreKeys()) {
                final String label = spec.mLabel;
                // If the label is single letter, minKeyWidth is enough to hold the label.
                if (label != null && StringUtils.codePointCount(label) > 1) {
                    maxWidth = Math.max(maxWidth,
                            (int)(TypefaceUtils.getStringWidth(label, paint) + padding));
                }
            }
            return maxWidth;
        }

        @Override
        @NonNull
        public MoreKeysKeyboard build() {
            final MoreKeysKeyboardParams params = mParams;
            final int moreKeyFlags = mParentKey.getMoreKeyLabelFlags();
            final MoreKeySpec[] moreKeys = mParentKey.getMoreKeys();
            for (int n = 0; n < moreKeys.length; n++) {
                final MoreKeySpec moreKeySpec = moreKeys[n];
                final int row = n / params.mNumColumns;
                final int x = params.getX(n, row);
                final int y = params.getY(row);
                final Key key = moreKeySpec.buildKey(x, y, moreKeyFlags, params);
                params.markAsEdgeKey(key, row);
                params.onAddKey(key);

                final int pos = params.getColumnPos(n);
                // The "pos" value represents the offset from the default position. Negative means
                // left of the default position.
                if (params.mDividerWidth > 0 && pos != 0) {
                    final int dividerX = (pos > 0) ? x - params.mDividerWidth
                            : x + params.mDefaultKeyWidth;
                    final Key divider = new MoreKeyDivider(
                            params, dividerX, y, params.mDividerWidth, params.mDefaultRowHeight);
                    params.onAddKey(divider);
                }
            }
            return new MoreKeysKeyboard(params);
        }
    }

    // Used as a divider maker. A divider is drawn by {@link MoreKeysKeyboardView}.
    public static class MoreKeyDivider extends Key.Spacer {
        public MoreKeyDivider(final KeyboardParams params, final int x, final int y,
                final int width, final int height) {
            super(params, x, y, width, height);
        }
    }
}
