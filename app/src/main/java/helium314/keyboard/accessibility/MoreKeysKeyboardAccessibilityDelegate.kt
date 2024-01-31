/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.accessibility

import android.graphics.Rect
import helium314.keyboard.latin.utils.Log
import android.view.MotionEvent
import helium314.keyboard.keyboard.KeyDetector
import helium314.keyboard.keyboard.MoreKeysKeyboardView
import helium314.keyboard.keyboard.PointerTracker

/**
 * This class represents a delegate that can be registered in [MoreKeysKeyboardView] to
 * enhance accessibility support via composition rather via inheritance.
 */
class MoreKeysKeyboardAccessibilityDelegate(
    moreKeysKeyboardView: MoreKeysKeyboardView,
    keyDetector: KeyDetector
) : KeyboardAccessibilityDelegate<MoreKeysKeyboardView>(moreKeysKeyboardView, keyDetector) {
    private val mMoreKeysKeyboardValidBounds = Rect()
    private var mOpenAnnounceResId = 0
    private var mCloseAnnounceResId = 0
    fun setOpenAnnounce(resId: Int) {
        mOpenAnnounceResId = resId
    }

    fun setCloseAnnounce(resId: Int) {
        mCloseAnnounceResId = resId
    }

    fun onShowMoreKeysKeyboard() {
        sendWindowStateChanged(mOpenAnnounceResId)
    }

    fun onDismissMoreKeysKeyboard() {
        sendWindowStateChanged(mCloseAnnounceResId)
    }

    override fun onHoverEnter(event: MotionEvent) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverEnter: key=" + getHoverKeyOf(event))
        }
        super.onHoverEnter(event)
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex).toInt()
        val y = event.getY(actionIndex).toInt()
        val pointerId = event.getPointerId(actionIndex)
        val eventTime = event.eventTime
        mKeyboardView.onDownEvent(x, y, pointerId, eventTime)
    }

    override fun onHoverMove(event: MotionEvent) {
        super.onHoverMove(event)
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex).toInt()
        val y = event.getY(actionIndex).toInt()
        val pointerId = event.getPointerId(actionIndex)
        val eventTime = event.eventTime
        mKeyboardView.onMoveEvent(x, y, pointerId, eventTime)
    }

    override fun onHoverExit(event: MotionEvent) {
        val lastKey = lastHoverKey
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverExit: key=" + getHoverKeyOf(event) + " last=" + lastKey)
        }
        if (lastKey != null) {
            super.onHoverExitFrom(lastKey)
        }
        lastHoverKey = null
        val actionIndex = event.actionIndex
        val x = event.getX(actionIndex).toInt()
        val y = event.getY(actionIndex).toInt()
        val pointerId = event.getPointerId(actionIndex)
        val eventTime = event.eventTime
        // A hover exit event at one pixel width or height area on the edges of more keys keyboard
        // are treated as closing.
        mMoreKeysKeyboardValidBounds[0, 0, mKeyboardView.width] = mKeyboardView.height
        mMoreKeysKeyboardValidBounds.inset(CLOSING_INSET_IN_PIXEL, CLOSING_INSET_IN_PIXEL)
        if (mMoreKeysKeyboardValidBounds.contains(x, y)) {
            // Invoke {@link MoreKeysKeyboardView#onUpEvent(int,int,int,long)} as if this hover
            // exit event selects a key.
            mKeyboardView.onUpEvent(x, y, pointerId, eventTime)
            // TODO: Should fix this reference. This is a hack to clear the state of
            // {@link PointerTracker}.
            PointerTracker.dismissAllMoreKeysPanels()
            return
        }
        // Close the more keys keyboard.
        // TODO: Should fix this reference. This is a hack to clear the state of
        // {@link PointerTracker}.
        PointerTracker.dismissAllMoreKeysPanels()
    }

    companion object {
        private val TAG = MoreKeysKeyboardAccessibilityDelegate::class.java.simpleName
        private const val CLOSING_INSET_IN_PIXEL = 1
    }
}
