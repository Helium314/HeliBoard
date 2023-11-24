/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.util.Log;

import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.InputPointers;
import org.dslul.openboard.inputmethod.latin.common.ResizableIntArray;

/**
 * This class holds event points to recognize a gesture stroke.
 * TODO: Should be package private class.
 */
public final class GestureStrokeRecognitionPoints {
    private static final String TAG = GestureStrokeRecognitionPoints.class.getSimpleName();
    private static final boolean DEBUG = false;
    private static final boolean DEBUG_SPEED = false;

    // The height of extra area above the keyboard to draw gesture trails.
    // Proportional to the keyboard height.
    public static final float EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO = 0.25f;

    private final int mPointerId;
    private final ResizableIntArray mEventTimes = new ResizableIntArray(
            Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(
            Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(
            Constants.DEFAULT_GESTURE_POINTS_CAPACITY);

    private final GestureStrokeRecognitionParams mRecognitionParams;

    private int mKeyWidth; // pixel
    private int mMinYCoordinate; // pixel
    private int mMaxYCoordinate; // pixel
    // Static threshold for starting gesture detection
    private int mDetectFastMoveSpeedThreshold; // pixel /sec
    private int mDetectFastMoveTime;
    private int mDetectFastMoveX;
    private int mDetectFastMoveY;
    // Dynamic threshold for gesture after fast typing
    private boolean mAfterFastTyping;
    private int mGestureDynamicDistanceThresholdFrom; // pixel
    private int mGestureDynamicDistanceThresholdTo; // pixel
    // Variables for gesture sampling
    private int mGestureSamplingMinimumDistance; // pixel
    private long mLastMajorEventTime;
    private int mLastMajorEventX;
    private int mLastMajorEventY;
    // Variables for gesture recognition
    private int mGestureRecognitionSpeedThreshold; // pixel / sec
    private int mIncrementalRecognitionSize;
    private int mLastIncrementalBatchSize;

    private static final int MSEC_PER_SEC = 1000;

    // TODO: Make this package private
    public GestureStrokeRecognitionPoints(final int pointerId,
            final GestureStrokeRecognitionParams recognitionParams) {
        mPointerId = pointerId;
        mRecognitionParams = recognitionParams;
    }

    // TODO: Make this package private
    public void setKeyboardGeometry(final int keyWidth, final int keyboardHeight) {
        mKeyWidth = keyWidth;
        mMinYCoordinate = -(int)(keyboardHeight * EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO);
        mMaxYCoordinate = keyboardHeight;
        // TODO: Find an appropriate base metric for these length. Maybe diagonal length of the key?
        mDetectFastMoveSpeedThreshold = (int)(
                keyWidth * mRecognitionParams.mDetectFastMoveSpeedThreshold);
        mGestureDynamicDistanceThresholdFrom = (int)(
                keyWidth * mRecognitionParams.mDynamicDistanceThresholdFrom);
        mGestureDynamicDistanceThresholdTo = (int)(
                keyWidth * mRecognitionParams.mDynamicDistanceThresholdTo);
        mGestureSamplingMinimumDistance = (int)(
                keyWidth * mRecognitionParams.mSamplingMinimumDistance);
        mGestureRecognitionSpeedThreshold = (int)(
                keyWidth * mRecognitionParams.mRecognitionSpeedThreshold);
        if (DEBUG) {
            Log.d(TAG, String.format(
                    "[%d] setKeyboardGeometry: keyWidth=%3d tT=%3d >> %3d tD=%3d >> %3d",
                    mPointerId, keyWidth,
                    mRecognitionParams.mDynamicTimeThresholdFrom,
                    mRecognitionParams.mDynamicTimeThresholdTo,
                    mGestureDynamicDistanceThresholdFrom,
                    mGestureDynamicDistanceThresholdTo));
        }
    }

    // TODO: Make this package private
    public int getLength() {
        return mEventTimes.getLength();
    }

    // TODO: Make this package private
    public void addDownEventPoint(final int x, final int y, final int elapsedTimeSinceFirstDown,
            final int elapsedTimeSinceLastTyping) {
        reset();
        if (elapsedTimeSinceLastTyping < mRecognitionParams.mStaticTimeThresholdAfterFastTyping) {
            mAfterFastTyping = true;
        }
        if (DEBUG) {
            Log.d(TAG, String.format("[%d] onDownEvent: dT=%3d%s", mPointerId,
                    elapsedTimeSinceLastTyping, mAfterFastTyping ? " afterFastTyping" : ""));
        }
        // Call {@link #addEventPoint(int,int,int,boolean)} to record this down event point as a
        // major event point.
        addEventPoint(x, y, elapsedTimeSinceFirstDown, true /* isMajorEvent */);
    }

    private int getGestureDynamicDistanceThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= mRecognitionParams.mDynamicThresholdDecayDuration) {
            return mGestureDynamicDistanceThresholdTo;
        }
        final int decayedThreshold =
                (mGestureDynamicDistanceThresholdFrom - mGestureDynamicDistanceThresholdTo)
                * deltaTime / mRecognitionParams.mDynamicThresholdDecayDuration;
        return mGestureDynamicDistanceThresholdFrom - decayedThreshold;
    }

