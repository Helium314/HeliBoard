/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.setup;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import androidx.core.view.ViewCompat;

public final class SetupStepIndicatorView extends View {
    private final Path mIndicatorPath = new Path();
    private final Paint mIndicatorPaint = new Paint();
    private float mXRatio;

    public SetupStepIndicatorView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        final boolean isNight = ResourceUtils.isNight(context.getResources());
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (!isNight) {
                mIndicatorPaint.setColor(getResources().getColor(android.R.color.system_accent1_50, null));
            } else {
                mIndicatorPaint.setColor(getResources().getColor(android.R.color.system_accent1_500, null));
            }
        } else {
            mIndicatorPaint.setColor(getResources().getColor(R.color.setup_step_background));
        }
        mIndicatorPaint.setStyle(Paint.Style.FILL);
    }

    public void setIndicatorPosition(final int stepPos, final int totalStepNum) {
        final int layoutDirection = ViewCompat.getLayoutDirection(this);
        // The indicator position is the center of the partition that is equally divided into
        // the total step number.
        final float partionWidth = 1.0f / totalStepNum;
        final float pos = stepPos * partionWidth + partionWidth / 2.0f;
        mXRatio = (layoutDirection == ViewCompat.LAYOUT_DIRECTION_RTL) ? 1.0f - pos : pos;
        invalidate();
    }

    @Override
    protected void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final int xPos = (int)(getWidth() * mXRatio);
        final int height = getHeight();
        mIndicatorPath.rewind();
        mIndicatorPath.moveTo(xPos, 0);
        mIndicatorPath.lineTo(xPos + height, height);
        mIndicatorPath.lineTo(xPos - height, height);
        mIndicatorPath.close();
        canvas.drawPath(mIndicatorPath, mIndicatorPaint);
    }
}
