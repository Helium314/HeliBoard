/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.internal;

import android.content.res.TypedArray;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.utils.ResourceUtils;

/**
 * This class holds parameters to control how a gesture stroke is sampled and recognized.
 * This class also has parameters to distinguish gesture input events from fast typing events.
 */
public final class GestureStrokeRecognitionParams {
    // Static threshold for gesture after fast typing
    public final int mStaticTimeThresholdAfterFastTyping; // msec
    // Static threshold for starting gesture detection
    public final float mDetectFastMoveSpeedThreshold; // keyWidth/sec
    // Dynamic threshold for gesture after fast typing
    public final int mDynamicThresholdDecayDuration; // msec
    // Time based threshold values
    public final int mDynamicTimeThresholdFrom; // msec
    public final int mDynamicTimeThresholdTo; // msec
    // Distance based threshold values
    public final float mDynamicDistanceThresholdFrom; // keyWidth
    public final float mDynamicDistanceThresholdTo; // keyWidth
    // Parameters for gesture sampling
    public final float mSamplingMinimumDistance; // keyWidth
    // Parameters for gesture recognition
    public final int mRecognitionMinimumTime; // msec
    public final float mRecognitionSpeedThreshold; // keyWidth/sec

    // Default GestureStrokeRecognitionPoints parameters.
    public static final GestureStrokeRecognitionParams DEFAULT =
            new GestureStrokeRecognitionParams();

    private GestureStrokeRecognitionParams() {
        // These parameter values are default and intended for testing.
        mStaticTimeThresholdAfterFastTyping = 350; // msec
        mDetectFastMoveSpeedThreshold = 1.5f; // keyWidth/sec
        mDynamicThresholdDecayDuration = 450; // msec
        mDynamicTimeThresholdFrom = 300; // msec
        mDynamicTimeThresholdTo = 20; // msec
        mDynamicDistanceThresholdFrom = 6.0f; // keyWidth
        mDynamicDistanceThresholdTo = 0.35f; // keyWidth
        // The following parameters' change will affect the result of regression test.
        mSamplingMinimumDistance = 1.0f / 6.0f; // keyWidth
        mRecognitionMinimumTime = 100; // msec
        mRecognitionSpeedThreshold = 5.5f; // keyWidth/sec
    }

    public GestureStrokeRecognitionParams(final TypedArray mainKeyboardViewAttr) {
        mStaticTimeThresholdAfterFastTyping = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureStaticTimeThresholdAfterFastTyping,
                DEFAULT.mStaticTimeThresholdAfterFastTyping);
        mDetectFastMoveSpeedThreshold = ResourceUtils.getFraction(mainKeyboardViewAttr,
                R.styleable.MainKeyboardView_gestureDetectFastMoveSpeedThreshold,
                DEFAULT.mDetectFastMoveSpeedThreshold);
        mDynamicThresholdDecayDuration = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureDynamicThresholdDecayDuration,
                DEFAULT.mDynamicThresholdDecayDuration);
        mDynamicTimeThresholdFrom = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureDynamicTimeThresholdFrom,
                DEFAULT.mDynamicTimeThresholdFrom);
        mDynamicTimeThresholdTo = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureDynamicTimeThresholdTo,
                DEFAULT.mDynamicTimeThresholdTo);
        mDynamicDistanceThresholdFrom = ResourceUtils.getFraction(mainKeyboardViewAttr,
                R.styleable.MainKeyboardView_gestureDynamicDistanceThresholdFrom,
                DEFAULT.mDynamicDistanceThresholdFrom);
        mDynamicDistanceThresholdTo = ResourceUtils.getFraction(mainKeyboardViewAttr,
                R.styleable.MainKeyboardView_gestureDynamicDistanceThresholdTo,
                DEFAULT.mDynamicDistanceThresholdTo);
        mSamplingMinimumDistance = ResourceUtils.getFraction(mainKeyboardViewAttr,
                R.styleable.MainKeyboardView_gestureSamplingMinimumDistance,
                DEFAULT.mSamplingMinimumDistance);
        mRecognitionMinimumTime = mainKeyboardViewAttr.getInt(
                R.styleable.MainKeyboardView_gestureRecognitionMinimumTime,
                DEFAULT.mRecognitionMinimumTime);
        mRecognitionSpeedThreshold = ResourceUtils.getFraction(mainKeyboardViewAttr,
                R.styleable.MainKeyboardView_gestureRecognitionSpeedThreshold,
                DEFAULT.mRecognitionSpeedThreshold);
    }
}
