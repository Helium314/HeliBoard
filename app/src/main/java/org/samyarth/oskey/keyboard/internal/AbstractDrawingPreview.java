/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.keyboard.internal;

import android.graphics.Canvas;
import android.view.View;

import androidx.annotation.NonNull;

import org.samyarth.oskey.keyboard.PointerTracker;

/**
 * Abstract base class for previews that are drawn on DrawingPreviewPlacerView, e.g.,
 * GestureFloatingTextDrawingPreview, GestureTrailsDrawingPreview, and
 * SlidingKeyInputDrawingPreview.
 */
public abstract class AbstractDrawingPreview {
    private View mDrawingView;
    private boolean mPreviewEnabled;
    private boolean mHasValidGeometry;

    public void setDrawingView(@NonNull final DrawingPreviewPlacerView drawingView) {
        mDrawingView = drawingView;
        drawingView.addPreview(this);
    }

    protected void invalidateDrawingView() {
        if (mDrawingView != null) {
            mDrawingView.invalidate();
        }
    }

    protected final boolean isPreviewEnabled() {
        return mPreviewEnabled && mHasValidGeometry;
    }

    public final void setPreviewEnabled(final boolean enabled) {
        mPreviewEnabled = enabled;
    }

    /**
     * Set {@link org.samyarth.oskey.keyboard.MainKeyboardView} geometry and position in the window of input method.
     * The class that is overriding this method must call this super implementation.
     *
     * @param originCoords the top-left coordinates of the {@link org.samyarth.oskey.keyboard.MainKeyboardView} in
     *        the input method window coordinate-system. This is unused but has a point in an
     *        extended class, such as {@link GestureTrailsDrawingPreview}.
     * @param width the width of {@link org.samyarth.oskey.keyboard.MainKeyboardView}.
     * @param height the height of {@link org.samyarth.oskey.keyboard.MainKeyboardView}.
     */
    public void setKeyboardViewGeometry(@NonNull final int[] originCoords, final int width,
            final int height) {
        mHasValidGeometry = (width > 0 && height > 0);
    }

    public abstract void onDeallocateMemory();

    /**
     * Draws the preview
     * @param canvas The canvas where the preview is drawn.
     */
    public abstract void drawPreview(@NonNull final Canvas canvas);

    /**
     * Set the position of the preview.
     * @param tracker The new location of the preview is based on the points in PointerTracker.
     */
    public abstract void setPreviewPosition(@NonNull final PointerTracker tracker);
}
