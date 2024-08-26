/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.latin.common;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * Utility methods for working with collections.
 */
public final class CollectionUtils {
    private CollectionUtils() {
        // This utility class is not publicly instantiable.
    }

    /**
     * Converts a sub-range of the given array to an ArrayList of the appropriate type.
     * @param array Array to be converted.
     * @param start First index inclusive to be converted.
     * @param end Last index exclusive to be converted.
     * @throws IllegalArgumentException if start or end are out of range or start &gt; end.
     */
    @NonNull
    public static <E> ArrayList<E> arrayAsList(@NonNull final E[] array, final int start,
            final int end) {
        if (start < 0 || start > end || end > array.length) {
            throw new IllegalArgumentException("Invalid start: " + start + " end: " + end
                    + " with array.length: " + array.length);
        }

        final ArrayList<E> list = new ArrayList<>(end - start);
        for (int i = start; i < end; i++) {
            list.add(array[i]);
        }
        return list;
    }
}
