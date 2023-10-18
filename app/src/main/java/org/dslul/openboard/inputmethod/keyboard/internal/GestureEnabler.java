/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import org.dslul.openboard.inputmethod.accessibility.AccessibilityUtils;

public final class GestureEnabler {
    /** True if we should handle gesture events. */
    private boolean mShouldHandleGesture;
    private boolean mMainDictionaryAvailable;
    private boolean mGestureHandlingEnabledByInputField;
    private boolean mGestureHandlingEnabledByUser;

    private void updateGestureHandlingMode() {
        mShouldHandleGesture = mMainDictionaryAvailable
                && mGestureHandlingEnabledByInputField
                && mGestureHandlingEnabledByUser
                && !AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled();
    }

    // Note that this method is called from a non-UI thread.
    public void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        mMainDictionaryAvailable = mainDictionaryAvailable;
        updateGestureHandlingMode();
    }

    public void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        mGestureHandlingEnabledByUser = gestureHandlingEnabledByUser;
        updateGestureHandlingMode();
    }

    public void setPasswordMode(final boolean passwordMode) {
        mGestureHandlingEnabledByInputField = !passwordMode;
        updateGestureHandlingMode();
    }

    public boolean shouldHandleGesture() {
        return mShouldHandleGesture;
    }
}
