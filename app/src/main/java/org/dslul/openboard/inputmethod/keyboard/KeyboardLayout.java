/*
 * Copyright (C) 2015 The Android Open Source Project
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
 * limitations under the License
 */

package org.dslul.openboard.inputmethod.keyboard;

import androidx.annotation.NonNull;

import com.android.inputmethod.keyboard.ProximityInfo;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import java.util.ArrayList;
import java.util.List;

/**
 * KeyboardLayout maintains the keyboard layout information.
 */
public class KeyboardLayout {

    private final int[] mKeyCodes;

    private final int[] mKeyXCoordinates;
    private final int[] mKeyYCoordinates;

    private final int[] mKeyWidths;
    private final int[] mKeyHeights;

    public final int mMostCommonKeyWidth;
    public final int mMostCommonKeyHeight;

    public final int mKeyboardWidth;
    public final int mKeyboardHeight;

    public KeyboardLayout(ArrayList<Key> layoutKeys, int mostCommonKeyWidth,
            int mostCommonKeyHeight, int keyboardWidth, int keyboardHeight) {
        mMostCommonKeyWidth = mostCommonKeyWidth;
        mMostCommonKeyHeight = mostCommonKeyHeight;
        mKeyboardWidth = keyboardWidth;
        mKeyboardHeight = keyboardHeight;

        mKeyCodes = new int[layoutKeys.size()];
        mKeyXCoordinates = new int[layoutKeys.size()];
        mKeyYCoordinates = new int[layoutKeys.size()];
        mKeyWidths = new int[layoutKeys.size()];
        mKeyHeights = new int[layoutKeys.size()];

        for (int i = 0; i < layoutKeys.size(); i++) {
            Key key = layoutKeys.get(i);
            mKeyCodes[i] = Character.toLowerCase(key.getCode());
            mKeyXCoordinates[i] = key.getX();
            mKeyYCoordinates[i] = key.getY();
            mKeyWidths[i] = key.getWidth();
            mKeyHeights[i] = key.getHeight();
        }
    }

    @UsedForTesting
    public int[] getKeyCodes() {
        return mKeyCodes;
    }

    /**
     * The x-coordinate for the top-left corner of the keys.
     *
     */
    public int[] getKeyXCoordinates() {
        return mKeyXCoordinates;
    }

    /**
     * The y-coordinate for the top-left corner of the keys.
     */
    public int[] getKeyYCoordinates() {
        return mKeyYCoordinates;
    }

    /**
     * The widths of the keys which are smaller than the true hit-area due to the gaps
     * between keys. The mostCommonKey(Width/Height) represents the true key width/height
     * including the gaps.
     */
    public int[] getKeyWidths() {
        return mKeyWidths;
    }

    /**
     * The heights of the keys which are smaller than the true hit-area due to the gaps
     * between keys. The mostCommonKey(Width/Height) represents the true key width/height
     * including the gaps.
     */
    public int[] getKeyHeights() {
        return mKeyHeights;
    }

    /**
     * Factory method to create {@link KeyboardLayout} objects.
     */
    public static KeyboardLayout newKeyboardLayout(@NonNull final List<Key> sortedKeys,
            int mostCommonKeyWidth, int mostCommonKeyHeight,
            int occupiedWidth, int occupiedHeight) {
        final ArrayList<Key> layoutKeys = new ArrayList<Key>();
        for (final Key key : sortedKeys) {
            if (!ProximityInfo.needsProximityInfo(key)) {
                continue;
            }
            if (key.getCode() != ',') {
                layoutKeys.add(key);
            }
        }
        return new KeyboardLayout(layoutKeys, mostCommonKeyWidth,
                mostCommonKeyHeight, occupiedWidth, occupiedHeight);
    }
}
