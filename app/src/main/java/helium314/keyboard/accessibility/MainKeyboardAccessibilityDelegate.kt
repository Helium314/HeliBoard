/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.accessibility

import android.graphics.Rect
import android.os.SystemClock
import helium314.keyboard.latin.utils.Log
import android.view.MotionEvent
import helium314.keyboard.accessibility.AccessibilityLongPressTimer.LongPressTimerCallback
import helium314.keyboard.keyboard.*
import helium314.keyboard.latin.R
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName

/**
 * This class represents a delegate that can be registered in [MainKeyboardView] to enhance
 * accessibility support via composition rather via inheritance.
 */
class MainKeyboardAccessibilityDelegate(
    mainKeyboardView: MainKeyboardView,
    keyDetector: KeyDetector
) : KeyboardAccessibilityDelegate<MainKeyboardView>(mainKeyboardView, keyDetector), LongPressTimerCallback {
    /** The most recently set keyboard mode.  */
    private var mLastKeyboardMode: KeyboardMode? = null
    // The rectangle region to ignore hover events.
    private val mBoundsToIgnoreHoverEvent = Rect()
    private val mAccessibilityLongPressTimer = AccessibilityLongPressTimer(this /* callback */, mainKeyboardView.context)

    // Since this method is called even when accessibility is off, make sure
    // to check the state before announcing anything.
    // Announce the language name only when the language is changed.
    // Announce the mode only when the mode is changed.
    // Announce the keyboard type only when the type is changed.
    /**
     * {@inheritDoc}
     */
    override var keyboard: Keyboard?
        get() = super.keyboard
        set(keyboard) {
            if (keyboard == null) {
                return
            }
            val lastKeyboard = super.keyboard
            super.keyboard = keyboard
            val lastKeyboardMode = mLastKeyboardMode
            mLastKeyboardMode = keyboard.mId.mode
            // Since this method is called even when accessibility is off, make sure
            // to check the state before announcing anything.
            if (!AccessibilityUtils.instance.isAccessibilityEnabled) {
                return
            }
            // Announce the language name only when the language is changed.
            if (lastKeyboard == null || keyboard.mId.subtype != lastKeyboard.mId.subtype) {
                announceKeyboardLanguage(keyboard)
                return
            }
            // Announce the mode only when the mode is changed.
            if (keyboard.mId.mode != lastKeyboardMode) {
                announceKeyboardMode(keyboard)
                return
            }
            // Announce the keyboard type only when the type is changed.
            if (keyboard.mId.element != lastKeyboard.mId.element) {
                announceKeyboardType(keyboard, lastKeyboard)
                return
            }
        }

    /**
     * Called when the keyboard is hidden and accessibility is enabled.
     */
    fun onHideWindow() {
        if (mLastKeyboardMode != null) {
            announceKeyboardHidden()
        }
        mLastKeyboardMode = null
    }

    /**
     * Announces which language of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     */
    private fun announceKeyboardLanguage(keyboard: Keyboard) {
        sendWindowStateChanged(keyboard.mId.subtype.rawSubtype.displayName())
    }

    /**
     * Announces which type of keyboard is being displayed.
     * If the keyboard type is unknown, no announcement is made.
     *
     * @param keyboard The new keyboard.
     */
    private fun announceKeyboardMode(keyboard: Keyboard) {
        val res = mKeyboardView.resources
        val modeText = res.getString(keyboard.mId.mode.contentDescription)
        val text = res.getString(R.string.announce_keyboard_mode, modeText)
        // TODO: this is saying "Showing showing (text) keyboard"
        sendWindowStateChanged(text)
    }

    /**
     * Announces which type of keyboard is being displayed.
     *
     * @param keyboard The new keyboard.
     * @param lastKeyboard The last keyboard.
     */
    private fun announceKeyboardType(keyboard: Keyboard, lastKeyboard: Keyboard) {
        val lastElement = lastKeyboard.mId.element
        val element = keyboard.mId.element
        when (element) {
            KeyboardElement.ALPHABET, KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED -> {
                if (lastElement == KeyboardElement.ALPHABET || lastElement == KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED) {
                    // Transition between alphabet mode and automatic shifted mode should be silently
                    // ignored because it can be determined by each key's talk back announce.
                    return
                }
            }
            KeyboardElement.ALPHABET_MANUAL_SHIFTED -> {
                if (lastElement == KeyboardElement.ALPHABET_AUTOMATIC_SHIFTED) {
                    // Resetting automatic shifted mode by pressing the shift key causes the transition
                    // from automatic shifted to manual shifted that should be silently ignored.
                    return
                }
            }
            KeyboardElement.ALPHABET_SHIFT_LOCK_SHIFTED -> {
                if (lastElement == KeyboardElement.ALPHABET_SHIFT_LOCKED) {
                    // Resetting caps locked mode by pressing the shift key causes the transition
                    // from shift locked to shift lock shifted that should be silently ignored.
                    return
                }
            }
            else -> {}
        }
        if (element.isEmojiLayout) {
            // the emoji pages are handled in EmojiCategory
            return
        }
        val contentDescription = element.contentDescription
        if (contentDescription != 0) {
            sendWindowStateChanged(contentDescription)
        }
    }

    /**
     * Announces that the keyboard has been hidden.
     */
    private fun announceKeyboardHidden() {
        sendWindowStateChanged(R.string.announce_keyboard_hidden)
    }

    override fun performClickOn(key: Key) {
        val x = key.hitBox.centerX()
        val y = key.hitBox.centerY()
        if (DEBUG_HOVER) {
            Log.d(TAG, "performClickOn: key=" + key
                    + " inIgnoreBounds=" + mBoundsToIgnoreHoverEvent.contains(x, y))
        }
        if (mBoundsToIgnoreHoverEvent.contains(x, y)) {
            // This hover exit event points to the key that should be ignored.
            // Clear the ignoring region to handle further hover events.
            mBoundsToIgnoreHoverEvent.setEmpty()
            return
        }
        super.performClickOn(key)
    }

    override fun onHoverEnterTo(key: Key) {
        val x = key.hitBox.centerX()
        val y = key.hitBox.centerY()
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverEnterTo: key=" + key
                    + " inIgnoreBounds=" + mBoundsToIgnoreHoverEvent.contains(x, y))
        }
        mAccessibilityLongPressTimer.cancelLongPress()
        if (mBoundsToIgnoreHoverEvent.contains(x, y)) {
            return
        }
        // This hover enter event points to the key that isn't in the ignoring region.
        // Further hover events should be handled.
        mBoundsToIgnoreHoverEvent.setEmpty()
        super.onHoverEnterTo(key)
        if (key.isLongPressEnabled) {
            mAccessibilityLongPressTimer.startLongPress(key)
        }
    }

    override fun onHoverExitFrom(key: Key) {
        val x = key.hitBox.centerX()
        val y = key.hitBox.centerY()
        if (DEBUG_HOVER) {
            Log.d(TAG, "onHoverExitFrom: key=" + key
                    + " inIgnoreBounds=" + mBoundsToIgnoreHoverEvent.contains(x, y))
        }
        mAccessibilityLongPressTimer.cancelLongPress()
        super.onHoverExitFrom(key)
    }

    override fun performLongClickOn(key: Key) {
        if (DEBUG_HOVER) {
            Log.d(TAG, "performLongClickOn: key=$key")
        }
        val tracker = PointerTracker.getPointerTracker(HOVER_EVENT_POINTER_ID)
        val eventTime = SystemClock.uptimeMillis()
        val x = key.hitBox.centerX()
        val y = key.hitBox.centerY()
        val downEvent = MotionEvent.obtain(eventTime, eventTime, MotionEvent.ACTION_DOWN, x.toFloat(), y.toFloat(), 0)
        // Inject a fake down event to {@link PointerTracker} to handle a long press correctly.
        tracker.processMotionEvent(downEvent, mKeyDetector)
        downEvent.recycle()
        // Invoke {@link PointerTracker#onLongPressed()} as if a long press timeout has passed.
        tracker.onLongPressed()
        // If {@link Key#hasNoPanelAutoPopupKeys()} is true (such as "0 +" key on the phone layout)
        // or a key invokes IME switcher dialog, we should just ignore the next
        // {@link #onRegisterHoverKey(Key,MotionEvent)}. It can be determined by whether
        // {@link PointerTracker} is in operation or not.
        if (tracker.isInOperation) {
            // This long press shows a popup keys keyboard and further hover events should be
            // handled.
            mBoundsToIgnoreHoverEvent.setEmpty()
            return
        }
        // This long press has handled at {@link MainKeyboardView#onLongPress(PointerTracker)}.
        // We should ignore further hover events on this key.
        mBoundsToIgnoreHoverEvent.set(key.hitBox)
        if (key.hasNoPanelAutoPopupKey()) {
            // This long press has registered a code point without showing a popup keys keyboard.
            // We should talk back the code point if possible.
            val codePointOfNoPanelAutoPopupKey = key.popupKeys?.get(0)?.mCode ?: return
            val text: String = KeyCodeDescriptionMapper.instance.getDescriptionForCodePoint(
                    mKeyboardView.context, codePointOfNoPanelAutoPopupKey) ?: return
            sendWindowStateChanged(text)
        }
    }

    companion object {
        private val TAG = MainKeyboardAccessibilityDelegate::class.java.simpleName
    }
}
