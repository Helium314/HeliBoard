/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard.internal;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.os.SystemClock;

import com.oscar.aikeyboard.latin.common.ResizableIntArray;

/**
 * This class holds drawing points to represent a gesture trail. The gesture trail may contain
 * multiple non-contiguous gesture strokes and will be animated asynchronously from gesture input.
 * <p>
 * On the other hand, {@link GestureStrokeDrawingPoints} class holds drawing points of each gesture
 * stroke. This class holds drawing points of those gesture strokes to draw as a gesture trail.
 * Drawing points in this class will be asynchronously removed when fading out animation goes.
 */
final class GestureTrailDrawingPoints {
    public static final boolean DEBUG_SHOW_POINTS = false;
    public static final int POINT_TYPE_SAMPLED = 1;
    public static final int POINT_TYPE_INTERPOLATED = 2;

    private static final int DEFAULT_CAPACITY = com.oscar.aikeyboard.keyboard.internal.GestureStrokeDrawingPoints.PREVIEW_CAPACITY;

    // These three {@link ResizableIntArray}s should be synchronized by {@link #mEventTimes}.
    private final ResizableIntArray mXCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mYCoordinates = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mEventTimes = new ResizableIntArray(DEFAULT_CAPACITY);
    private final ResizableIntArray mPointTypes = new ResizableIntArray(
            DEBUG_SHOW_POINTS ? DEFAULT_CAPACITY : 0);
    private int mCurrentStrokeId = -1;
    // The wall time of the zero value in {@link #mEventTimes}
    private long mCurrentTimeBase;
    private int mTrailStartIndex;
    private int mLastInterpolatedDrawIndex;

    // Use this value as imaginary zero because x-coordinates may be zero.
    private static final int DOWN_EVENT_MARKER = -128;

    private static int markAsDownEvent(final int xCoord) {
        return DOWN_EVENT_MARKER - xCoord;
    }

    private static boolean isDownEventXCoord(final int xCoordOrMark) {
        return xCoordOrMark <= DOWN_EVENT_MARKER;
    }

    private static int getXCoordValue(final int xCoordOrMark) {
        return isDownEventXCoord(xCoordOrMark)
                ? DOWN_EVENT_MARKER - xCoordOrMark : xCoordOrMark;
    }

    public void addStroke(final com.oscar.aikeyboard.keyboard.internal.GestureStrokeDrawingPoints stroke, final long downTime) {
        synchronized (mEventTimes) {
            addStrokeLocked(stroke, downTime);
        }
    }

    private void addStrokeLocked(final com.oscar.aikeyboard.keyboard.internal.GestureStrokeDrawingPoints stroke, final long downTime) {
        final int trailSize = mEventTimes.getLength();
        stroke.appendPreviewStroke(mEventTimes, mXCoordinates, mYCoordinates, mPointTypes);
        if (mEventTimes.getLength() == trailSize) {
            return;
        }
        final int[] eventTimes = mEventTimes.getPrimitiveArray();
        final int strokeId = stroke.getGestureStrokeId();
        // Because interpolation algorithm in {@link GestureStrokeDrawingPoints} can't determine
        // the interpolated points in the last segment of gesture stroke, it may need recalculation
        // of interpolation when new segments are added to the stroke.
        // {@link #mLastInterpolatedDrawIndex} holds the start index of the last segment. It may
        // be updated by the interpolation
        // {@link GestureStrokeDrawingPoints#interpolatePreviewStroke}
        // or by animation {@link #drawGestureTrail(Canvas,Paint,Rect,GestureTrailDrawingParams)}
        // below.
        final int lastInterpolatedIndex = (strokeId == mCurrentStrokeId)
                ? mLastInterpolatedDrawIndex : trailSize;
        mLastInterpolatedDrawIndex = stroke.interpolateStrokeAndReturnStartIndexOfLastSegment(
                lastInterpolatedIndex, mEventTimes, mXCoordinates, mYCoordinates, mPointTypes);
        if (strokeId != mCurrentStrokeId) {
            final int elapsedTime = (int)(downTime - mCurrentTimeBase);
            for (int i = mTrailStartIndex; i < trailSize; i++) {
                // Decay the previous strokes' event times.
                eventTimes[i] -= elapsedTime;
            }
            final int[] xCoords = mXCoordinates.getPrimitiveArray();
            xCoords[trailSize] = markAsDownEvent(xCoords[trailSize]);
            mCurrentTimeBase = downTime - eventTimes[trailSize];
            mCurrentStrokeId = strokeId;
        }
    }

