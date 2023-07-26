/*
 * Copyright (C) 2012 The Android Open Source Project
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

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Paint.Align;
import android.graphics.Rect;
import android.graphics.RectF;
import android.text.TextUtils;

import org.dslul.openboard.inputmethod.keyboard.PointerTracker;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.common.CoordinateUtils;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

import javax.annotation.Nonnull;

/**
 * The class for single gesture preview text. The class for multiple gesture preview text will be
 * derived from it.
 *
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextSize
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextColor
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewTextOffset
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewColor
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewHorizontalPadding
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewVerticalPadding
 * @attr ref R.styleable#KeyboardView_gestureFloatingPreviewRoundRadius
 */
public class GestureFloatingTextDrawingPreview extends AbstractDrawingPreview {
    protected static final class GesturePreviewTextParams {
        public final int mGesturePreviewTextOffset;
        public final int mGesturePreviewTextHeight;
        public final float mGesturePreviewHorizontalPadding;
        public final float mGesturePreviewVerticalPadding;
        public final float mGesturePreviewRoundRadius;
        public final int mDisplayWidth;

        private final int mGesturePreviewTextSize;
        private final int mGesturePreviewTextColor;
        private final int mGesturePreviewColor;
        private final Paint mPaint = new Paint();

        private static final char[] TEXT_HEIGHT_REFERENCE_CHAR = { 'M' };

        public GesturePreviewTextParams(final TypedArray mainKeyboardViewAttr) {
            final Colors colors = Settings.getInstance().getCurrent().mColors;
            mGesturePreviewTextSize = mainKeyboardViewAttr.getDimensionPixelSize(
                    R.styleable.MainKeyboardView_gestureFloatingPreviewTextSize, 0);
            mGesturePreviewTextColor = colors.isCustom
                    ? colors.keyText
                    : mainKeyboardViewAttr.getColor(R.styleable.MainKeyboardView_gestureFloatingPreviewTextColor, 0);
            mGesturePreviewTextOffset = mainKeyboardViewAttr.getDimensionPixelOffset(
                    R.styleable.MainKeyboardView_gestureFloatingPreviewTextOffset, 0);
            mGesturePreviewColor = colors.isCustom
                    ? colors.background
                    : mainKeyboardViewAttr.getColor(R.styleable.MainKeyboardView_gestureFloatingPreviewColor, 0);
            mGesturePreviewHorizontalPadding = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gestureFloatingPreviewHorizontalPadding, 0.0f);
            mGesturePreviewVerticalPadding = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gestureFloatingPreviewVerticalPadding, 0.0f);
            mGesturePreviewRoundRadius = mainKeyboardViewAttr.getDimension(
                    R.styleable.MainKeyboardView_gestureFloatingPreviewRoundRadius, 0.0f);
            mDisplayWidth = mainKeyboardViewAttr.getResources().getDisplayMetrics().widthPixels;

            final Paint textPaint = getTextPaint();
            final Rect textRect = new Rect();
            textPaint.getTextBounds(TEXT_HEIGHT_REFERENCE_CHAR, 0, 1, textRect);
            mGesturePreviewTextHeight = textRect.height();
        }

        public Paint getTextPaint() {
            mPaint.setAntiAlias(true);
            mPaint.setTextAlign(Align.CENTER);
            mPaint.setTextSize(mGesturePreviewTextSize);
            mPaint.setColor(mGesturePreviewTextColor);
            return mPaint;
        }

        public Paint getBackgroundPaint() {
            mPaint.setColor(mGesturePreviewColor);
            return mPaint;
        }
    }

    private final GesturePreviewTextParams mParams;
    private final RectF mGesturePreviewRectangle = new RectF();
    private int mPreviewTextX;
    private int mPreviewTextY;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private final int[] mLastPointerCoords = CoordinateUtils.newInstance();

    public GestureFloatingTextDrawingPreview(final TypedArray mainKeyboardViewAttr) {
        mParams = new GesturePreviewTextParams(mainKeyboardViewAttr);
    }

    @Override
    public void onDeallocateMemory() {
        // Nothing to do here.
    }

    public void dismissGestureFloatingPreviewText() {
        setSuggetedWords(SuggestedWords.getEmptyInstance());
    }

    public void setSuggetedWords(@Nonnull final SuggestedWords suggestedWords) {
        if (!isPreviewEnabled()) {
            return;
        }
        mSuggestedWords = suggestedWords;
        updatePreviewPosition();
    }

    @Override
    public void setPreviewPosition(final PointerTracker tracker) {
        if (!isPreviewEnabled()) {
            return;
        }
        tracker.getLastCoordinates(mLastPointerCoords);
        updatePreviewPosition();
    }

    /**
     * Draws gesture preview text
     * @param canvas The canvas where preview text is drawn.
     */
    @Override
    public void drawPreview(final Canvas canvas) {
        if (!isPreviewEnabled() || mSuggestedWords.isEmpty()
                || TextUtils.isEmpty(mSuggestedWords.getWord(0))) {
            return;
        }
        final float round = mParams.mGesturePreviewRoundRadius;
        canvas.drawRoundRect(
                mGesturePreviewRectangle, round, round, mParams.getBackgroundPaint());
        final String text = mSuggestedWords.getWord(0);
        canvas.drawText(text, mPreviewTextX, mPreviewTextY, mParams.getTextPaint());
    }

    /**
     * Updates gesture preview text position based on mLastPointerCoords.
     */
    protected void updatePreviewPosition() {
        if (mSuggestedWords.isEmpty() || TextUtils.isEmpty(mSuggestedWords.getWord(0))) {
            invalidateDrawingView();
            return;
        }
        final String text = mSuggestedWords.getWord(0);

        final RectF rectangle = mGesturePreviewRectangle;

        final int textHeight = mParams.mGesturePreviewTextHeight;
        final float textWidth = mParams.getTextPaint().measureText(text);
        final float hPad = mParams.mGesturePreviewHorizontalPadding;
        final float vPad = mParams.mGesturePreviewVerticalPadding;
        final float rectWidth = textWidth + hPad * 2.0f;
        final float rectHeight = textHeight + vPad * 2.0f;

        final float rectX = Math.min(
                Math.max(CoordinateUtils.x(mLastPointerCoords) - rectWidth / 2.0f, 0.0f),
                mParams.mDisplayWidth - rectWidth);
        final float rectY = CoordinateUtils.y(mLastPointerCoords)
                - mParams.mGesturePreviewTextOffset - rectHeight;
        rectangle.set(rectX, rectY, rectX + rectWidth, rectY + rectHeight);

        mPreviewTextX = (int)(rectX + hPad + textWidth / 2.0f);
        mPreviewTextY = (int)(rectY + vPad) + textHeight;
        // TODO: Should narrow the invalidate region.
        invalidateDrawingView();
    }
}
