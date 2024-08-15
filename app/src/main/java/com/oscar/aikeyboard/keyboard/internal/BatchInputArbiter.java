/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard.internal;

import com.oscar.aikeyboard.latin.common.Constants;
import com.oscar.aikeyboard.latin.common.InputPointers;

/**
 * This class arbitrates batch input.
 * An instance of this class holds a {@link GestureStrokeRecognitionPoints}.
 * And it arbitrates multiple strokes gestured by multiple fingers and aggregates those gesture
 * points into one batch input.
 */
public class BatchInputArbiter {
    public interface BatchInputArbiterListener {
        void onStartBatchInput();
        void onUpdateBatchInput(
                final InputPointers aggregatedPointers, final long moveEventTime);
        void onStartUpdateBatchInputTimer();
        void onEndBatchInput(final InputPointers aggregatedPointers, final long upEventTime);
    }

    // The starting time of the first stroke of a gesture input.
    private static long sGestureFirstDownTime;
    // The {@link InputPointers} that includes all events of a gesture input.
    private static final InputPointers sAggregatedPointers = new InputPointers(
            Constants.DEFAULT_GESTURE_POINTS_CAPACITY);
    private static int sLastRecognitionPointSize = 0; // synchronized using sAggregatedPointers
    private static long sLastRecognitionTime = 0; // synchronized using sAggregatedPointers

    private final GestureStrokeRecognitionPoints mRecognitionPoints;

    public BatchInputArbiter(final int pointerId, final com.oscar.aikeyboard.keyboard.internal.GestureStrokeRecognitionParams params) {
        mRecognitionPoints = new GestureStrokeRecognitionPoints(pointerId, params);
    }

    public void setKeyboardGeometry(final int keyWidth, final int keyboardHeight) {
        mRecognitionPoints.setKeyboardGeometry(keyWidth, keyboardHeight);
    }

    /**
     * Calculate elapsed time since the first gesture down.
     * @param eventTime the time of this event.
     * @return the elapsed time in millisecond from the first gesture down.
     */
    public int getElapsedTimeSinceFirstDown(final long eventTime) {
        return (int)(eventTime - sGestureFirstDownTime);
    }

    /**
     * Add a down event point.
     * @param x the x-coordinate of this down event.
     * @param y the y-coordinate of this down event.
     * @param downEventTime the time of this down event.
     * @param lastLetterTypingTime the last typing input time.
     * @param activePointerCount the number of active pointers when this pointer down event occurs.
     */
    public void addDownEventPoint(final int x, final int y, final long downEventTime,
            final long lastLetterTypingTime, final int activePointerCount) {
        if (activePointerCount == 1) {
            sGestureFirstDownTime = downEventTime;
        }
        final int elapsedTimeSinceFirstDown = getElapsedTimeSinceFirstDown(downEventTime);
        final int elapsedTimeSinceLastTyping = (int)(downEventTime - lastLetterTypingTime);
        mRecognitionPoints.addDownEventPoint(
                x, y, elapsedTimeSinceFirstDown, elapsedTimeSinceLastTyping);
    }

    /**
     * Add a move event point.
     * @param x the x-coordinate of this move event.
     * @param y the y-coordinate of this move event.
     * @param moveEventTime the time of this move event.
     * @param isMajorEvent false if this is a historical move event.
     * @param listener {@link BatchInputArbiterListener#onStartUpdateBatchInputTimer()} of this
     *     <code>listener</code> may be called if enough move points have been added.
     * @return true if this move event occurs on the valid gesture area.
     */
    public boolean addMoveEventPoint(final int x, final int y, final long moveEventTime,
            final boolean isMajorEvent, final BatchInputArbiterListener listener) {
        final int beforeLength = mRecognitionPoints.getLength();
        final boolean onValidArea = mRecognitionPoints.addEventPoint(
                x, y, getElapsedTimeSinceFirstDown(moveEventTime), isMajorEvent);
        if (mRecognitionPoints.getLength() > beforeLength) {
            listener.onStartUpdateBatchInputTimer();
        }
        return onValidArea;
    }

    /**
     * Determine whether the batch input has started or not.
     * @param listener {@link BatchInputArbiterListener#onStartBatchInput()} of this
     *     <code>listener</code> will be called when the batch input has started successfully.
     * @return true if the batch input has started successfully.
     */
    public boolean mayStartBatchInput(final BatchInputArbiterListener listener) {
        if (!mRecognitionPoints.isStartOfAGesture()) {
            return false;
        }
        synchronized (sAggregatedPointers) {
            sAggregatedPointers.reset();
            sLastRecognitionPointSize = 0;
            sLastRecognitionTime = 0;
            listener.onStartBatchInput();
        }
        return true;
    }

    /**
     * Add synthetic move event point. After adding the point,
     * {@link #updateBatchInput(long,BatchInputArbiterListener)} will be called internally.
     * @param syntheticMoveEventTime the synthetic move event time.
     * @param listener the listener to be passed to
     *     {@link #updateBatchInput(long,BatchInputArbiterListener)}.
     */
    public void updateBatchInputByTimer(final long syntheticMoveEventTime,
            final BatchInputArbiterListener listener) {
        mRecognitionPoints.duplicateLastPointWith(
                getElapsedTimeSinceFirstDown(syntheticMoveEventTime));
        updateBatchInput(syntheticMoveEventTime, listener);
    }

    /**
     * Determine whether we have enough gesture points to lookup dictionary.
     * @param moveEventTime the time of this move event.
     * @param listener {@link BatchInputArbiterListener#onUpdateBatchInput(InputPointers,long)} of
     *     this <code>listener</code> will be called when enough event points we have. Also
     *     {@link BatchInputArbiterListener#onStartUpdateBatchInputTimer()} will be called to have
     *     possible future synthetic move event.
     */
    public void updateBatchInput(final long moveEventTime,
            final BatchInputArbiterListener listener) {
        synchronized (sAggregatedPointers) {
            mRecognitionPoints.appendIncrementalBatchPoints(sAggregatedPointers);
            final int size = sAggregatedPointers.getPointerSize();
            if (size > sLastRecognitionPointSize && mRecognitionPoints.hasRecognitionTimePast(
                    moveEventTime, sLastRecognitionTime)) {
                listener.onUpdateBatchInput(sAggregatedPointers, moveEventTime);
                listener.onStartUpdateBatchInputTimer();
                // The listener may change the size of the pointers (when auto-committing
                // for example), so we need to get the size from the pointers again.
                sLastRecognitionPointSize = sAggregatedPointers.getPointerSize();
                sLastRecognitionTime = moveEventTime;
            }
        }
    }

    /**
     * Determine whether the batch input has ended successfully or continues.
     * @param upEventTime the time of this up event.
     * @param activePointerCount the number of active pointers when this pointer up event occurs.
     * @param listener {@link BatchInputArbiterListener#onEndBatchInput(InputPointers,long)} of this
     *     <code>listener</code> will be called when the batch input has started successfully.
     * @return true if the batch input has ended successfully.
     */
    public boolean mayEndBatchInput(final long upEventTime, final int activePointerCount,
            final BatchInputArbiterListener listener) {
        synchronized (sAggregatedPointers) {
            mRecognitionPoints.appendAllBatchPoints(sAggregatedPointers);
            if (activePointerCount == 1) {
                listener.onEndBatchInput(sAggregatedPointers, upEventTime);
                return true;
            }
        }
        return false;
    }
}
