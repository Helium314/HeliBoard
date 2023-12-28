/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.res.TypedArray;

import org.dslul.openboard.inputmethod.latin.R;

/**
 * This class holds parameters to control how a gesture stroke is sampled and drawn on the screen.
 *
 * @attr ref R.styleable#MainKeyboardView_gestureTrailMinSamplingDistance
 * @attr ref R.styleable#MainKeyboardView_gestureTrailMaxInterpolationAngularThreshold
 * @attr ref R.styleable#MainKeyboardView_gestureTrailMaxInterpolationDistanceThreshold
 * @attr ref R.styleable#MainKeyboardView_gestureTrailMaxInterpolationSegments
 */
public final class GestureStrokeDrawingParams {
    public final double mMinSamplingDistance; // in pixel
    public final double mMaxInterpolationAngularThreshold; // in radian
    public final double mMaxInterpolationDistanceThreshold; // in pixel
    public final int mMaxInterpolationSegments;

    private static final float DEFAULT_MIN_SAMPLING_DISTANCE = 0.0f; // dp
    private static final int DEFAULT_MAX_INTERPOLATION_ANGULAR_THRESHOLD = 15; // in degree
    private static final float DEFAULT_MAX_INTERPOLATION_DISTANCE_THRESHOLD = 0.0f; // dp
    private static final int DEFAULT_MAX_INTERPOLATION_SEGMENTS = 4;

    public GestureStrokeDrawingParams(final TypedArray mainKeyboardViewAttr) {
        mMinSamplingDistance = mainKeyboardViewAttr.getDimension(
                R.styleable.MainKeyboardView_gestureTrailMinSamplingDistance,
                DEFAULT_MIN_SAMPLING_DISTANCE);
        final int interpolationAngularDegree = mainKeyboardViewAttr.getInteger(R.styleable
                .MainKeyboardView_gestureTrailMaxInterpolationAngularThreshold, 0);
        mMaxInterpolationAngularThreshold = (interpolationAngularDegree <= 0)
                ? Math.toRadians(DEFAULT_MAX_INTERPOLATION_ANGULAR_THRESHOLD)
                : Math.toRadians(interpolationAngularDegree);
        mMaxInterpolationDistanceThreshold = mainKeyboardViewAttr.getDimension(R.styleable
                .MainKeyboardView_gestureTrailMaxInterpolationDistanceThreshold,
                DEFAULT_MAX_INTERPOLATION_DISTANCE_THRESHOLD);
        mMaxInterpolationSegments = mainKeyboardViewAttr.getInteger(
                R.styleable.MainKeyboardView_gestureTrailMaxInterpolationSegments,
                DEFAULT_MAX_INTERPOLATION_SEGMENTS);
    }
}
