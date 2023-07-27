/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.graphics.Path;

import org.dslul.openboard.inputmethod.keyboard.PointerTracker;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.common.CoordinateUtils;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

/**
 * Draw rubber band preview graphics during sliding key input.
 *
 * @attr ref R.styleable#MainKeyboardView_slidingKeyInputPreviewColor
 * @attr ref R.styleable#MainKeyboardView_slidingKeyInputPreviewWidth
 * @attr ref R.styleable#MainKeyboardView_slidingKeyInputPreviewBodyRatio
 * @attr ref R.styleable#MainKeyboardView_slidingKeyInputPreviewShadowRatio
 */
public final class SlidingKeyInputDrawingPreview extends AbstractDrawingPreview {
    private final float mPreviewBodyRadius;

    private boolean mShowsSlidingKeyInputPreview;
    private final int[] mPreviewFrom = CoordinateUtils.newInstance();
    private final int[] mPreviewTo = CoordinateUtils.newInstance();

    // TODO: Finalize the rubber band preview implementation.
    private final RoundedLine mRoundedLine = new RoundedLine();
    private final Paint mPaint = new Paint();

    public SlidingKeyInputDrawingPreview(final TypedArray mainKeyboardViewAttr) {
        final Colors colors = Settings.getInstance().getCurrent().mColors;
        final int previewColor = colors.isCustom
                ? colors.accent
                : mainKeyboardViewAttr.getColor(R.styleable.MainKeyboardView_slidingKeyInputPreviewColor, 0);
        final float previewRadius = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_slidingKeyInputPreviewWidth, 0) / 2.0f;
        final int PERCENTAGE_INT = 100;
        final float previewBodyRatio = (float)mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_slidingKeyInputPreviewBodyRatio, PERCENTAGE_INT)
                / (float)PERCENTAGE_INT;
        mPreviewBodyRadius = previewRadius * previewBodyRatio;
        final int previewShadowRatioInt = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_slidingKeyInputPreviewShadowRatio, 0);
        if (previewShadowRatioInt > 0) {
            final float previewShadowRatio = (float)previewShadowRatioInt / (float)PERCENTAGE_INT;
            final float shadowRadius = previewRadius * previewShadowRatio;
            mPaint.setShadowLayer(shadowRadius, 0.0f, 0.0f, previewColor);
        }
        mPaint.setColor(previewColor);
    }

    @Override
    public void onDeallocateMemory() {
        // Nothing to do here.
    }

    public void dismissSlidingKeyInputPreview() {
        mShowsSlidingKeyInputPreview = false;
        invalidateDrawingView();
    }

    /**
     * Draws the preview
     * @param canvas The canvas where the preview is drawn.
     */
    @Override
    public void drawPreview(final Canvas canvas) {
        if (!isPreviewEnabled() || !mShowsSlidingKeyInputPreview) {
            return;
        }

        // TODO: Finalize the rubber band preview implementation.
        final float radius = mPreviewBodyRadius;
        final Path path = mRoundedLine.makePath(
                CoordinateUtils.x(mPreviewFrom), CoordinateUtils.y(mPreviewFrom), radius,
                CoordinateUtils.x(mPreviewTo), CoordinateUtils.y(mPreviewTo), radius);
        canvas.drawPath(path, mPaint);
    }

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    @Override
    public void setPreviewPosition(final PointerTracker tracker) {
        tracker.getDownCoordinates(mPreviewFrom);
        tracker.getLastCoordinates(mPreviewTo);
        mShowsSlidingKeyInputPreview = true;
        invalidateDrawingView();
    }
}
