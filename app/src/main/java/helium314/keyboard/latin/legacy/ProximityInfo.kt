/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.legacy

import android.graphics.Rect
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.internal.TouchPositionCorrection
import helium314.keyboard.latin.common.Constants
import helium314.keyboard.latin.utils.JniUtils
import helium314.keyboard.latin.utils.Log
import java.util.ArrayList
import java.util.Arrays
import java.util.Collections
import java.util.Locale
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

class ProximityInfo(
    gridWidth: Int,
    gridHeight: Int,
    minWidth: Int,
    height: Int,
    mostCommonKeyWidth: Int,
    mostCommonKeyHeight: Int,
    sortedKeys: List<Key>,
    touchPositionCorrection: TouchPositionCorrection
) {
    private val mGridWidth: Int = gridWidth
    private val mGridHeight: Int = gridHeight
    private val mGridSize: Int = mGridWidth * mGridHeight
    private val mCellWidth: Int = (minWidth + mGridWidth - 1) / mGridWidth
    private val mCellHeight: Int = (height + mGridHeight - 1) / mGridHeight
    private val mKeyboardMinWidth: Int = minWidth
    private val mKeyboardHeight: Int = height
    private val mMostCommonKeyWidth: Int = mostCommonKeyWidth
    private val mMostCommonKeyHeight: Int = mostCommonKeyHeight
    private val mSortedKeys: List<Key> = sortedKeys
    private val mGridNeighbors: Array<List<Key>>

    private var mNativeProximityInfo: Long = 0

    val nativeProximityInfo: Long
        get() = mNativeProximityInfo

    init {
        mGridNeighbors = Array(mGridSize) { EMPTY_KEY_LIST }
        if (minWidth != 0 && height != 0) {
            computeNearestNeighbors()
            try {
                mNativeProximityInfo = createNativeProximityInfo(touchPositionCorrection)
            } catch (e: Throwable) {
                Log.e(TAG, "could not create proximity info", e)
                mNativeProximityInfo = 0
            }
        }
    }

    protected fun finalize() {
        if (mNativeProximityInfo != 0L) {
            releaseProximityInfoNative(mNativeProximityInfo)
            mNativeProximityInfo = 0
        }
    }

    private fun computeNearestNeighbors() {
        val keyCount = mSortedKeys.size
        val gridSize = mGridNeighbors.size
        val threshold = (mMostCommonKeyWidth * SEARCH_DISTANCE).toInt()
        val thresholdSquared = threshold * threshold
        // Round-up so we don't have any pixels outside the grid
        val lastPixelXCoordinate = mGridWidth * mCellWidth - 1
        val lastPixelYCoordinate = mGridHeight * mCellHeight - 1

        val neighborsFlatBuffer = arrayOfNulls<Key>(gridSize * keyCount)
        val neighborCountPerCell = IntArray(gridSize)
        val halfCellWidth = mCellWidth / 2
        val halfCellHeight = mCellHeight / 2

        for (key in mSortedKeys) {
            if (key.isSpacer) continue

            val keyX = key.x
            val keyY = key.y
            val topPixelWithinThreshold = keyY - threshold
            val yDeltaToGrid = topPixelWithinThreshold % mCellHeight
            val yMiddleOfTopCell = topPixelWithinThreshold - yDeltaToGrid + halfCellHeight
            val yStart = max(
                halfCellHeight,
                yMiddleOfTopCell + if (yDeltaToGrid <= halfCellHeight) 0 else mCellHeight
            )
            val yEnd = min(lastPixelYCoordinate, keyY + key.height + threshold)

            val leftPixelWithinThreshold = keyX - threshold
            val xDeltaToGrid = leftPixelWithinThreshold % mCellWidth
            val xMiddleOfLeftCell = leftPixelWithinThreshold - xDeltaToGrid + halfCellWidth
            val xStart = max(
                halfCellWidth,
                xMiddleOfLeftCell + if (xDeltaToGrid <= halfCellWidth) 0 else mCellWidth
            )
            val xEnd = min(lastPixelXCoordinate, keyX + key.width + threshold)

            var baseIndexOfCurrentRow = (yStart / mCellHeight) * mGridWidth + (xStart / mCellWidth)
            var centerY = yStart
            while (centerY <= yEnd) {
                var index = baseIndexOfCurrentRow
                var centerX = xStart
                while (centerX <= xEnd) {
                    if (key.squaredDistanceToEdge(centerX, centerY) < thresholdSquared) {
                        neighborsFlatBuffer[index * keyCount + neighborCountPerCell[index]] = key
                        ++neighborCountPerCell[index]
                    }
                    ++index
                    centerX += mCellWidth
                }
                baseIndexOfCurrentRow += mGridWidth
                centerY += mCellHeight
            }
        }

        for (i in 0 until gridSize) {
            val indexStart = i * keyCount
            val indexEnd = indexStart + neighborCountPerCell[i]
            val neighbors = ArrayList<Key>(indexEnd - indexStart)
            for (index in indexStart until indexEnd) {
                neighborsFlatBuffer[index]?.let { neighbors.add(it) }
            }
            mGridNeighbors[i] = Collections.unmodifiableList(neighbors)
        }
    }

    private fun createNativeProximityInfo(touchPositionCorrection: TouchPositionCorrection): Long {
        val proximityCharsArray = IntArray(mGridSize * MAX_PROXIMITY_CHARS_SIZE)
        Arrays.fill(proximityCharsArray, Constants.NOT_A_CODE)
        for (i in 0 until mGridSize) {
            val neighborKeys = mGridNeighbors[i]
            val proximityCharsLength = neighborKeys.size
            var infoIndex = i * MAX_PROXIMITY_CHARS_SIZE
            for (j in 0 until proximityCharsLength) {
                val neighborKey = neighborKeys[j]
                if (!needsProximityInfo(neighborKey)) {
                    continue
                }
                proximityCharsArray[infoIndex] = neighborKey.code
                infoIndex++
            }
        }
        if (DEBUG) {
            val sb = StringBuilder()
            for (i in 0 until mGridSize) {
                sb.setLength(0)
                for (j in 0 until MAX_PROXIMITY_CHARS_SIZE) {
                    val code = proximityCharsArray[i * MAX_PROXIMITY_CHARS_SIZE + j]
                    if (code == Constants.NOT_A_CODE) {
                        break
                    }
                    if (sb.isNotEmpty()) sb.append(" ")
                    sb.append(Constants.printableCode(code))
                }
                Log.d(TAG, "proxmityChars[$i]: $sb")
            }
        }

        val keyCount = getProximityInfoKeysCount(mSortedKeys)
        val keyXCoordinates = IntArray(keyCount)
        val keyYCoordinates = IntArray(keyCount)
        val keyWidths = IntArray(keyCount)
        val keyHeights = IntArray(keyCount)
        val keyCharCodes = IntArray(keyCount)
        val sweetSpotCenterXs: FloatArray?
        val sweetSpotCenterYs: FloatArray?
        val sweetSpotRadii: FloatArray?

        var infoIndex = 0
        for (key in mSortedKeys) {
            if (!needsProximityInfo(key)) {
                continue
            }
            keyXCoordinates[infoIndex] = key.x
            keyYCoordinates[infoIndex] = key.y
            keyWidths[infoIndex] = key.width
            keyHeights[infoIndex] = key.height
            keyCharCodes[infoIndex] = key.code
            infoIndex++
        }

        if (touchPositionCorrection.isValid) {
            if (DEBUG) {
                Log.d(TAG, "touchPositionCorrection: ON")
            }
            sweetSpotCenterXs = FloatArray(keyCount)
            sweetSpotCenterYs = FloatArray(keyCount)
            sweetSpotRadii = FloatArray(keyCount)
            val rows = touchPositionCorrection.rows
            val defaultRadius = DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS *
                    hypot(mMostCommonKeyWidth.toDouble(), mMostCommonKeyHeight.toDouble()).toFloat()
            
            infoIndex = 0
            for (key in mSortedKeys) {
                if (!needsProximityInfo(key)) {
                    continue
                }
                val hitBox = key.hitBox
                sweetSpotCenterXs[infoIndex] = hitBox.exactCenterX()
                sweetSpotCenterYs[infoIndex] = hitBox.exactCenterY()
                sweetSpotRadii[infoIndex] = defaultRadius
                val row = hitBox.top / mMostCommonKeyHeight
                if (row < rows) {
                    val hitBoxWidth = hitBox.width()
                    val hitBoxHeight = hitBox.height()
                    val hitBoxDiagonal = hypot(hitBoxWidth.toDouble(), hitBoxHeight.toDouble()).toFloat()
                    sweetSpotCenterXs[infoIndex] += touchPositionCorrection.getX(row) * hitBoxWidth
                    sweetSpotCenterYs[infoIndex] += touchPositionCorrection.getY(row) * hitBoxHeight
                    sweetSpotRadii[infoIndex] = touchPositionCorrection.getRadius(row) * hitBoxDiagonal
                }
                if (DEBUG) {
                    Log.d(
                        TAG, String.format(
                            Locale.US,
                            "  [%2d] row=%d x/y/r=%7.2f/%7.2f/%5.2f %s code=%s", infoIndex, row,
                            sweetSpotCenterXs[infoIndex], sweetSpotCenterYs[infoIndex],
                            sweetSpotRadii[infoIndex], if (row < rows) "correct" else "default",
                            Constants.printableCode(key.code)
                        )
                    )
                }
                infoIndex++
            }
        } else {
            sweetSpotCenterXs = null
            sweetSpotCenterYs = null
            sweetSpotRadii = null
            if (DEBUG) {
                Log.d(TAG, "touchPositionCorrection: OFF")
            }
        }

        return setProximityInfoNative(
            mKeyboardMinWidth, mKeyboardHeight, mGridWidth, mGridHeight,
            mMostCommonKeyWidth, mMostCommonKeyHeight, proximityCharsArray, keyCount,
            keyXCoordinates, keyYCoordinates, keyWidths, keyHeights, keyCharCodes,
            sweetSpotCenterXs, sweetSpotCenterYs, sweetSpotRadii
        )
    }

    fun fillArrayWithNearestKeyCodes(x: Int, y: Int, primaryKeyCode: Int, dest: IntArray) {
        val destLength = dest.size
        if (destLength < 1) {
            return
        }
        var index = 0
        if (primaryKeyCode > Constants.CODE_SPACE) {
            dest[index++] = primaryKeyCode
        }
        val nearestKeys = getNearestKeys(x, y)
        for (key in nearestKeys) {
            if (index >= destLength) {
                break
            }
            val code = key.code
            if (code <= Constants.CODE_SPACE) {
                break
            }
            dest[index++] = code
        }
        if (index < destLength) {
            dest[index] = Constants.NOT_A_CODE
        }
    }

    fun getNearestKeys(x: Int, y: Int): List<Key> {
        if (x >= 0 && x < mKeyboardMinWidth && y >= 0 && y < mKeyboardHeight) {
            val index = (y / mCellHeight) * mGridWidth + (x / mCellWidth)
            if (index < mGridSize) {
                return mGridNeighbors[index]
            }
        }
        return EMPTY_KEY_LIST
    }

    companion object {
        private val TAG = ProximityInfo::class.java.simpleName
        private const val DEBUG = false

        // Must be equal to MAX_PROXIMITY_CHARS_SIZE in native/jni/src/defines.h
        const val MAX_PROXIMITY_CHARS_SIZE = 16
        /** Number of key widths from current touch point to search for nearest keys. */
        private const val SEARCH_DISTANCE = 1.2f
        private val EMPTY_KEY_LIST = emptyList<Key>()
        private const val DEFAULT_TOUCH_POSITION_CORRECTION_RADIUS = 0.15f

        init {
            JniUtils.loadNativeLibrary()
        }

        @JvmStatic
        private external fun setProximityInfoNative(
            displayWidth: Int, displayHeight: Int,
            gridWidth: Int, gridHeight: Int,
            mostCommonKeyWidth: Int, mostCommonKeyHeight: Int,
            proximityCharsArray: IntArray,
            keyCount: Int,
            keyXCoordinates: IntArray,
            keyYCoordinates: IntArray,
            keyWidths: IntArray,
            keyHeights: IntArray,
            keyCharCodes: IntArray,
            sweetSpotCenterXs: FloatArray?,
            sweetSpotCenterYs: FloatArray?,
            sweetSpotRadii: FloatArray?
        ): Long

        @JvmStatic
        private external fun releaseProximityInfoNative(nativeProximityInfo: Long)

        @JvmStatic
        fun needsProximityInfo(key: Key): Boolean {
            // Don't include special keys into ProximityInfo.
            return key.code >= Constants.CODE_SPACE
        }

        private fun getProximityInfoKeysCount(keys: List<Key>): Int {
            var count = 0
            for (key in keys) {
                if (needsProximityInfo(key)) {
                    count++
                }
            }
            return count
        }
    }
}
