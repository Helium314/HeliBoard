/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.util.AttributeSet;
import android.widget.RelativeLayout;

import org.dslul.openboard.inputmethod.latin.common.CoordinateUtils;

import java.util.ArrayList;

public final class DrawingPreviewPlacerView extends RelativeLayout {
    private final int[] mKeyboardViewOrigin = CoordinateUtils.newInstance();

    private final ArrayList<AbstractDrawingPreview> mPreviews = new ArrayList<>();

    public DrawingPreviewPlacerView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        setWillNotDraw(false);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        final Paint layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    public void addPreview(final AbstractDrawingPreview preview) {
        if (!mPreviews.contains(preview)) {
            mPreviews.add(preview);
        }
    }

    public void setKeyboardViewGeometry(final int[] originCoords, final int width,
            final int height) {
        CoordinateUtils.copy(mKeyboardViewOrigin, originCoords);
        final int count = mPreviews.size();
        for (int i = 0; i < count; i++) {
            mPreviews.get(i).setKeyboardViewGeometry(originCoords, width, height);
        }
    }

    public void deallocateMemory() {
        final int count = mPreviews.size();
        for (int i = 0; i < count; i++) {
            mPreviews.get(i).onDeallocateMemory();
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        deallocateMemory();
    }

    @Override
    public void onDraw(final Canvas canvas) {
        super.onDraw(canvas);
        final int originX = CoordinateUtils.x(mKeyboardViewOrigin);
        final int originY = CoordinateUtils.y(mKeyboardViewOrigin);
        canvas.translate(originX, originY);
        final int count = mPreviews.size();
        for (int i = 0; i < count; i++) {
            mPreviews.get(i).drawPreview(canvas);
        }
        canvas.translate(-originX, -originY);
    }
}
