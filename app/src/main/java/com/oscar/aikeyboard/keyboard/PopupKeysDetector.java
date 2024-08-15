/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard;

public final class PopupKeysDetector extends com.oscar.aikeyboard.keyboard.KeyDetector {
    private final int mSlideAllowanceSquare;
    private final int mSlideAllowanceSquareTop;

    public PopupKeysDetector(float slideAllowance) {
        super();
        mSlideAllowanceSquare = (int)(slideAllowance * slideAllowance);
        // Top slide allowance is slightly longer (sqrt(2) times) than other edges.
        mSlideAllowanceSquareTop = mSlideAllowanceSquare * 2;
    }

    @Override
    public boolean alwaysAllowsKeySelectionByDraggingFinger() {
        return true;
    }

    @Override
    public com.oscar.aikeyboard.keyboard.Key detectHitKey(final int x, final int y) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return null;
        }
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        com.oscar.aikeyboard.keyboard.Key nearestKey = null;
        int nearestDist = (y < 0) ? mSlideAllowanceSquareTop : mSlideAllowanceSquare;
        for (final com.oscar.aikeyboard.keyboard.Key key : keyboard.getSortedKeys()) {
            final int dist = key.squaredDistanceToEdge(touchX, touchY);
            if (dist < nearestDist) {
                nearestKey = key;
                nearestDist = dist;
            }
        }
        return nearestKey;
    }
}
