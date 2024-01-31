/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common;

import androidx.annotation.NonNull;

import helium314.keyboard.annotations.UsedForTesting;

import java.util.Arrays;

// TODO: This class is not thread-safe.
public final class ResizableIntArray {
    @NonNull
    private int[] mArray;
    private int mLength;

    public ResizableIntArray(final int capacity) {
        reset(capacity);
    }

    public int get(final int index) {
        if (index < mLength) {
            return mArray[index];
        }
        throw new ArrayIndexOutOfBoundsException("length=" + mLength + "; index=" + index);
    }

    public void addAt(final int index, final int val) {
        if (index < mLength) {
            mArray[index] = val;
        } else {
            mLength = index;
            add(val);
        }
    }

    public void add(final int val) {
        final int currentLength = mLength;
        ensureCapacity(currentLength + 1);
        mArray[currentLength] = val;
        mLength = currentLength + 1;
    }

    /**
     * Calculate the new capacity of {@code mArray}.
     * @param minimumCapacity the minimum capacity that the {@code mArray} should have.
     * @return the new capacity that the {@code mArray} should have. Returns zero when there is no
     * need to expand {@code mArray}.
     */
    private int calculateCapacity(final int minimumCapacity) {
        final int currentCapcity = mArray.length;
        if (currentCapcity < minimumCapacity) {
            final int nextCapacity = currentCapcity * 2;
            // The following is the same as return Math.max(minimumCapacity, nextCapacity);
            return minimumCapacity > nextCapacity ? minimumCapacity : nextCapacity;
        }
        return 0;
    }

    private void ensureCapacity(final int minimumCapacity) {
        final int newCapacity = calculateCapacity(minimumCapacity);
        if (newCapacity > 0) {
            // TODO: Implement primitive array pool.
            mArray = Arrays.copyOf(mArray, newCapacity);
        }
    }

    public int getLength() {
        return mLength;
    }

    public void setLength(final int newLength) {
        ensureCapacity(newLength);
        mLength = newLength;
    }

    public void reset(final int capacity) {
        // TODO: Implement primitive array pool.
        mArray = new int[capacity];
        mLength = 0;
    }

    @NonNull
    public int[] getPrimitiveArray() {
        return mArray;
    }

    public void set(@NonNull final ResizableIntArray ip) {
        // TODO: Implement primitive array pool.
        mArray = ip.mArray;
        mLength = ip.mLength;
    }

    public void copy(@NonNull final ResizableIntArray ip) {
        final int newCapacity = calculateCapacity(ip.mLength);
        if (newCapacity > 0) {
            // TODO: Implement primitive array pool.
            mArray = new int[newCapacity];
        }
        System.arraycopy(ip.mArray, 0, mArray, 0, ip.mLength);
        mLength = ip.mLength;
    }

    public void append(@NonNull final ResizableIntArray src, final int startPos, final int length) {
        if (length == 0) {
            return;
        }
        final int currentLength = mLength;
        final int newLength = currentLength + length;
        ensureCapacity(newLength);
        System.arraycopy(src.mArray, startPos, mArray, currentLength, length);
        mLength = newLength;
    }

    public void fill(final int value, final int startPos, final int length) {
        if (startPos < 0 || length < 0) {
            throw new IllegalArgumentException("startPos=" + startPos + "; length=" + length);
        }
        final int endPos = startPos + length;
        ensureCapacity(endPos);
        Arrays.fill(mArray, startPos, endPos, value);
        if (mLength < endPos) {
            mLength = endPos;
        }
    }

    /**
     * Shift to the left by elementCount, discarding elementCount pointers at the start.
     * @param elementCount how many elements to shift.
     */
    @UsedForTesting
    public void shift(final int elementCount) {
        System.arraycopy(mArray, elementCount, mArray, 0, mLength - elementCount);
        mLength -= elementCount;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mLength; i++) {
            if (i != 0) {
                sb.append(",");
            }
            sb.append(mArray[i]);
        }
        return "[" + sb + "]";
    }
}
