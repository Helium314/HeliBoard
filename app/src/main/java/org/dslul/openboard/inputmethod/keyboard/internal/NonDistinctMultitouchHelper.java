/*
 * Copyright (C) 2013 The Android Open Source Project
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
import android.view.MotionEvent;

import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.KeyDetector;
import org.dslul.openboard.inputmethod.keyboard.PointerTracker;
import org.dslul.openboard.inputmethod.latin.common.CoordinateUtils;

public final class NonDistinctMultitouchHelper {
    private static final String TAG = NonDistinctMultitouchHelper.class.getSimpleName();

    private static final int MAIN_POINTER_TRACKER_ID = 0;
    private int mOldPointerCount = 1;
    private Key mOldKey;
    private final int[] mLastCoords = CoordinateUtils.newInstance();

    public void processMotionEvent(final MotionEvent me, final KeyDetector keyDetector) {
        final int pointerCount = me.getPointerCount();
        final int oldPointerCount = mOldPointerCount;
        mOldPointerCount = pointerCount;
        // Ignore continuous multi-touch events because we can't trust the coordinates
        // in multi-touch events.
        if (pointerCount > 1 && oldPointerCount > 1) {
            return;
        }

        // Use only main pointer tracker.
        final PointerTracker mainTracker = PointerTracker.getPointerTracker(
                MAIN_POINTER_TRACKER_ID);
        final int action = me.getActionMasked();
        final int index = me.getActionIndex();
        final long eventTime = me.getEventTime();
        final long downTime = me.getDownTime();

        // In single-touch.
        if (oldPointerCount == 1 && pointerCount == 1) {
            if (me.getPointerId(index) == mainTracker.mPointerId) {
                mainTracker.processMotionEvent(me, keyDetector);
                return;
            }
            // Inject a copied event.
            injectMotionEvent(action, me.getX(index), me.getY(index), downTime, eventTime,
                    mainTracker, keyDetector);
            return;
        }

        // Single-touch to multi-touch transition.
        if (oldPointerCount == 1 && pointerCount == 2) {
            // Send an up event for the last pointer, be cause we can't trust the coordinates of
            // this multi-touch event.
            mainTracker.getLastCoordinates(mLastCoords);
            final int x = CoordinateUtils.x(mLastCoords);
            final int y = CoordinateUtils.y(mLastCoords);
            mOldKey = mainTracker.getKeyOn(x, y);
            // Inject an artifact up event for the old key.
            injectMotionEvent(MotionEvent.ACTION_UP, x, y, downTime, eventTime,
                    mainTracker, keyDetector);
            return;
        }

        // Multi-touch to single-touch transition.
        if (oldPointerCount == 2 && pointerCount == 1) {
            // Send a down event for the latest pointer if the key is different from the previous
            // key.
            final int x = (int)me.getX(index);
            final int y = (int)me.getY(index);
            final Key newKey = mainTracker.getKeyOn(x, y);
            if (mOldKey != newKey) {
                // Inject an artifact down event for the new key.
                // An artifact up event for the new key will usually be injected as a single-touch.
                injectMotionEvent(MotionEvent.ACTION_DOWN, x, y, downTime, eventTime,
                        mainTracker, keyDetector);
                if (action == MotionEvent.ACTION_UP) {
                    // Inject an artifact up event for the new key also.
                    injectMotionEvent(MotionEvent.ACTION_UP, x, y, downTime, eventTime,
                            mainTracker, keyDetector);
                }
            }
            return;
        }

        Log.w(TAG, "Unknown touch panel behavior: pointer count is "
                + pointerCount + " (previously " + oldPointerCount + ")");
    }

    private static void injectMotionEvent(final int action, final float x, final float y,
            final long downTime, final long eventTime, final PointerTracker tracker,
            final KeyDetector keyDetector) {
        final MotionEvent me = MotionEvent.obtain(
                downTime, eventTime, action, x, y, 0 /* metaState */);
        try {
            tracker.processMotionEvent(me, keyDetector);
        } finally {
            me.recycle();
        }
    }
}
