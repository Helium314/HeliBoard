/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.utils;

import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.util.SparseArray;

public final class TypefaceUtils {
    private static final char[] KEY_LABEL_REFERENCE_CHAR = { 'M' };
    private static final char[] KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR = { '8' };

    private TypefaceUtils() {
        // This utility class is not publicly instantiable.
    }

    // This sparse array caches key label text height in pixel indexed by key label text size.
    private static final SparseArray<Float> sTextHeightCache = new SparseArray<>();
    // Working variable for the following method.
    private static final Rect sTextHeightBounds = new Rect();

    private static float getCharHeight(final char[] referenceChar, final Paint paint) {
        final int key = getCharGeometryCacheKey(referenceChar[0], paint);
        synchronized (sTextHeightCache) {
            final Float cachedValue = sTextHeightCache.get(key);
            if (cachedValue != null) {
                return cachedValue;
            }

            paint.getTextBounds(referenceChar, 0, 1, sTextHeightBounds);
            final float height = sTextHeightBounds.height();
            sTextHeightCache.put(key, height);
            return height;
        }
    }

    // This sparse array caches key label text width in pixel indexed by key label text size.
    private static final SparseArray<Float> sTextWidthCache = new SparseArray<>();
    // Working variable for the following method.
    private static final Rect sTextWidthBounds = new Rect();

    private static float getCharWidth(final char[] referenceChar, final Paint paint) {
        final int key = getCharGeometryCacheKey(referenceChar[0], paint);
        synchronized (sTextWidthCache) {
            final Float cachedValue = sTextWidthCache.get(key);
            if (cachedValue != null) {
                return cachedValue;
            }

            paint.getTextBounds(referenceChar, 0, 1, sTextWidthBounds);
            final float width = sTextWidthBounds.width();
            sTextWidthCache.put(key, width);
            return width;
        }
    }

    private static int getCharGeometryCacheKey(final char referenceChar, final Paint paint) {
        final int labelSize = (int)paint.getTextSize();
        final Typeface face = paint.getTypeface();
        final int codePointOffset = referenceChar << 15;
        if (face == Typeface.DEFAULT) {
            return codePointOffset + labelSize;
        } else if (face == Typeface.DEFAULT_BOLD) {
            return codePointOffset + labelSize + 0x1000;
        } else if (face == Typeface.MONOSPACE) {
            return codePointOffset + labelSize + 0x2000;
        } else {
            return codePointOffset + labelSize;
        }
    }

    public static float getReferenceCharHeight(final Paint paint) {
        return getCharHeight(KEY_LABEL_REFERENCE_CHAR, paint);
    }

    public static float getReferenceCharWidth(final Paint paint) {
        return getCharWidth(KEY_LABEL_REFERENCE_CHAR, paint);
    }

    public static float getReferenceDigitWidth(final Paint paint) {
        return getCharWidth(KEY_NUMERIC_HINT_LABEL_REFERENCE_CHAR, paint);
    }

    // Working variable for the following method.
    private static final Rect sStringWidthBounds = new Rect();

    public static float getStringWidth(final String string, final Paint paint) {
        synchronized (sStringWidthBounds) {
            paint.getTextBounds(string, 0, string.length(), sStringWidthBounds);
            return sStringWidthBounds.width();
        }
    }
}