    /**
     * Calculate the alpha of a gesture trail.
     * A gesture trail starts from fully opaque. After mFadeStartDelay has been passed, the alpha
     * of a trail reduces in proportion to the elapsed time. Then after mFadeDuration has been
     * passed, a trail becomes fully transparent.
     *
     * @param elapsedTime the elapsed time since a trail has been made.
     * @param params gesture trail display parameters
     * @return the width of a gesture trail
     */
    private static int getAlpha(final int elapsedTime, final com.oscar.aikeyboard.keyboard.internal.GestureTrailDrawingParams params) {
        final int fullAlpha = Color.alpha(params.mTrailColor);
        if (elapsedTime < params.mFadeoutStartDelay) {
            return fullAlpha;
        }
        final int decreasingAlpha = fullAlpha
                * (elapsedTime - params.mFadeoutStartDelay)
                / params.mFadeoutDuration;
        return fullAlpha - decreasingAlpha;
    }

    /**
     * Calculate the width of a gesture trail.
     * A gesture trail starts from the width of mTrailStartWidth and reduces its width in proportion
     * to the elapsed time. After mTrailEndWidth has been passed, the width becomes mTraiLEndWidth.
     *
     * @param elapsedTime the elapsed time since a trail has been made.
     * @param params gesture trail display parameters
     * @return the width of a gesture trail
     */
    private static float getWidth(final int elapsedTime, final com.oscar.aikeyboard.keyboard.internal.GestureTrailDrawingParams params) {
        final float deltaWidth = params.mTrailStartWidth - params.mTrailEndWidth;
        return params.mTrailStartWidth - (deltaWidth * elapsedTime) / params.mTrailLingerDuration;
    }

    private final com.oscar.aikeyboard.keyboard.internal.RoundedLine mRoundedLine = new com.oscar.aikeyboard.keyboard.internal.RoundedLine();
    private final Rect mRoundedLineBounds = new Rect();

    /**
     * Draw gesture trail
     * @param canvas The canvas to draw the gesture trail
     * @param paint The paint object to be used to draw the gesture trail
     * @param outBoundsRect the bounding box of this gesture trail drawing
     * @param params The drawing parameters of gesture trail
     * @return true if some gesture trails remain to be drawn
     */
    public boolean drawGestureTrail(final Canvas canvas, final Paint paint,
            final Rect outBoundsRect, final com.oscar.aikeyboard.keyboard.internal.GestureTrailDrawingParams params) {
        synchronized (mEventTimes) {
            return drawGestureTrailLocked(canvas, paint, outBoundsRect, params);
        }
    }

