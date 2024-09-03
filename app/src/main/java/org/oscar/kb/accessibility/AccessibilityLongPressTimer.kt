/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.accessibility

import android.content.Context
import android.os.Handler
import android.os.Message
import org.oscar.kb.keyboard.Key
import org.oscar.kb.R

// Handling long press timer to show a popup keys keyboard.
internal class AccessibilityLongPressTimer(private val mCallback: LongPressTimerCallback,
                                           context: Context) : Handler() {
    interface LongPressTimerCallback {
        fun performLongClickOn(key: Key)
    }

    private val mConfigAccessibilityLongPressTimeout: Long
    override fun handleMessage(msg: Message) {
        when (msg.what) {
            MSG_LONG_PRESS -> {
                cancelLongPress()
                mCallback.performLongClickOn(msg.obj as Key)
                return
            }
            else -> {
                super.handleMessage(msg)
                return
            }
        }
    }

    fun startLongPress(key: Key?) {
        cancelLongPress()
        val longPressMessage = obtainMessage(MSG_LONG_PRESS, key)
        sendMessageDelayed(longPressMessage, mConfigAccessibilityLongPressTimeout)
    }

    fun cancelLongPress() {
        removeMessages(MSG_LONG_PRESS)
    }

    companion object {
        private const val MSG_LONG_PRESS = 1
    }

    init {
        mConfigAccessibilityLongPressTimeout = context.resources.getInteger(
                R.integer.config_accessibility_long_press_key_timeout).toLong()
    }
}