    private int getGestureDynamicTimeThreshold(final int deltaTime) {
        if (!mAfterFastTyping || deltaTime >= mRecognitionParams.mDynamicThresholdDecayDuration) {
            return mRecognitionParams.mDynamicTimeThresholdTo;
        }
        final int decayedThreshold =
                (mRecognitionParams.mDynamicTimeThresholdFrom
                        - mRecognitionParams.mDynamicTimeThresholdTo)
                * deltaTime / mRecognitionParams.mDynamicThresholdDecayDuration;
        return mRecognitionParams.mDynamicTimeThresholdFrom - decayedThreshold;
    }

    // TODO: Make this package private
    public boolean isStartOfAGesture() {
        if (!hasDetectedFastMove()) {
            return false;
        }
        final int size = getLength();
        if (size <= 0) {
            return false;
        }
        final int lastIndex = size - 1;
        final int deltaTime = mEventTimes.get(lastIndex) - mDetectFastMoveTime;
        if (deltaTime < 0) {
            return false;
        }
        final int deltaDistance = getDistance(
                mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex),
                mDetectFastMoveX, mDetectFastMoveY);
        final int distanceThreshold = getGestureDynamicDistanceThreshold(deltaTime);
        final int timeThreshold = getGestureDynamicTimeThreshold(deltaTime);
        final boolean isStartOfAGesture = deltaTime >= timeThreshold
                && deltaDistance >= distanceThreshold;
        if (DEBUG) {
            Log.d(TAG, String.format("[%d] isStartOfAGesture: dT=%3d tT=%3d dD=%3d tD=%3d%s%s",
                    mPointerId, deltaTime, timeThreshold,
                    deltaDistance, distanceThreshold,
                    mAfterFastTyping ? " afterFastTyping" : "",
                    isStartOfAGesture ? " startOfAGesture" : ""));
        }
        return isStartOfAGesture;
    }

    // TODO: Make this package private
    public void duplicateLastPointWith(final int time) {
        final int lastIndex = getLength() - 1;
        if (lastIndex >= 0) {
            final int x = mXCoordinates.get(lastIndex);
            final int y = mYCoordinates.get(lastIndex);
            if (DEBUG) {
                Log.d(TAG, String.format("[%d] duplicateLastPointWith: %d,%d|%d", mPointerId,
                        x, y, time));
            }
            // TODO: Have appendMajorPoint()
            appendPoint(x, y, time);
            updateIncrementalRecognitionSize(x, y, time);
        }
    }

    private void reset() {
        mIncrementalRecognitionSize = 0;
        mLastIncrementalBatchSize = 0;
        mEventTimes.setLength(0);
        mXCoordinates.setLength(0);
        mYCoordinates.setLength(0);
        mLastMajorEventTime = 0;
        mDetectFastMoveTime = 0;
        mAfterFastTyping = false;
    }

    private void appendPoint(final int x, final int y, final int time) {
        final int lastIndex = getLength() - 1;
        // The point that is created by {@link duplicateLastPointWith(int)} may have later event
        // time than the next {@link MotionEvent}. To maintain the monotonicity of the event time,
        // drop the successive point here.
        if (lastIndex >= 0 && mEventTimes.get(lastIndex) > time) {
            Log.w(TAG, String.format("[%d] drop stale event: %d,%d|%d last: %d,%d|%d", mPointerId,
                    x, y, time, mXCoordinates.get(lastIndex), mYCoordinates.get(lastIndex),
                    mEventTimes.get(lastIndex)));
            return;
        }
        mEventTimes.add(time);
        mXCoordinates.add(x);
        mYCoordinates.add(y);
    }

    private void updateMajorEvent(final int x, final int y, final int time) {
        mLastMajorEventTime = time;
        mLastMajorEventX = x;
        mLastMajorEventY = y;
    }

    private boolean hasDetectedFastMove() {
        return mDetectFastMoveTime > 0;
    }

    private int detectFastMove(final int x, final int y, final int time) {
        final int size = getLength();
        final int lastIndex = size - 1;
        final int lastX = mXCoordinates.get(lastIndex);
        final int lastY = mYCoordinates.get(lastIndex);
        final int dist = getDistance(lastX, lastY, x, y);
        final int msecs = time - mEventTimes.get(lastIndex);
        if (msecs > 0) {
            final int pixels = getDistance(lastX, lastY, x, y);
            final int pixelsPerSec = pixels * MSEC_PER_SEC;
            if (DEBUG_SPEED) {
                final float speed = (float)pixelsPerSec / msecs / mKeyWidth;
                Log.d(TAG, String.format("[%d] detectFastMove: speed=%5.2f", mPointerId, speed));
            }
            // Equivalent to (pixels / msecs < mStartSpeedThreshold / MSEC_PER_SEC)
            if (!hasDetectedFastMove() && pixelsPerSec > mDetectFastMoveSpeedThreshold * msecs) {
                if (DEBUG) {
                    final float speed = (float)pixelsPerSec / msecs / mKeyWidth;
                    Log.d(TAG, String.format(
                            "[%d] detectFastMove: speed=%5.2f T=%3d points=%3d fastMove",
                            mPointerId, speed, time, size));
                }
                mDetectFastMoveTime = time;
                mDetectFastMoveX = x;
                mDetectFastMoveY = y;
            }
        }
        return dist;
    }

    /**
     * Add an event point to this gesture stroke recognition points. Returns true if the event
     * point is on the valid gesture area.
     * @param x the x-coordinate of the event point
     * @param y the y-coordinate of the event point
     * @param time the elapsed time in millisecond from the first gesture down
     * @param isMajorEvent false if this is a historical move event
     * @return true if the event point is on the valid gesture area
     */
    // TODO: Make this package private
    public boolean addEventPoint(final int x, final int y, final int time,
            final boolean isMajorEvent) {
        final int size = getLength();
        if (size <= 0) {
            // The first event of this stroke (a.k.a. down event).
            appendPoint(x, y, time);
            updateMajorEvent(x, y, time);
        } else {
            final int distance = detectFastMove(x, y, time);
            if (distance > mGestureSamplingMinimumDistance) {
                appendPoint(x, y, time);
            }
        }
        if (isMajorEvent) {
            updateIncrementalRecognitionSize(x, y, time);
            updateMajorEvent(x, y, time);
        }
        return y >= mMinYCoordinate && y < mMaxYCoordinate;
    }

    private void updateIncrementalRecognitionSize(final int x, final int y, final int time) {
        final int msecs = (int)(time - mLastMajorEventTime);
        if (msecs <= 0) {
            return;
        }
        final int pixels = getDistance(mLastMajorEventX, mLastMajorEventY, x, y);
        final int pixelsPerSec = pixels * MSEC_PER_SEC;
        // Equivalent to (pixels / msecs < mGestureRecognitionThreshold / MSEC_PER_SEC)
        if (pixelsPerSec < mGestureRecognitionSpeedThreshold * msecs) {
            mIncrementalRecognitionSize = getLength();
        }
    }

    // TODO: Make this package private
    public boolean hasRecognitionTimePast(
            final long currentTime, final long lastRecognitionTime) {
        return currentTime > lastRecognitionTime + mRecognitionParams.mRecognitionMinimumTime;
    }

    // TODO: Make this package private
    public void appendAllBatchPoints(final InputPointers out) {
        appendBatchPoints(out, getLength());
    }

    // TODO: Make this package private
    public void appendIncrementalBatchPoints(final InputPointers out) {
        appendBatchPoints(out, mIncrementalRecognitionSize);
    }

    private void appendBatchPoints(final InputPointers out, final int size) {
        final int length = size - mLastIncrementalBatchSize;
        if (length <= 0) {
            return;
        }
        out.append(mPointerId, mEventTimes, mXCoordinates, mYCoordinates,
                mLastIncrementalBatchSize, length);
        mLastIncrementalBatchSize = size;
    }

    private static int getDistance(final int x1, final int y1, final int x2, final int y2) {
        return (int)Math.hypot(x1 - x2, y1 - y2);
    }
}
