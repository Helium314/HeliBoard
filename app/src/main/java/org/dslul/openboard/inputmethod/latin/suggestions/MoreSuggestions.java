/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.suggestions;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;

import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardBuilder;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.utils.TypefaceUtils;

public final class MoreSuggestions extends Keyboard {
    public final SuggestedWords mSuggestedWords;

    MoreSuggestions(final MoreSuggestionsParam params, final SuggestedWords suggestedWords) {
        super(params);
        mSuggestedWords = suggestedWords;
    }

    private static final class MoreSuggestionsParam extends KeyboardParams {
        private final int[] mWidths = new int[SuggestedWords.MAX_SUGGESTIONS];
        private final int[] mRowNumbers = new int[SuggestedWords.MAX_SUGGESTIONS];
        private final int[] mColumnOrders = new int[SuggestedWords.MAX_SUGGESTIONS];
        private final int[] mNumColumnsInRow = new int[SuggestedWords.MAX_SUGGESTIONS];
        private static final int MAX_COLUMNS_IN_ROW = 3;
        private int mNumRows;
        public Drawable mDivider;
        public int mDividerWidth;

        public MoreSuggestionsParam() {
            super();
        }

        public int layout(final SuggestedWords suggestedWords, final int fromIndex,
                final int maxWidth, final int minWidth, final int maxRow, final Paint paint,
                final Resources res) {
            clearKeys();
            mDivider = res.getDrawable(R.drawable.more_suggestions_divider);
            mDividerWidth = mDivider.getIntrinsicWidth();
            final float padding = res.getDimension(
                    R.dimen.config_more_suggestions_key_horizontal_padding);

            int row = 0;
            int index = fromIndex;
            int rowStartIndex = fromIndex;
            final int size = Math.min(suggestedWords.size(), SuggestedWords.MAX_SUGGESTIONS);
            while (index < size) {
                final String word;
                if (isIndexSubjectToAutoCorrection(suggestedWords, index)) {
                    // INDEX_OF_AUTO_CORRECTION and INDEX_OF_TYPED_WORD got swapped.
                    word = suggestedWords.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD);
                } else {
                    word = suggestedWords.getLabel(index);
                }
                // TODO: Should take care of text x-scaling.
                mWidths[index] = (int)(TypefaceUtils.getStringWidth(word, paint) + padding);
                final int numColumn = index - rowStartIndex + 1;
                final int columnWidth =
                        (maxWidth - mDividerWidth * (numColumn - 1)) / numColumn;
                if (numColumn > MAX_COLUMNS_IN_ROW
                        || !fitInWidth(rowStartIndex, index + 1, columnWidth)) {
                    if ((row + 1) >= maxRow) {
                        break;
                    }
                    mNumColumnsInRow[row] = index - rowStartIndex;
                    rowStartIndex = index;
                    row++;
                }
                mColumnOrders[index] = index - rowStartIndex;
                mRowNumbers[index] = row;
                index++;
            }
            mNumColumnsInRow[row] = index - rowStartIndex;
            mNumRows = row + 1;
            mBaseWidth = mOccupiedWidth = Math.max(
                    minWidth, calcurateMaxRowWidth(fromIndex, index));
            mBaseHeight = mOccupiedHeight = mNumRows * mDefaultRowHeight + mVerticalGap;
            return index - fromIndex;
        }

        private boolean fitInWidth(final int startIndex, final int endIndex, final int width) {
            for (int index = startIndex; index < endIndex; index++) {
                if (mWidths[index] > width)
                    return false;
            }
            return true;
        }

        private int calcurateMaxRowWidth(final int startIndex, final int endIndex) {
            int maxRowWidth = 0;
            int index = startIndex;
            for (int row = 0; row < mNumRows; row++) {
                final int numColumnInRow = mNumColumnsInRow[row];
                int maxKeyWidth = 0;
                while (index < endIndex && mRowNumbers[index] == row) {
                    maxKeyWidth = Math.max(maxKeyWidth, mWidths[index]);
                    index++;
                }
                maxRowWidth = Math.max(maxRowWidth,
                        maxKeyWidth * numColumnInRow + mDividerWidth * (numColumnInRow - 1));
            }
            return maxRowWidth;
        }

        private static final int[][] COLUMN_ORDER_TO_NUMBER = {
            { 0 }, // center
            { 1, 0 }, // right-left
            { 1, 0, 2 }, // center-left-right
        };

        public int getNumColumnInRow(final int index) {
            return mNumColumnsInRow[mRowNumbers[index]];
        }

        public int getColumnNumber(final int index) {
            final int columnOrder = mColumnOrders[index];
            final int numColumn = getNumColumnInRow(index);
            return COLUMN_ORDER_TO_NUMBER[numColumn - 1][columnOrder];
        }

        public int getX(final int index) {
            final int columnNumber = getColumnNumber(index);
            return columnNumber * (getWidth(index) + mDividerWidth);
        }

