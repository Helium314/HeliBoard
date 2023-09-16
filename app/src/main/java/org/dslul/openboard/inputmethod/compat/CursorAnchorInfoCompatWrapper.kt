package org.dslul.openboard.inputmethod.compat

import android.annotation.TargetApi
import android.graphics.Matrix
import android.graphics.RectF
import android.os.Build
import android.os.Build.VERSION_CODES
import android.view.inputmethod.CursorAnchorInfo

/**
 * A wrapper for [CursorAnchorInfo], which has been introduced in API Level 21. You can use
 * this wrapper to avoid direct dependency on newly introduced types.
 */
open class CursorAnchorInfoCompatWrapper internal constructor() {
    open val selectionStart: Int
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val selectionEnd: Int
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val composingText: CharSequence?
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val composingTextStart: Int
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val matrix: Matrix?
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open fun getCharacterBounds(index: Int): RectF? {
        throw UnsupportedOperationException("not supported.")
    }

    open fun getCharacterBoundsFlags(index: Int): Int {
        throw UnsupportedOperationException("not supported.")
    }

    open val insertionMarkerBaseline: Float
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val insertionMarkerBottom: Float
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val insertionMarkerHorizontal: Float
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val insertionMarkerTop: Float
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    open val insertionMarkerFlags: Int
        get() {
            throw UnsupportedOperationException("not supported.")
        }

    @TargetApi(VERSION_CODES.LOLLIPOP)
    private class RealWrapper(private val mInstance: CursorAnchorInfo) : CursorAnchorInfoCompatWrapper() {

        override val selectionStart: Int
        get() {
            return mInstance.selectionStart
        }

        override val selectionEnd: Int
        get() {
            return mInstance.selectionEnd
        }

        override val composingText: CharSequence?
        get() {
            return mInstance.composingText
        }

        override val composingTextStart: Int
        get() {
            return mInstance.composingTextStart
        }

        override val matrix: Matrix?
        get() {
            return mInstance.matrix
        }

        override fun getCharacterBounds(index: Int): RectF? {
            return mInstance.getCharacterBounds(index)
        }

        override fun getCharacterBoundsFlags(index: Int): Int {
            return mInstance.getCharacterBoundsFlags(index)
        }

        override val insertionMarkerBaseline: Float
        get() {
            return mInstance.insertionMarkerBaseline
        }

        override val insertionMarkerBottom: Float
        get() {
            return mInstance.insertionMarkerBottom
        }

        override val insertionMarkerHorizontal: Float
        get() {
            return mInstance.insertionMarkerHorizontal
        }

        override val insertionMarkerTop: Float
        get() {
            return mInstance.insertionMarkerTop
        }

        override val insertionMarkerFlags: Int
        get() {
            return mInstance.insertionMarkerFlags
        }

    }

    companion object {
        /**
         * The insertion marker or character bounds have at least one visible region.
         */
        const val FLAG_HAS_VISIBLE_REGION = 0x01
        /**
         * The insertion marker or character bounds have at least one invisible (clipped) region.
         */
        const val FLAG_HAS_INVISIBLE_REGION = 0x02
        /**
         * The insertion marker or character bounds is placed at right-to-left (RTL) character.
         */
        const val FLAG_IS_RTL = 0x04

        @JvmStatic
        @TargetApi(VERSION_CODES.LOLLIPOP)
        fun wrap(instance: CursorAnchorInfo?): CursorAnchorInfoCompatWrapper? {
            return if (Build.VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
                null
            } else instance?.let { RealWrapper(it) }
        }
    }
}