    private boolean drawGestureTrailLocked(final Canvas canvas, final Paint paint,
            final Rect outBoundsRect, final com.oscar.aikeyboard.keyboard.internal.GestureTrailDrawingParams params) {
        // Initialize bounds rectangle.
        outBoundsRect.setEmpty();
        final int trailSize = mEventTimes.getLength();
        if (trailSize == 0) {
            return false;
        }

        final int[] eventTimes = mEventTimes.getPrimitiveArray();
        final int[] xCoords = mXCoordinates.getPrimitiveArray();
        final int[] yCoords = mYCoordinates.getPrimitiveArray();
        final int[] pointTypes = mPointTypes.getPrimitiveArray();
        final int sinceDown = (int)(SystemClock.uptimeMillis() - mCurrentTimeBase);
        int startIndex;
        for (startIndex = mTrailStartIndex; startIndex < trailSize; startIndex++) {
            final int elapsedTime = sinceDown - eventTimes[startIndex];
            // Skip too old trail points.
            if (elapsedTime < params.mTrailLingerDuration) {
                break;
            }
        }
        mTrailStartIndex = startIndex;

        if (startIndex < trailSize) {
            paint.setColor(params.mTrailColor);
            paint.setStyle(Paint.Style.FILL);
            final com.oscar.aikeyboard.keyboard.internal.RoundedLine roundedLine = mRoundedLine;
            int p1x = getXCoordValue(xCoords[startIndex]);
            int p1y = yCoords[startIndex];
            final int lastTime = sinceDown - eventTimes[startIndex];
            float r1 = getWidth(lastTime, params) / 2.0f;
            for (int i = startIndex + 1; i < trailSize; i++) {
                final int elapsedTime = sinceDown - eventTimes[i];
                final int p2x = getXCoordValue(xCoords[i]);
                final int p2y = yCoords[i];
                final float r2 = getWidth(elapsedTime, params) / 2.0f;
                // Draw trail line only when the current point isn't a down point.
                if (!isDownEventXCoord(xCoords[i])) {
                    final float body1 = r1 * params.mTrailBodyRatio;
                    final float body2 = r2 * params.mTrailBodyRatio;
                    final Path path = roundedLine.makePath(p1x, p1y, body1, p2x, p2y, body2);
                    if (!path.isEmpty()) {
                        roundedLine.getBounds(mRoundedLineBounds);
                        if (params.mTrailShadowEnabled) {
                            final float shadow2 = r2 * params.mTrailShadowRatio;
                            paint.setShadowLayer(shadow2, 0.0f, 0.0f, params.mTrailColor);
                            final int shadowInset = -(int)Math.ceil(shadow2);
                            mRoundedLineBounds.inset(shadowInset, shadowInset);
                        }
                        // Take union for the bounds.
                        outBoundsRect.union(mRoundedLineBounds);
                        final int alpha = getAlpha(elapsedTime, params);
                        paint.setAlpha(alpha);
                        canvas.drawPath(path, paint);
                    }
                }
                p1x = p2x;
                p1y = p2y;
                r1 = r2;
            }
            if (DEBUG_SHOW_POINTS) {
                debugDrawPoints(canvas, startIndex, trailSize, paint);
            }
        }

        final int newSize = trailSize - startIndex;
        if (newSize < startIndex) {
            mTrailStartIndex = 0;
            if (newSize > 0) {
                System.arraycopy(eventTimes, startIndex, eventTimes, 0, newSize);
                System.arraycopy(xCoords, startIndex, xCoords, 0, newSize);
                System.arraycopy(yCoords, startIndex, yCoords, 0, newSize);
                if (DEBUG_SHOW_POINTS) {
                    System.arraycopy(pointTypes, startIndex, pointTypes, 0, newSize);
                }
            }
            mEventTimes.setLength(newSize);
            mXCoordinates.setLength(newSize);
            mYCoordinates.setLength(newSize);
            if (DEBUG_SHOW_POINTS) {
                mPointTypes.setLength(newSize);
            }
            // The start index of the last segment of the stroke
            // {@link mLastInterpolatedDrawIndex} should also be updated because all array
            // elements have just been shifted for compaction or been zeroed.
            mLastInterpolatedDrawIndex = Math.max(mLastInterpolatedDrawIndex - startIndex, 0);
        }
        return newSize > 0;
    }

    private void debugDrawPoints(final Canvas canvas, final int startIndex, final int endIndex,
            final Paint paint) {
        final int[] xCoords = mXCoordinates.getPrimitiveArray();
        final int[] yCoords = mYCoordinates.getPrimitiveArray();
        final int[] pointTypes = mPointTypes.getPrimitiveArray();
        // {@link Paint} that is zero width stroke and anti alias off draws exactly 1 pixel.
        paint.setAntiAlias(false);
        paint.setStrokeWidth(0);
        for (int i = startIndex; i < endIndex; i++) {
            final int pointType = pointTypes[i];
            if (pointType == POINT_TYPE_INTERPOLATED) {
                paint.setColor(Color.RED);
            } else if (pointType == POINT_TYPE_SAMPLED) {
                paint.setColor(0xFFA000FF);
            } else {
                paint.setColor(Color.GREEN);
            }
            canvas.drawPoint(getXCoordValue(xCoords[i]), yCoords[i], paint);
        }
        paint.setAntiAlias(true);
    }
}
