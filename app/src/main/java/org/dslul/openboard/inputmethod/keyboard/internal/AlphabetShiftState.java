/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.util.Log;

import androidx.annotation.NonNull;

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
            case UNSHIFTED:
                mState = MANUAL_SHIFTED;
                break;
            case AUTOMATIC_SHIFTED:
                mState = MANUAL_SHIFTED_FROM_AUTO;
                break;
            case SHIFT_LOCKED:
                mState = SHIFT_LOCK_SHIFTED;
                break;
            }
        } else {
            switch (oldState) {
            case MANUAL_SHIFTED:
            case MANUAL_SHIFTED_FROM_AUTO:
            case AUTOMATIC_SHIFTED:
                mState = UNSHIFTED;
                break;
            case SHIFT_LOCK_SHIFTED:
                mState = SHIFT_LOCKED;
                break;
            }
        }
        if (DEBUG)
            Log.d(TAG, "setShifted(" + newShiftState + "): " + toString(oldState) + " > " + this);
    }

    public void setShiftLocked(boolean newShiftLockState) {
        final int oldState = mState;
        if (newShiftLockState) {
            switch (oldState) {
            case UNSHIFTED:
            case MANUAL_SHIFTED:
            case MANUAL_SHIFTED_FROM_AUTO:
            case AUTOMATIC_SHIFTED:
                mState = SHIFT_LOCKED;
                break;
            }
        } else {
            mState = UNSHIFTED;
        }
        if (DEBUG)
            Log.d(TAG, "setShiftLocked(" + newShiftLockState + "): " + toString(oldState)
                    + " > " + this);
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
        switch (state) {
        case UNSHIFTED: return "UNSHIFTED";
        case MANUAL_SHIFTED: return "MANUAL_SHIFTED";
        case MANUAL_SHIFTED_FROM_AUTO: return "MANUAL_SHIFTED_FROM_AUTO";
        case AUTOMATIC_SHIFTED: return "AUTOMATIC_SHIFTED";
        case SHIFT_LOCKED: return "SHIFT_LOCKED";
        case SHIFT_LOCK_SHIFTED: return "SHIFT_LOCK_SHIFTED";
        default: return "UNKNOWN";
        }
    }
}
