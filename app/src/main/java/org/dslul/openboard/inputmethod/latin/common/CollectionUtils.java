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

package org.dslul.openboard.inputmethod.latin.common;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

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

    /**
     * Tests whether c contains no elements, true if c is null or c is empty.
     * @param c Collection to test.
     * @return Whether c contains no elements.
     */
    @UsedForTesting
    public static boolean isNullOrEmpty(@Nullable final Collection c) {
        return c == null || c.isEmpty();
    }

    /**
     * Tests whether map contains no elements, true if map is null or map is empty.
     * @param map Map to test.
     * @return Whether map contains no elements.
     */
    @UsedForTesting
    public static boolean isNullOrEmpty(@Nullable final Map map) {
        return map == null || map.isEmpty();
    }
}
