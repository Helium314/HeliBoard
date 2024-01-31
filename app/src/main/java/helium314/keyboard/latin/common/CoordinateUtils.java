/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common;

import androidx.annotation.NonNull;

public final class CoordinateUtils {
    private static final int INDEX_X = 0;
    private static final int INDEX_Y = 1;
    private static final int ELEMENT_SIZE = INDEX_Y + 1;

    private CoordinateUtils() {
        // This utility class is not publicly instantiable.
    }

    @NonNull
    public static int[] newInstance() {
        return new int[ELEMENT_SIZE];
    }

    public static int x(@NonNull final int[] coords) {
        return coords[INDEX_X];
    }

    public static int y(@NonNull final int[] coords) {
        return coords[INDEX_Y];
    }

    public static void set(@NonNull final int[] coords, final int x, final int y) {
        coords[INDEX_X] = x;
        coords[INDEX_Y] = y;
    }

    public static void copy(@NonNull final int[] destination, @NonNull final int[] source) {
        destination[INDEX_X] = source[INDEX_X];
        destination[INDEX_Y] = source[INDEX_Y];
    }

    @NonNull
    public static int[] newCoordinateArray(final int arraySize) {
        return new int[ELEMENT_SIZE * arraySize];
    }

    @NonNull
    public static int[] newCoordinateArray(final int arraySize,
            final int defaultX, final int defaultY) {
        final int[] result = new int[ELEMENT_SIZE * arraySize];
        for (int i = 0; i < arraySize; ++i) {
            setXYInArray(result, i, defaultX, defaultY);
        }
        return result;
    }

    public static int xFromArray(@NonNull final int[] coordsArray, final int index) {
        return coordsArray[ELEMENT_SIZE * index + INDEX_X];
    }

    public static int yFromArray(@NonNull final int[] coordsArray, final int index) {
        return coordsArray[ELEMENT_SIZE * index + INDEX_Y];
    }

    @NonNull
    public static int[] coordinateFromArray(@NonNull final int[] coordsArray, final int index) {
        final int[] coords = newInstance();
        set(coords, xFromArray(coordsArray, index), yFromArray(coordsArray, index));
        return coords;
    }

    public static void setXYInArray(@NonNull final int[] coordsArray, final int index,
            final int x, final int y) {
        final int baseIndex = ELEMENT_SIZE * index;
        coordsArray[baseIndex + INDEX_X] = x;
        coordsArray[baseIndex + INDEX_Y] = y;
    }

    public static void setCoordinateInArray(@NonNull final int[] coordsArray, final int index,
            @NonNull final int[] coords) {
        setXYInArray(coordsArray, index, x(coords), y(coords));
    }
}
