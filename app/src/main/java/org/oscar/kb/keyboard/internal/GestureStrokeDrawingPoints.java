/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.keyboard.internal;

import org.oscar.kb.latin.common.ResizableIntArray;


/**
 * This class holds drawing points to represent a gesture stroke on the screen.
 */
public final class GestureStrokeDrawingPoints {
    public static final int PREVIEW_CAPACITY = 256;

    private final ResizableIntArray mPreviewEventTimes = new ResizableIntArray(PREVIEW_CAPACITY);
    private final ResizableIntArray mPreviewXCoordinates = new ResizableIntArray(PREVIEW_CAPACITY);
    private final ResizableIntArray mPreviewYCoordinates = new ResizableIntArray(PREVIEW_CAPACITY);

    private final GestureStrokeDrawingParams mDrawingParams;

    private int mStrokeId;
    private int mLastPreviewSize;
    private final HermiteInterpolator mInterpolator = new HermiteInterpolator();
    private int mLastInterpolatedPreviewIndex;

    private int mLastX;
    private int mLastY;
    private double mDistanceFromLastSample;

    public GestureStrokeDrawingPoints(final GestureStrokeDrawingParams drawingParams) {
        mDrawingParams = drawingParams;
    }

    private void reset() {
        mStrokeId++;
        mLastPreviewSize = 0;
        mLastInterpolatedPreviewIndex = 0;
        mPreviewEventTimes.setLength(0);
        mPreviewXCoordinates.setLength(0);
        mPreviewYCoordinates.setLength(0);
    }

    public int getGestureStrokeId() {
        return mStrokeId;
    }

    public void onDownEvent(final int x, final int y, final int elapsedTimeSinceFirstDown) {
        reset();
        onMoveEvent(x, y, elapsedTimeSinceFirstDown);
    }

    private boolean needsSampling(final int x, final int y) {
        mDistanceFromLastSample += Math.hypot(x - mLastX, y - mLastY);
        mLastX = x;
        mLastY = y;
        final boolean isDownEvent = (mPreviewEventTimes.getLength() == 0);
        if (mDistanceFromLastSample >= mDrawingParams.mMinSamplingDistance || isDownEvent) {
            mDistanceFromLastSample = 0.0d;
            return true;
        }
        return false;
    }

    public void onMoveEvent(final int x, final int y, final int elapsedTimeSinceFirstDown) {
        if (needsSampling(x, y)) {
            mPreviewEventTimes.add(elapsedTimeSinceFirstDown);
            mPreviewXCoordinates.add(x);
            mPreviewYCoordinates.add(y);
        }
    }

