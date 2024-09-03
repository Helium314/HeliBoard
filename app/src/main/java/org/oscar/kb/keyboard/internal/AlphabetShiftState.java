/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.keyboard.internal;


import androidx.annotation.NonNull;

import org.oscar.kb.latin.utils.Log;

public final class AlphabetShiftState {
    private static final String TAG = AlphabetShiftState.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final int UNSHIFTED = 0;
    private static final int MANUAL_SHIFTED = 1;
    private static final int MANUAL_SHIFTED_FROM_AUTO = 2;
    private static final int AUTOMATIC_SHIFTED = 3;
    private static final int SHIFT_LOCKED = 4;
    private static final int SHIFT_LOCK_SHIFTED = 5;

    private int mState = UNSHIFTED;

    public void setShifted(boolean newShiftState) {
        final int oldState = mState;
        if (newShiftState) {
            switch (oldState) {
                case UNSHIFTED -> mState = MANUAL_SHIFTED;
                case AUTOMATIC_SHIFTED -> mState = MANUAL_SHIFTED_FROM_AUTO;
                case SHIFT_LOCKED -> mState = SHIFT_LOCK_SHIFTED;
            }
        } else {
            switch (oldState) {
                case MANUAL_SHIFTED, MANUAL_SHIFTED_FROM_AUTO, AUTOMATIC_SHIFTED -> mState = UNSHIFTED;
                case SHIFT_LOCK_SHIFTED -> mState = SHIFT_LOCKED;
            }
        }
        if (DEBUG)
            Log.d(TAG, "setShifted(" + newShiftState + "): " + toString(oldState) + " > " + this);
    }

    public void setShiftLocked(boolean newShiftLockState) {
        final int oldState = mState;
        if (newShiftLockState) {
            switch (oldState) {
                case UNSHIFTED, MANUAL_SHIFTED, MANUAL_SHIFTED_FROM_AUTO, AUTOMATIC_SHIFTED -> mState = SHIFT_LOCKED;
            }
        } else {
            mState = UNSHIFTED;
        }
        if (DEBUG)
            Log.d(TAG, "setShiftLocked(" + newShiftLockState + "): " + toString(oldState) + " > " + this);
    }

    public void setAutomaticShifted() {
        final int oldState = mState;
        mState = AUTOMATIC_SHIFTED;
        if (DEBUG)
            Log.d(TAG, "setAutomaticShifted: " + toString(oldState) + " > " + this);
    }

    public boolean isShiftedOrShiftLocked() {
        return mState != UNSHIFTED;
    }

    public boolean isShiftLocked() {
        return mState == SHIFT_LOCKED || mState == SHIFT_LOCK_SHIFTED;
    }

    public boolean isShiftLockShifted() {
        return mState == SHIFT_LOCK_SHIFTED;
    }

    public boolean isAutomaticShifted() {
        return mState == AUTOMATIC_SHIFTED;
    }

    public boolean isManualShifted() {
        return mState == MANUAL_SHIFTED || mState == MANUAL_SHIFTED_FROM_AUTO
                || mState == SHIFT_LOCK_SHIFTED;
    }

    public boolean isManualShiftedFromAutomaticShifted() {
        return mState == MANUAL_SHIFTED_FROM_AUTO;
    }

    @NonNull
    @Override
    public String toString() {
        return toString(mState);
    }

    private static String toString(int state) {
        return switch (state) {
            case UNSHIFTED -> "UNSHIFTED";
            case MANUAL_SHIFTED -> "MANUAL_SHIFTED";
            case MANUAL_SHIFTED_FROM_AUTO -> "MANUAL_SHIFTED_FROM_AUTO";
            case AUTOMATIC_SHIFTED -> "AUTOMATIC_SHIFTED";
            case SHIFT_LOCKED -> "SHIFT_LOCKED";
            case SHIFT_LOCK_SHIFTED -> "SHIFT_LOCK_SHIFTED";
            default -> "UNKNOWN";
        };
    }
}
