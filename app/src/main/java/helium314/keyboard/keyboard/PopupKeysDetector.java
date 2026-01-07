/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

public final class PopupKeysDetector extends KeyDetector {
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
    public Key detectHitKey(final int x, final int y) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard == null) {
            return null;
        }
        final int touchX = getTouchX(x);
        final int touchY = getTouchY(y);

        Key nearestKey = null;
        int nearestDist = (y < 0) ? mSlideAllowanceSquareTop : mSlideAllowanceSquare;
        for (final Key key : keyboard.getSortedKeys()) {
            final int dist = key.squaredDistanceToEdge(touchX, touchY);
            if (dist < nearestDist) {
                nearestKey = key;
                nearestDist = dist;
            }
        }
        return nearestKey;
    }
}