    /**
     * Append sampled preview points.
     *
     * @param eventTimes the event time array of gesture trail to be drawn.
     * @param xCoords the x-coordinates array of gesture trail to be drawn.
     * @param yCoords the y-coordinates array of gesture trail to be drawn.
     * @param types the point types array of gesture trail. This is valid only when
     * {@link GestureTrailDrawingPoints#DEBUG_SHOW_POINTS} is true.
     */
    public void appendPreviewStroke(final ResizableIntArray eventTimes,
            final ResizableIntArray xCoords, final ResizableIntArray yCoords,
            final ResizableIntArray types) {
        final int length = mPreviewEventTimes.getLength() - mLastPreviewSize;
        if (length <= 0) {
            return;
        }
        eventTimes.append(mPreviewEventTimes, mLastPreviewSize, length);
        xCoords.append(mPreviewXCoordinates, mLastPreviewSize, length);
        yCoords.append(mPreviewYCoordinates, mLastPreviewSize, length);
        if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
            types.fill(GestureTrailDrawingPoints.POINT_TYPE_SAMPLED, types.getLength(), length);
        }
        mLastPreviewSize = mPreviewEventTimes.getLength();
    }

    /**
     * Calculate interpolated points between the last interpolated point and the end of the trail.
     * And return the start index of the last interpolated segment of input arrays because it
     * may need to recalculate the interpolated points in the segment if further segments are
     * added to this stroke.
     *
     * @param lastInterpolatedIndex the start index of the last interpolated segment of
     *        <code>eventTimes</code>, <code>xCoords</code>, and <code>yCoords</code>.
     * @param eventTimes the event time array of gesture trail to be drawn.
     * @param xCoords the x-coordinates array of gesture trail to be drawn.
     * @param yCoords the y-coordinates array of gesture trail to be drawn.
     * @param types the point types array of gesture trail. This is valid only when
     * {@link GestureTrailDrawingPoints#DEBUG_SHOW_POINTS} is true.
     * @return the start index of the last interpolated segment of input arrays.
     */
    public int interpolateStrokeAndReturnStartIndexOfLastSegment(final int lastInterpolatedIndex,
            final ResizableIntArray eventTimes, final ResizableIntArray xCoords,
            final ResizableIntArray yCoords, final ResizableIntArray types) {
        final int size = mPreviewEventTimes.getLength();
        final int[] pt = mPreviewEventTimes.getPrimitiveArray();
        final int[] px = mPreviewXCoordinates.getPrimitiveArray();
        final int[] py = mPreviewYCoordinates.getPrimitiveArray();
        mInterpolator.reset(px, py, 0, size);
        // The last segment of gesture stroke needs to be interpolated again because the slope of
        // the tangent at the last point isn't determined.
        int lastInterpolatedDrawIndex = lastInterpolatedIndex;
        int d1 = lastInterpolatedIndex;
        for (int p2 = mLastInterpolatedPreviewIndex + 1; p2 < size; p2++) {
            final int p1 = p2 - 1;
            final int p0 = p1 - 1;
            final int p3 = p2 + 1;
            mLastInterpolatedPreviewIndex = p1;
            lastInterpolatedDrawIndex = d1;
            mInterpolator.setInterval(p0, p1, p2, p3);
            final double m1 = Math.atan2(mInterpolator.mSlope1Y, mInterpolator.mSlope1X);
            final double m2 = Math.atan2(mInterpolator.mSlope2Y, mInterpolator.mSlope2X);
            final double deltaAngle = Math.abs(angularDiff(m2, m1));
            final int segmentsByAngle = (int)Math.ceil(
                    deltaAngle / mDrawingParams.mMaxInterpolationAngularThreshold);
            final double deltaDistance = Math.hypot(mInterpolator.mP1X - mInterpolator.mP2X,
                    mInterpolator.mP1Y - mInterpolator.mP2Y);
            final int segmentsByDistance = (int)Math.ceil(deltaDistance
                    / mDrawingParams.mMaxInterpolationDistanceThreshold);
            final int segments = Math.min(mDrawingParams.mMaxInterpolationSegments,
                    Math.max(segmentsByAngle, segmentsByDistance));
            final int t1 = eventTimes.get(d1);
            final int dt = pt[p2] - pt[p1];
            d1++;
            for (int i = 1; i < segments; i++) {
                final float t = i / (float)segments;
                mInterpolator.interpolate(t);
                eventTimes.addAt(d1, (int)(dt * t) + t1);
                xCoords.addAt(d1, (int)mInterpolator.mInterpolatedX);
                yCoords.addAt(d1, (int)mInterpolator.mInterpolatedY);
                if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
                    types.addAt(d1, GestureTrailDrawingPoints.POINT_TYPE_INTERPOLATED);
                }
                d1++;
            }
            eventTimes.addAt(d1, pt[p2]);
            xCoords.addAt(d1, px[p2]);
            yCoords.addAt(d1, py[p2]);
            if (GestureTrailDrawingPoints.DEBUG_SHOW_POINTS) {
                types.addAt(d1, GestureTrailDrawingPoints.POINT_TYPE_SAMPLED);
            }
        }
        return lastInterpolatedDrawIndex;
    }

    private static final double TWO_PI = Math.PI * 2.0d;

    /**
     * Calculate the angular of rotation from <code>a0</code> to <code>a1</code>.
     *
     * @param a1 the angular to which the rotation ends.
     * @param a0 the angular from which the rotation starts.
     * @return the angular rotation value from a0 to a1, normalized to [-PI, +PI].
     */
    private static double angularDiff(final double a1, final double a0) {
        double deltaAngle = a1 - a0;
        while (deltaAngle > Math.PI) {
            deltaAngle -= TWO_PI;
        }
        while (deltaAngle < -Math.PI) {
            deltaAngle += TWO_PI;
        }
        return deltaAngle;
    }
}
