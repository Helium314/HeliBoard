/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.res.TypedArray;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.ColorType;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

/**
 * This class holds parameters to control how a gesture trail is drawn and animated on the screen.
 *
 * On the other hand, {@link GestureStrokeDrawingParams} class controls how each gesture stroke is
 * sampled and interpolated. This class controls how those gesture strokes are displayed as a
 * gesture trail and animated on the screen.
 *
 * @attr ref R.styleable#MainKeyboardView_gestureTrailFadeoutStartDelay
 * @attr ref R.styleable#MainKeyboardView_gestureTrailFadeoutDuration
 * @attr ref R.styleable#MainKeyboardView_gestureTrailUpdateInterval
 * @attr ref R.styleable#MainKeyboardView_gestureTrailColor
 * @attr ref R.styleable#MainKeyboardView_gestureTrailWidth
 */
final class GestureTrailDrawingParams {
    private static final int FADEOUT_START_DELAY_FOR_DEBUG = 2000; // millisecond
    private static final int FADEOUT_DURATION_FOR_DEBUG = 200; // millisecond

    public final int mTrailColor;
    public final float mTrailStartWidth;
    public final float mTrailEndWidth;
    public final float mTrailBodyRatio;
    public boolean mTrailShadowEnabled;
    public final float mTrailShadowRatio;
    public final int mFadeoutStartDelay;
    public final int mFadeoutDuration;
    public final int mUpdateInterval;

    public final int mTrailLingerDuration;

    public GestureTrailDrawingParams(final TypedArray mainKeyboardViewAttr) {
        mTrailColor = Settings.getInstance().getCurrent().mColors.get(ColorType.GESTURE_TRAIL); //mainKeyboardViewAttr.getColor(R.styleable.MainKeyboardView_gestureTrailColor, 0);
        mTrailStartWidth = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_gestureTrailStartWidth, 0.0f);
        mTrailEndWidth = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_gestureTrailEndWidth, 0.0f);
        final int PERCENTAGE_INT = 100;
        mTrailBodyRatio = (float)mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureTrailBodyRatio, PERCENTAGE_INT)
                / (float)PERCENTAGE_INT;
        final int trailShadowRatioInt = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureTrailShadowRatio, 0);
        mTrailShadowEnabled = (trailShadowRatioInt > 0);
        mTrailShadowRatio = (float)trailShadowRatioInt / (float)PERCENTAGE_INT;
        mFadeoutStartDelay = GestureTrailDrawingPoints.DEBUG_SHOW_POINTS
                ? FADEOUT_START_DELAY_FOR_DEBUG
                : mainKeyboardViewAttr.getInt(
                        R.styleable.MainKeyboardView_gestureTrailFadeoutStartDelay, 0);
        mFadeoutDuration = GestureTrailDrawingPoints.DEBUG_SHOW_POINTS
                ? FADEOUT_DURATION_FOR_DEBUG
                : mainKeyboardViewAttr.getInt(
                        R.styleable.MainKeyboardView_gestureTrailFadeoutDuration, 0);
        mTrailLingerDuration = mFadeoutStartDelay + mFadeoutDuration;
        mUpdateInterval = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureTrailUpdateInterval, 0);
    }
}
