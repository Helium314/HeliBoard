/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package helium314.keyboard.latin.common

import helium314.keyboard.latin.WordComposer
import kotlin.random.Random

/** An immutable class that encapsulates a snapshot of word composition data. */
class ComposedData(
    @JvmField val mInputPointers: InputPointers,
    @JvmField val mIsBatchMode: Boolean,
    @JvmField val mTypedWord: String
) {
    /**
     * Copy the code points in the typed word to a destination array of ints.
     *
     * If the array is too small to hold the code points in the typed word, nothing is copied and
     * -1 is returned.
     *
     * @param destination the array of ints.
     * @return the number of copied code points.
     */
    fun copyCodePointsExceptTrailingSingleQuotesAndReturnCodePointCount(
        destination: IntArray
    ): Int {
        // lastIndex is exclusive
        val lastIndex = (mTypedWord.length - StringUtils.getTrailingSingleQuotesCount(mTypedWord))
        if (lastIndex <= 0) {
            return 0 // The string is empty or contains only single quotes.
        }

        // The following function counts the number of code points in the text range which begins
        // at index 0 and extends to the character at lastIndex.
        val codePointSize = Character.codePointCount(mTypedWord, 0, lastIndex)
        if (codePointSize > destination.size) {
            return -1
        }
        return StringUtils.copyCodePointsAndReturnCodePointCount(
            destination, mTypedWord, 0, lastIndex, true
        )
    }

    companion object {
        fun createForWord(word: String): ComposedData {
            val codePoints = StringUtils.toCodePointArray(word)
            val coordinates = CoordinateUtils.newCoordinateArray(codePoints.size)
            for (i in codePoints.indices) {
                CoordinateUtils.setXYInArray(coordinates, i, Random.nextBits(2), Random.nextBits(2))
            }
            return WordComposer().apply { setComposingWord(codePoints, coordinates) }.composedDataSnapshot
        }
    }
}
