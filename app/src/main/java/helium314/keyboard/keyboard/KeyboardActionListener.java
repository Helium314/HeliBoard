/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import android.view.KeyEvent;

import helium314.keyboard.event.HapticEvent;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.InputPointers;

public interface KeyboardActionListener {

    enum Direction {
        UP, DOWN, LEFT, RIGHT
    }

    /**
     * Called when the user presses a key. This is sent before the {@link #onCodeInput} is called.
     * For keys that repeat, this is only called once.
     */
    void onPressKey(int primaryCode, int repeatCount, boolean isSinglePointer, HapticEvent hapticEvent);

    void onLongPressKey(int primaryCode);

    /**
     * Called when the user releases a key.
     */
    void onReleaseKey(int primaryCode, boolean withSliding);

    void onMoveFocus(Direction direction);
    void onPressFocusedKey();

    /** For handling hardware key presses. Returns whether the event was handled. */
    boolean onKeyDown(int keyCode, KeyEvent keyEvent);

    /** For handling hardware key presses. Returns whether the event was handled. */
    boolean onKeyUp(int keyCode, KeyEvent keyEvent);

    /**
     * Send a key code to the listener.
     */
    void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);

    /** Sends a string of characters to the listener. */
    void onTextInput(String text);

    /** Called when user started batch input. */
    void onStartBatchInput();

    /** Sends the ongoing batch input points data. */
    void onUpdateBatchInput(InputPointers batchPointers);

    /** Sends the final batch input points data. */
    void onEndBatchInput(InputPointers batchPointers);

    void onCancelBatchInput();

    /** Called when user released a finger outside any key. */
    void onCancelInput();

    /** Called when user finished sliding key input. */
    void onFinishSlidingInput();

    /** Send a non-"code input" custom request to the listener. */
    boolean onCustomRequest(int requestCode);

    /** Swipes on space bar etc. */
    boolean onHorizontalSpaceSwipe(int steps);
    boolean onVerticalSpaceSwipe(int steps);
    void onEndSpaceSwipe();
    boolean toggleNumpad(boolean withSliding, boolean forceReturnToAlpha);

    void onMoveDeletePointer(int steps);
    void onUpWithDeletePointerActive();
    void resetMetaState();

    KeyboardActionListener EMPTY_LISTENER = new Adapter();

    int SWIPE_NO_ACTION = 0;
    int SWIPE_MOVE_CURSOR = 1;
    int SWIPE_SWITCH_LANGUAGE = 2;
    int SWIPE_TOGGLE_NUMPAD = 3;
    int SWIPE_HIDE_KEYBOARD = 4;

    class Adapter implements KeyboardActionListener {

        @Override
        public void onPressKey(int primaryCode, int repeatCount, boolean isSinglePointer, HapticEvent hapticEvent) {}

        @Override
        public void onLongPressKey(int primaryCode) {}

        @Override
        public void onReleaseKey(int primaryCode, boolean withSliding) {}

        @Override
        public boolean onKeyDown(int keyCode, KeyEvent keyEvent) { return false; }

        @Override
        public boolean onKeyUp(int keyCode, KeyEvent keyEvent) { return false; }

        @Override
        public void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat) {}

        @Override
        public void onTextInput(String text) {}

        @Override
        public void onStartBatchInput() {}

        @Override
        public void onUpdateBatchInput(InputPointers batchPointers) {}

        @Override
        public void onEndBatchInput(InputPointers batchPointers) {}

        @Override
        public void onCancelBatchInput() {}

        @Override
        public void onCancelInput() {}

        @Override
        public void onFinishSlidingInput() {}

        @Override
        public boolean onCustomRequest(int requestCode) {
            return false;
        }

        @Override
        public boolean onHorizontalSpaceSwipe(int steps) {
            return false;
        }

        @Override
        public boolean onVerticalSpaceSwipe(int steps) {
            return false;
        }

        @Override
        public boolean toggleNumpad(boolean withSliding, boolean forceReturnToAlpha) {
            return false;
        }

        @Override
        public void onEndSpaceSwipe() {}

        @Override
        public void onMoveDeletePointer(int steps) {}

        @Override
        public void onUpWithDeletePointerActive() {}

        @Override
        public void resetMetaState() {}

        @Override
        public void onMoveFocus(Direction direction) {}

        @Override
        public void onPressFocusedKey() {}
    }
}
