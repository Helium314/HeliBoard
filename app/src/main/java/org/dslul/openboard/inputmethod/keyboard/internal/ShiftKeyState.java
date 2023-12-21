/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import org.dslul.openboard.inputmethod.latin.utils.Log;

import androidx.annotation.NonNull;

/* package */ final class ShiftKeyState extends ModifierKeyState {
    private static final int PRESSING_ON_SHIFTED = 3; // both temporary shifted & shift locked
    private static final int IGNORING = 4;

    public ShiftKeyState(String name) {
        super(name);
    }

    @Override
    public void onOtherKeyPressed() {
        int oldState = mState;
        if (oldState == PRESSING) {
            mState = CHORDING;
        } else if (oldState == PRESSING_ON_SHIFTED) {
            mState = IGNORING;
        }
        if (DEBUG)
            Log.d(TAG, mName + ".onOtherKeyPressed: " + toString(oldState) + " > " + this);
    }

    public void onPressOnShifted() {
        int oldState = mState;
        mState = PRESSING_ON_SHIFTED;
        if (DEBUG)
            Log.d(TAG, mName + ".onPressOnShifted: " + toString(oldState) + " > " + this);
    }

    public boolean isPressingOnShifted() {
        return mState == PRESSING_ON_SHIFTED;
    }

    public boolean isIgnoring() {
        return mState == IGNORING;
    }

    @NonNull
    @Override
    public String toString() {
        return toString(mState);
    }

    @Override
    protected String toString(int state) {
        return switch (state) {
            case PRESSING_ON_SHIFTED -> "PRESSING_ON_SHIFTED";
            case IGNORING -> "IGNORING";
            default -> super.toString(state);
        };
    }
}