        public int getY(final int index) {
            final int row = mRowNumbers[index];
            return (mNumRows -1 - row) * mDefaultRowHeight + mTopPadding;
        }

        public int getWidth(final int index) {
            final int numColumnInRow = getNumColumnInRow(index);
            return (mOccupiedWidth - mDividerWidth * (numColumnInRow - 1)) / numColumnInRow;
        }

        public void markAsEdgeKey(final Key key, final int index) {
            final int row = mRowNumbers[index];
            if (row == 0)
                key.markAsBottomEdge(this);
            if (row == mNumRows - 1)
                key.markAsTopEdge(this);

            final int numColumnInRow = mNumColumnsInRow[row];
            final int column = getColumnNumber(index);
            if (column == 0)
                key.markAsLeftEdge(this);
            if (column == numColumnInRow - 1)
                key.markAsRightEdge(this);
        }
    }

    static boolean isIndexSubjectToAutoCorrection(final SuggestedWords suggestedWords,
            final int index) {
        return suggestedWords.mWillAutoCorrect && index == SuggestedWords.INDEX_OF_AUTO_CORRECTION;
    }

    public static final class Builder extends KeyboardBuilder<MoreSuggestionsParam> {
        private final MoreSuggestionsView mPaneView;
        private SuggestedWords mSuggestedWords;
        private int mFromIndex;
        private int mToIndex;

        public Builder(final Context context, final MoreSuggestionsView paneView) {
            super(context, new MoreSuggestionsParam());
            mPaneView = paneView;
        }

        public Builder layout(final SuggestedWords suggestedWords, final int fromIndex,
                final int maxWidth, final int minWidth, final int maxRow,
                final Keyboard parentKeyboard) {
            final int xmlId = R.xml.kbd_suggestions_pane_template;
            load(xmlId, parentKeyboard.mId);
            mParams.mVerticalGap = mParams.mTopPadding = parentKeyboard.mVerticalGap / 2;
            mPaneView.updateKeyboardGeometry(mParams.mDefaultRowHeight);
            final int count = mParams.layout(suggestedWords, fromIndex, maxWidth, minWidth, maxRow,
                    mPaneView.newLabelPaint(null /* key */), mResources);
            mFromIndex = fromIndex;
            mToIndex = fromIndex + count;
            mSuggestedWords = suggestedWords;
            return this;
        }

        @Override
        public MoreSuggestions build() {
            final MoreSuggestionsParam params = mParams;
            for (int index = mFromIndex; index < mToIndex; index++) {
                final int x = params.getX(index);
                final int y = params.getY(index);
                final int width = params.getWidth(index);
                final String word;
                final String info;
                if (isIndexSubjectToAutoCorrection(mSuggestedWords, index)) {
                    // INDEX_OF_AUTO_CORRECTION and INDEX_OF_TYPED_WORD got swapped.
                    word = mSuggestedWords.getLabel(SuggestedWords.INDEX_OF_TYPED_WORD);
                    info = mSuggestedWords.getDebugString(SuggestedWords.INDEX_OF_TYPED_WORD);
                } else {
                    word = mSuggestedWords.getLabel(index);
                    info = mSuggestedWords.getDebugString(index);
                }
                final Key key = new MoreSuggestionKey(word, info, index, params);
                params.markAsEdgeKey(key, index);
                params.onAddKey(key);
                final int columnNumber = params.getColumnNumber(index);
                final int numColumnInRow = params.getNumColumnInRow(index);
                if (columnNumber < numColumnInRow - 1) {
                    final Divider divider = new Divider(params, params.mDivider, x + width, y,
                            params.mDividerWidth, params.mDefaultRowHeight);
                    params.onAddKey(divider);
                }
            }
            return new MoreSuggestions(params, mSuggestedWords);
        }
    }

    static final class MoreSuggestionKey extends Key {
        public final int mSuggestedWordIndex;

        public MoreSuggestionKey(final String word, final String info, final int index,
                final MoreSuggestionsParam params) {
            super(word /* label */, KeyboardIconsSet.ICON_UNDEFINED, Constants.CODE_OUTPUT_TEXT,
                    word /* outputText */, info, 0 /* labelFlags */, Key.BACKGROUND_TYPE_NORMAL,
                    params.getX(index), params.getY(index), params.getWidth(index),
                    params.mDefaultRowHeight, params.mHorizontalGap, params.mVerticalGap);
            mSuggestedWordIndex = index;
        }
    }

    private static final class Divider extends Key.Spacer {
        private final Drawable mIcon;

        public Divider(final KeyboardParams params, final Drawable icon, final int x,
                final int y, final int width, final int height) {
            super(params, x, y, width, height);
            mIcon = icon;
        }

        @Override
        public Drawable getIcon(final KeyboardIconsSet iconSet, final int alpha) {
            // KeyboardIconsSet and alpha are unused. Use the icon that has been passed to the
            // constructor.
            // TODO: Drawable itself should have an alpha value.
            mIcon.setAlpha(128);
            return mIcon;
        }
    }
}
