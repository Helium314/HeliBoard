/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard.internal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oscar.aikeyboard.keyboard.Key;
import com.oscar.aikeyboard.keyboard.PopupKeysPanel;
import com.oscar.aikeyboard.keyboard.PointerTracker;

public interface DrawingProxy {
    /**
     * Called when a key is being pressed.
     * @param key the {@link Key} that is being pressed.
     * @param withPreview true if key popup preview should be displayed.
     */
    void onKeyPressed(@NonNull Key key, boolean withPreview);

    /**
     * Called when a key is being released.
     * @param key the {@link Key} that is being released.
     * @param withAnimation when true, key popup preview should be dismissed with animation.
     */
    void onKeyReleased(@NonNull Key key, boolean withAnimation);

    /**
     * Start showing popup keys keyboard of a key that is being long pressed.
     * @param key the {@link Key} that is being long pressed and showing popup keys keyboard.
     * @param tracker the {@link PointerTracker} that detects this long pressing.
     * @return {@link PopupKeysPanel} that is being shown. null if there is no need to show popup keys keyboard.
     */
    @Nullable
    PopupKeysPanel showPopupKeysKeyboard(@NonNull Key key, @NonNull PointerTracker tracker);

    /**
     * Start a while-typing-animation.
     * @param fadeInOrOut {@link #FADE_IN} starts while-typing-fade-in animation.
     * {@link #FADE_OUT} starts while-typing-fade-out animation.
     */
    void startWhileTypingAnimation(int fadeInOrOut);
    int FADE_IN = 0;
    int FADE_OUT = 1;

    /**
     * Show sliding-key input preview.
     * @param tracker the {@link PointerTracker} that is currently doing the sliding-key input.
     * null to dismiss the sliding-key input preview.
     */
    void showSlidingKeyInputPreview(@Nullable PointerTracker tracker);

    /**
     * Show gesture trails.
     * @param tracker the {@link PointerTracker} whose gesture trail will be shown.
     * @param showsFloatingPreviewText when true, a gesture floating preview text will be shown
     * with this <code>tracker</code>'s trail.
     */
    void showGestureTrail(@NonNull PointerTracker tracker, boolean showsFloatingPreviewText);

    /**
     * Dismiss a gesture floating preview text without delay.
     */
    void dismissGestureFloatingPreviewTextWithoutDelay();
}
