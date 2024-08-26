/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.keyboard.internal;

import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.os.Handler;
import android.util.SparseArray;

import androidx.annotation.NonNull;

import org.samyarth.oskey.keyboard.PointerTracker;

/**
 * Draw preview graphics of multiple gesture trails during gesture input.
 */
public final class GestureTrailsDrawingPreview extends AbstractDrawingPreview implements Runnable {
    private final SparseArray<GestureTrailDrawingPoints> mGestureTrails = new SparseArray<>();
    private final GestureTrailDrawingParams mDrawingParams;
    private final Paint mGesturePaint;
    private int mOffscreenWidth;
    private int mOffscreenHeight;
    private int mOffscreenOffsetY;
    private Bitmap mOffscreenBuffer;
    private final Canvas mOffscreenCanvas = new Canvas();
    private final Rect mOffscreenSrcRect = new Rect();
    private final Rect mDirtyRect = new Rect();
    private final Rect mGestureTrailBoundsRect = new Rect(); // per trail

    private final Handler mDrawingHandler = new Handler();

    public GestureTrailsDrawingPreview(final TypedArray mainKeyboardViewAttr) {
        mDrawingParams = new GestureTrailDrawingParams(mainKeyboardViewAttr);
        final Paint gesturePaint = new Paint();
        gesturePaint.setAntiAlias(true);
        gesturePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC));
        mGesturePaint = gesturePaint;
    }

    @Override
    public void setKeyboardViewGeometry(@NonNull final int[] originCoords, final int width,
            final int height) {
        super.setKeyboardViewGeometry(originCoords, width, height);
        mOffscreenOffsetY = (int)(height
                * GestureStrokeRecognitionPoints.EXTRA_GESTURE_TRAIL_AREA_ABOVE_KEYBOARD_RATIO);
        mOffscreenWidth = width;
        mOffscreenHeight = mOffscreenOffsetY + height;
    }

    @Override
    public void onDeallocateMemory() {
        freeOffscreenBuffer();
    }

    private void freeOffscreenBuffer() {
        mOffscreenCanvas.setBitmap(null);
        mOffscreenCanvas.setMatrix(null);
        if (mOffscreenBuffer != null) {
            mOffscreenBuffer.recycle();
            mOffscreenBuffer = null;
        }
    }

    private void mayAllocateOffscreenBuffer() {
        if (mOffscreenBuffer != null && mOffscreenBuffer.getWidth() == mOffscreenWidth
                && mOffscreenBuffer.getHeight() == mOffscreenHeight) {
            return;
        }
        freeOffscreenBuffer();
        mOffscreenBuffer = Bitmap.createBitmap(
                mOffscreenWidth, mOffscreenHeight, Bitmap.Config.ARGB_8888);
        mOffscreenCanvas.setBitmap(mOffscreenBuffer);
        mOffscreenCanvas.translate(0, mOffscreenOffsetY);
    }

    private boolean drawGestureTrails(final Canvas offscreenCanvas, final Paint paint,
            final Rect dirtyRect) {
        // Clear previous dirty rectangle.
        if (!dirtyRect.isEmpty()) {
            paint.setColor(Color.TRANSPARENT);
            paint.setStyle(Paint.Style.FILL);
            offscreenCanvas.drawRect(dirtyRect, paint);
        }
        dirtyRect.setEmpty();
        boolean needsUpdatingGestureTrail = false;
        // Draw gesture trails to offscreen buffer.
        synchronized (mGestureTrails) {
            // Trails count == fingers count that have ever been active.
            final int trailsCount = mGestureTrails.size();
            for (int index = 0; index < trailsCount; index++) {
                final GestureTrailDrawingPoints trail = mGestureTrails.valueAt(index);
                needsUpdatingGestureTrail |= trail.drawGestureTrail(offscreenCanvas, paint,
                        mGestureTrailBoundsRect, mDrawingParams);
                // {@link #mGestureTrailBoundsRect} has bounding box of the trail.
                dirtyRect.union(mGestureTrailBoundsRect);
            }
        }
        return needsUpdatingGestureTrail;
    }

    @Override
    public void run() {
        // Update preview.
        invalidateDrawingView();
    }

    /**
     * Draws the preview
     * @param canvas The canvas where the preview is drawn.
     */
    @Override
    public void drawPreview(@NonNull final Canvas canvas) {
        if (!isPreviewEnabled()) {
            return;
        }
        mayAllocateOffscreenBuffer();
        // Draw gesture trails to offscreen buffer.
        final boolean needsUpdatingGestureTrail = drawGestureTrails(
                mOffscreenCanvas, mGesturePaint, mDirtyRect);
        if (needsUpdatingGestureTrail) {
            mDrawingHandler.removeCallbacks(this);
            mDrawingHandler.postDelayed(this, mDrawingParams.mUpdateInterval);
        }
        // Transfer offscreen buffer to screen.
        if (!mDirtyRect.isEmpty()) {
            mOffscreenSrcRect.set(mDirtyRect);
            mOffscreenSrcRect.offset(0, mOffscreenOffsetY);
            canvas.drawBitmap(mOffscreenBuffer, mOffscreenSrcRect, mDirtyRect, null);
            // Note: Defer clearing the dirty rectangle here because we will get cleared
            // rectangle on the canvas.
        }
    }

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    @Override
    public void setPreviewPosition(@NonNull final PointerTracker tracker) {
        if (!isPreviewEnabled()) {
            return;
        }
        GestureTrailDrawingPoints trail;
        synchronized (mGestureTrails) {
            trail = mGestureTrails.get(tracker.mPointerId);
            if (trail == null) {
                trail = new GestureTrailDrawingPoints();
                mGestureTrails.put(tracker.mPointerId, trail);
            }
        }
        trail.addStroke(tracker.getGestureStrokeDrawingPoints(), tracker.getDownTime());

        // TODO: Should narrow the invalidate region.
        invalidateDrawingView();
    }
}
