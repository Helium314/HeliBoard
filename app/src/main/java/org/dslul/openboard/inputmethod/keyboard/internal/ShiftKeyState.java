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
        switch (state) {
        case PRESSING_ON_SHIFTED: return "PRESSING_ON_SHIFTED";
        case IGNORING: return "IGNORING";
        default: return super.toString(state);
        }
    }
}
