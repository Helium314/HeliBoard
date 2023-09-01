/*
 * Copyright (C) 2014 The Android Open Source Project
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

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.PointerTracker;

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
     * Start showing more keys keyboard of a key that is being long pressed.
     * @param key the {@link Key} that is being long pressed and showing more keys keyboard.
     * @param tracker the {@link PointerTracker} that detects this long pressing.
     * @return {@link MoreKeysPanel} that is being shown. null if there is no need to show more keys
     *     keyboard.
     */
    @Nullable
    MoreKeysPanel showMoreKeysKeyboard(@NonNull Key key, @NonNull PointerTracker tracker);

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
