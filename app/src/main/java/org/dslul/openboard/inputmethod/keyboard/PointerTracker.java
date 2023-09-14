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

package org.dslul.openboard.inputmethod.keyboard;

import static java.lang.Math.abs;

import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.SystemClock;
import android.util.Log;
import android.view.MotionEvent;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.keyboard.internal.BatchInputArbiter;
import org.dslul.openboard.inputmethod.keyboard.internal.BatchInputArbiter.BatchInputArbiterListener;
import org.dslul.openboard.inputmethod.keyboard.internal.BogusMoveEventDetector;
import org.dslul.openboard.inputmethod.keyboard.internal.DrawingProxy;
import org.dslul.openboard.inputmethod.keyboard.internal.GestureEnabler;
import org.dslul.openboard.inputmethod.keyboard.internal.GestureStrokeDrawingParams;
import org.dslul.openboard.inputmethod.keyboard.internal.GestureStrokeDrawingPoints;
import org.dslul.openboard.inputmethod.keyboard.internal.GestureStrokeRecognitionParams;
import org.dslul.openboard.inputmethod.keyboard.internal.PointerTrackerQueue;
import org.dslul.openboard.inputmethod.keyboard.internal.TimerProxy;
import org.dslul.openboard.inputmethod.keyboard.internal.TypingTimeRecorder;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.CoordinateUtils;
import org.dslul.openboard.inputmethod.latin.common.InputPointers;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValues;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import java.util.ArrayList;
import java.util.List;

public final class PointerTracker implements PointerTrackerQueue.Element,
        BatchInputArbiterListener {
    private static final String TAG = PointerTracker.class.getSimpleName();
    private static final boolean DEBUG_EVENT = false;
    private static final boolean DEBUG_MOVE_EVENT = false;
    private static final boolean DEBUG_LISTENER = false;
    private static final boolean DEBUG_MODE = DebugFlags.DEBUG_ENABLED || DEBUG_EVENT;

    static final class PointerTrackerParams {
        public final boolean mKeySelectionByDraggingFinger;
        public final int mTouchNoiseThresholdTime;
        public final int mTouchNoiseThresholdDistance;
        public final int mSuppressKeyPreviewAfterBatchInputDuration;
        public final int mKeyRepeatStartTimeout;
        public final int mKeyRepeatInterval;
        public final int mLongPressShiftLockTimeout;

        public PointerTrackerParams(final TypedArray mainKeyboardViewAttr) {
            mKeySelectionByDraggingFinger = mainKeyboardViewAttr.getBoolean(
                    R.styleable.MainKeyboardView_keySelectionByDraggingFinger, false);
            mTouchNoiseThresholdTime = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_touchNoiseThresholdTime, 0);
            mTouchNoiseThresholdDistance = mainKeyboardViewAttr.getDimensionPixelSize(
                    R.styleable.MainKeyboardView_touchNoiseThresholdDistance, 0);
            mSuppressKeyPreviewAfterBatchInputDuration = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_suppressKeyPreviewAfterBatchInputDuration, 0);
            mKeyRepeatStartTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatStartTimeout, 0);
            mKeyRepeatInterval = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_keyRepeatInterval, 0);
            mLongPressShiftLockTimeout = mainKeyboardViewAttr.getInt(
                    R.styleable.MainKeyboardView_longPressShiftLockTimeout, 0);
        }
    }

    private static final GestureEnabler sGestureEnabler = new GestureEnabler();

    // Parameters for pointer handling.
    private static PointerTrackerParams sParams;
    private static final int sPointerStep = (int)(10.0 * Resources.getSystem().getDisplayMetrics().density);
    private static GestureStrokeRecognitionParams sGestureStrokeRecognitionParams;
    private static GestureStrokeDrawingParams sGestureStrokeDrawingParams;
    private static boolean sNeedsPhantomSuddenMoveEventHack;
    // Move this threshold to resource.
    // TODO: Device specific parameter would be better for device specific hack?
    private static final float PHANTOM_SUDDEN_MOVE_THRESHOLD = 0.25f; // in keyWidth

    private static final ArrayList<PointerTracker> sTrackers = new ArrayList<>();
    private static final PointerTrackerQueue sPointerTrackerQueue = new PointerTrackerQueue();

    public final int mPointerId;

    private static DrawingProxy sDrawingProxy;
    private static TimerProxy sTimerProxy;
    private static KeyboardActionListener sListener = KeyboardActionListener.EMPTY_LISTENER;

    // The {@link KeyDetector} is set whenever the down event is processed. Also this is updated
    // when new {@link Keyboard} is set by {@link #setKeyDetector(KeyDetector)}.
    private KeyDetector mKeyDetector = new KeyDetector();
    private Keyboard mKeyboard;
    private int mPhantomSuddenMoveThreshold;
    private final BogusMoveEventDetector mBogusMoveEventDetector = new BogusMoveEventDetector();

    private boolean mIsDetectingGesture = false; // per PointerTracker.
    private static boolean sInGesture = false;
    private static TypingTimeRecorder sTypingTimeRecorder;

    // The position and time at which first down event occurred.
    private long mDownTime;
    @NonNull
    private int[] mDownCoordinates = CoordinateUtils.newInstance();
    private long mUpTime;

    // The current key where this pointer is.
    private Key mCurrentKey = null;
    // The position where the current key was recognized for the first time.
    private int mKeyX;
    private int mKeyY;

    // Last pointer position.
    private int mLastX;
    private int mLastY;
    private int mStartX;
    private int mStartY;
    private int mPreviousY;
    private long mStartTime;
    private boolean mCursorMoved = false;
    private boolean mLanguageSlideStarted = false;

    // true if keyboard layout has been changed.
    private boolean mKeyboardLayoutHasBeenChanged;
    private int keyboardChangeOccupiedHeightDifference;

    // true if this pointer is no longer triggering any action because it has been canceled.
    private boolean mIsTrackingForActionDisabled;

    // the more keys panel currently being shown. equals null if no panel is active.
    private MoreKeysPanel mMoreKeysPanel;

    private static final int MULTIPLIER_FOR_LONG_PRESS_TIMEOUT_IN_SLIDING_INPUT = 3;
    // true if this pointer is in the dragging finger mode.
    boolean mIsInDraggingFinger;
    // true if this pointer is sliding from a modifier key and in the sliding key input mode,
    // so that further modifier keys should be ignored.
    boolean mIsInSlidingKeyInput;
    // if not a NOT_A_CODE, the key of this code is repeating
    private int mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;

    // true if dragging finger is allowed.
    private boolean mIsAllowedDraggingFinger;

    private final BatchInputArbiter mBatchInputArbiter;
    private final GestureStrokeDrawingPoints mGestureStrokeDrawingPoints;

    // TODO: Add PointerTrackerFactory singleton and move some class static methods into it.
    public static void init(final TypedArray mainKeyboardViewAttr, final TimerProxy timerProxy,
            final DrawingProxy drawingProxy) {
        sParams = new PointerTrackerParams(mainKeyboardViewAttr);
        sGestureStrokeRecognitionParams = new GestureStrokeRecognitionParams(mainKeyboardViewAttr);
        sGestureStrokeDrawingParams = new GestureStrokeDrawingParams(mainKeyboardViewAttr);
        sTypingTimeRecorder = new TypingTimeRecorder(
                sGestureStrokeRecognitionParams.mStaticTimeThresholdAfterFastTyping,
                sParams.mSuppressKeyPreviewAfterBatchInputDuration);

        final Resources res = mainKeyboardViewAttr.getResources();
        sNeedsPhantomSuddenMoveEventHack = Boolean.parseBoolean(
                ResourceUtils.getDeviceOverrideValue(res,
                        R.array.phantom_sudden_move_event_device_list, Boolean.FALSE.toString()));
        BogusMoveEventDetector.init(res);

        sTimerProxy = timerProxy;
        sDrawingProxy = drawingProxy;
    }

    // Note that this method is called from a non-UI thread.
    public static void setMainDictionaryAvailability(final boolean mainDictionaryAvailable) {
        sGestureEnabler.setMainDictionaryAvailability(mainDictionaryAvailable);
    }

    public static void setGestureHandlingEnabledByUser(final boolean gestureHandlingEnabledByUser) {
        sGestureEnabler.setGestureHandlingEnabledByUser(gestureHandlingEnabledByUser);
    }

    public static PointerTracker getPointerTracker(final int id) {
        final ArrayList<PointerTracker> trackers = sTrackers;

        // Create pointer trackers until we can get 'id+1'-th tracker, if needed.
        for (int i = trackers.size(); i <= id; i++) {
            final PointerTracker tracker = new PointerTracker(i);
            trackers.add(tracker);
        }

        return trackers.get(id);
    }

    public static boolean isAnyInDraggingFinger() {
        return sPointerTrackerQueue.isAnyInDraggingFinger();
    }

    public static void cancelAllPointerTrackers() {
        sPointerTrackerQueue.cancelAllPointerTrackers();
    }

    public static void setKeyboardActionListener(final KeyboardActionListener listener) {
        sListener = listener;
    }

    public static void setKeyDetector(final KeyDetector keyDetector) {
        final Keyboard keyboard = keyDetector.getKeyboard();
        if (keyboard == null) {
            return;
        }
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setKeyDetectorInner(keyDetector);
        }
        sGestureEnabler.setPasswordMode(keyboard.mId.passwordInput());
    }

    public static void setReleasedKeyGraphicsToAllKeys() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.setReleasedKeyGraphics(tracker.getKey(), true /* withAnimation */);
        }
    }

    public static void dismissAllMoreKeysPanels() {
        final int trackersSize = sTrackers.size();
        for (int i = 0; i < trackersSize; ++i) {
            final PointerTracker tracker = sTrackers.get(i);
            tracker.dismissMoreKeysPanel();
        }
    }

    private PointerTracker(final int id) {
        mPointerId = id;
        mBatchInputArbiter = new BatchInputArbiter(id, sGestureStrokeRecognitionParams);
        mGestureStrokeDrawingPoints = new GestureStrokeDrawingPoints(sGestureStrokeDrawingParams);
    }

    // Returns true if keyboard has been changed by this callback.
    private boolean callListenerOnPressAndCheckKeyboardLayoutChange(final Key key,
            final int repeatCount) {
        // While gesture input is going on, this method should be a no-operation. But when gesture
        // input has been canceled, <code>sInGesture</code> and <code>mIsDetectingGesture</code>
        // are set to false. To keep this method is a no-operation,
        // <code>mIsTrackingForActionDisabled</code> should also be taken account of.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return false;
        }
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onPress    : %s%s%s%s", mPointerId,
                    (key == null ? "none" : Constants.printableCode(key.getCode())),
                    ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ? "" : " disabled",
                    repeatCount > 0 ? " repeatCount=" + repeatCount : ""));
        }
        if (ignoreModifierKey) {
            return false;
        }
        if (key.isEnabled()) {
            sListener.onPressKey(key.getCode(), repeatCount, getActivePointerTrackerCount() == 1);
            final boolean keyboardLayoutHasBeenChanged = mKeyboardLayoutHasBeenChanged;
            mKeyboardLayoutHasBeenChanged = false;
            sTimerProxy.startTypingStateTimer(key);
            return keyboardLayoutHasBeenChanged;
        }
        return false;
    }

    // Note that we need primaryCode argument because the keyboard may in shifted state and the
    // primaryCode is different from {@link Key#mKeyCode}.
    private void callListenerOnCodeInput(final Key key, final int primaryCode, final int x,
            final int y, final long eventTime, final boolean isKeyRepeat) {
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        final boolean altersCode = key.altCodeWhileTyping() && sTimerProxy.isTypingState();
        final int code = altersCode ? key.getAltCode() : primaryCode;
        if (DEBUG_LISTENER) {
            final String output = code == Constants.CODE_OUTPUT_TEXT
                    ? key.getOutputText() : Constants.printableCode(code);
            Log.d(TAG, String.format("[%d] onCodeInput: %4d %4d %s%s%s", mPointerId, x, y,
                    output, ignoreModifierKey ? " ignoreModifier" : "",
                    altersCode ? " altersCode" : "", key.isEnabled() ? "" : " disabled"));
        }
        if (ignoreModifierKey) {
            return;
        }
        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        if (key.isEnabled() || altersCode) {
            sTypingTimeRecorder.onCodeInput(code, eventTime);
            if (code == Constants.CODE_OUTPUT_TEXT) {
                sListener.onTextInput(key.getOutputText());
            } else if (code != Constants.CODE_UNSPECIFIED) {
                if (mKeyboard.hasProximityCharsCorrection(code)) {
                    sListener.onCodeInput(code, x, y, isKeyRepeat);
                } else {
                    sListener.onCodeInput(code,
                            Constants.NOT_A_COORDINATE, Constants.NOT_A_COORDINATE, isKeyRepeat);
                }
            }
        }
    }

    // Note that we need primaryCode argument because the keyboard may be in shifted state and the
    // primaryCode is different from {@link Key#mKeyCode}.
    private void callListenerOnRelease(final Key key, final int primaryCode,
            final boolean withSliding) {
        // See the comment at {@link #callListenerOnPressAndCheckKeyboardLayoutChange(Key}}.
        if (sInGesture || mIsDetectingGesture || mIsTrackingForActionDisabled) {
            return;
        }
        final boolean ignoreModifierKey = mIsInDraggingFinger && key.isModifier();
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onRelease  : %s%s%s%s", mPointerId,
                    Constants.printableCode(primaryCode),
                    withSliding ? " sliding" : "", ignoreModifierKey ? " ignoreModifier" : "",
                    key.isEnabled() ?  "": " disabled"));
        }
        if (ignoreModifierKey) {
            return;
        }
        if (key.isEnabled()) {
            sListener.onReleaseKey(primaryCode, withSliding);
        }
    }

    private void callListenerOnFinishSlidingInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onFinishSlidingInput", mPointerId));
        }
        sListener.onFinishSlidingInput();
    }

    private void callListenerOnCancelInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onCancelInput", mPointerId));
        }
        sListener.onCancelInput();
    }

    private void setKeyDetectorInner(final KeyDetector keyDetector) {
        final Keyboard keyboard = keyDetector.getKeyboard();
        if (keyboard == null) {
            return;
        }
        if (keyDetector == mKeyDetector && keyboard == mKeyboard) {
            return;
        }
        if (mKeyboard != null) {
            // changing keyboards may change height
            // since y is measured from top of view, this change needs to be considered in some places
            keyboardChangeOccupiedHeightDifference = keyboard.mOccupiedHeight - mKeyboard.mOccupiedHeight;
        }
        mKeyDetector = keyDetector;
        mKeyboard = keyboard;
        // Mark that keyboard layout has been changed.
        mKeyboardLayoutHasBeenChanged = true;
        final int keyWidth = mKeyboard.mMostCommonKeyWidth;
        final int keyHeight = mKeyboard.mMostCommonKeyHeight;
        mBatchInputArbiter.setKeyboardGeometry(keyWidth, mKeyboard.mOccupiedHeight);
        // Keep {@link #mCurrentKey} that comes from previous keyboard. The key preview of
        // {@link #mCurrentKey} will be dismissed by {@setReleasedKeyGraphics(Key)} via
        // {@link onMoveEventInternal(int,int,long)} or {@link #onUpEventInternal(int,int,long)}.
        mPhantomSuddenMoveThreshold = (int)(keyWidth * PHANTOM_SUDDEN_MOVE_THRESHOLD);
        mBogusMoveEventDetector.setKeyboardGeometry(keyWidth, keyHeight);
    }

    @Override
    public boolean isInDraggingFinger() {
        return mIsInDraggingFinger;
    }

    @Nullable
    public Key getKey() {
        return mCurrentKey;
    }

    @Override
    public boolean isModifier() {
        return mCurrentKey != null && mCurrentKey.isModifier();
    }

    public Key getKeyOn(final int x, final int y) {
        return mKeyDetector.detectHitKey(x, y);
    }

    private void setReleasedKeyGraphics(@Nullable final Key key, final boolean withAnimation) {
        if (key == null) {
            return;
        }

        sDrawingProxy.onKeyReleased(key, withAnimation);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    sDrawingProxy.onKeyReleased(shiftKey, false /* withAnimation */);
                }
            }
        }

        if (key.altCodeWhileTyping()) {
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                sDrawingProxy.onKeyReleased(altKey, false /* withAnimation */);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    sDrawingProxy.onKeyReleased(k, false /* withAnimation */);
                }
            }
        }
    }

    private static boolean needsToSuppressKeyPreviewPopup(final long eventTime) {
        if (!sGestureEnabler.shouldHandleGesture()) return false;
        return sTypingTimeRecorder.needsToSuppressKeyPreviewPopup(eventTime);
    }

    private void setPressedKeyGraphics(@Nullable final Key key, final long eventTime) {
        if (key == null) {
            return;
        }

        // Even if the key is disabled, it should respond if it is in the altCodeWhileTyping state.
        final boolean altersCode = key.altCodeWhileTyping() && sTimerProxy.isTypingState();
        final boolean needsToUpdateGraphics = key.isEnabled() || altersCode;
        if (!needsToUpdateGraphics) {
            return;
        }

        final boolean noKeyPreview = sInGesture || needsToSuppressKeyPreviewPopup(eventTime);
        sDrawingProxy.onKeyPressed(key, !noKeyPreview);

        if (key.isShift()) {
            for (final Key shiftKey : mKeyboard.mShiftKeys) {
                if (shiftKey != key) {
                    sDrawingProxy.onKeyPressed(shiftKey, false /* withPreview */);
                }
            }
        }

        if (altersCode) {
            final int altCode = key.getAltCode();
            final Key altKey = mKeyboard.getKey(altCode);
            if (altKey != null) {
                sDrawingProxy.onKeyPressed(altKey, false /* withPreview */);
            }
            for (final Key k : mKeyboard.mAltCodeKeysWhileTyping) {
                if (k != key && k.getAltCode() == altCode) {
                    sDrawingProxy.onKeyPressed(k, false /* withPreview */);
                }
            }
        }
    }

    public GestureStrokeDrawingPoints getGestureStrokeDrawingPoints() {
        return mGestureStrokeDrawingPoints;
    }

    public void getLastCoordinates(@NonNull final int[] outCoords) {
        CoordinateUtils.set(outCoords, mLastX, mLastY);
    }

    public long getDownTime() {
        return mDownTime;
    }

    public void getDownCoordinates(@NonNull final int[] outCoords) {
        CoordinateUtils.copy(outCoords, mDownCoordinates);
    }

    private Key onDownKey(final int x, final int y, final long eventTime) {
        mDownTime = eventTime;
        CoordinateUtils.set(mDownCoordinates, x, y);
        mBogusMoveEventDetector.onDownKey();
        return onMoveToNewKey(onMoveKeyInternal(x, y), x, y);
    }

    private static int getDistance(final int x1, final int y1, final int x2, final int y2) {
        return (int)Math.hypot(x1 - x2, y1 - y2);
    }

    private Key onMoveKeyInternal(final int x, final int y) {
        mBogusMoveEventDetector.onMoveKey(getDistance(x, y, mLastX, mLastY));
        mLastX = x;
        mLastY = y;
        return mKeyDetector.detectHitKey(x, y);
    }

    private Key onMoveKey(final int x, final int y) {
        return onMoveKeyInternal(x, y);
    }

    private Key onMoveToNewKey(final Key newKey, final int x, final int y) {
        mCurrentKey = newKey;
        mKeyX = x;
        mKeyY = y;
        return newKey;
    }

    /* package */ static int getActivePointerTrackerCount() {
        return sPointerTrackerQueue.size();
    }

    private boolean isOldestTrackerInQueue() {
        return sPointerTrackerQueue.getOldestElement() == this;
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onStartBatchInput() {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onStartBatchInput", mPointerId));
        }
        sListener.onStartBatchInput();
        dismissAllMoreKeysPanels();
        sTimerProxy.cancelLongPressTimersOf(this);
    }

    private void showGestureTrail() {
        if (mIsTrackingForActionDisabled) {
            return;
        }
        // A gesture floating preview text will be shown at the oldest pointer/finger on the screen.
        sDrawingProxy.showGestureTrail(
                this, isOldestTrackerInQueue() /* showsFloatingPreviewText */);
    }

    public void updateBatchInputByTimer(final long syntheticMoveEventTime) {
        mBatchInputArbiter.updateBatchInputByTimer(syntheticMoveEventTime, this);
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onUpdateBatchInput(final InputPointers aggregatedPointers, final long eventTime) {
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onUpdateBatchInput: batchPoints=%d", mPointerId,
                    aggregatedPointers.getPointerSize()));
        }
        sListener.onUpdateBatchInput(aggregatedPointers);
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onStartUpdateBatchInputTimer() {
        sTimerProxy.startUpdateBatchInputTimer(this);
    }

    // Implements {@link BatchInputArbiterListener}.
    @Override
    public void onEndBatchInput(final InputPointers aggregatedPointers, final long eventTime) {
        sTypingTimeRecorder.onEndBatchInput(eventTime);
        sTimerProxy.cancelAllUpdateBatchInputTimers();
        if (mIsTrackingForActionDisabled) {
            return;
        }
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onEndBatchInput   : batchPoints=%d",
                    mPointerId, aggregatedPointers.getPointerSize()));
        }
        sListener.onEndBatchInput(aggregatedPointers);
    }

    private void cancelBatchInput() {
        cancelAllPointerTrackers();
        mIsDetectingGesture = false;
        if (!sInGesture) {
            return;
        }
        sInGesture = false;
        if (DEBUG_LISTENER) {
            Log.d(TAG, String.format("[%d] onCancelBatchInput", mPointerId));
        }
        sListener.onCancelBatchInput();
    }

    public void processMotionEvent(final MotionEvent me, final KeyDetector keyDetector) {
        final int action = me.getActionMasked();
        final long eventTime = me.getEventTime();
        if (action == MotionEvent.ACTION_MOVE) {
            // When this pointer is the only active pointer and is showing a more keys panel,
            // we should ignore other pointers' motion event.
            final boolean shouldIgnoreOtherPointers =
                    isShowingMoreKeysPanel() && getActivePointerTrackerCount() == 1;
            final int pointerCount = me.getPointerCount();
            for (int index = 0; index < pointerCount; index++) {
                final int id = me.getPointerId(index);
                if (shouldIgnoreOtherPointers && id != mPointerId) {
                    continue;
                }
                final int x = (int)me.getX(index);
                final int y = (int)me.getY(index);
                final PointerTracker tracker = getPointerTracker(id);
                tracker.onMoveEvent(x, y, eventTime, me);
            }
            return;
        }
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        switch (action) {
        case MotionEvent.ACTION_DOWN:
        case MotionEvent.ACTION_POINTER_DOWN:
            onDownEvent(x, y, eventTime, keyDetector);
            break;
        case MotionEvent.ACTION_UP:
        case MotionEvent.ACTION_POINTER_UP:
            onUpEvent(x, y, eventTime);
            break;
        case MotionEvent.ACTION_CANCEL:
            onCancelEvent(x, y, eventTime);
            break;
        }
    }

    private void onDownEvent(final int x, final int y, final long eventTime,
            final KeyDetector keyDetector) {
        setKeyDetectorInner(keyDetector);
        if (DEBUG_EVENT) {
            printTouchEvent("onDownEvent:", x, y, eventTime);
        }
        // Naive up-to-down noise filter.
        final long deltaT = eventTime - mUpTime;
        if (deltaT < sParams.mTouchNoiseThresholdTime) {
            final int distance = getDistance(x, y, mLastX, mLastY);
            if (distance < sParams.mTouchNoiseThresholdDistance) {
                if (DEBUG_MODE)
                    Log.w(TAG, String.format("[%d] onDownEvent:"
                            + " ignore potential noise: time=%d distance=%d",
                            mPointerId, deltaT, distance));
                cancelTrackingForAction();
                return;
            }
        }

        final Key key = getKeyOn(x, y);
        mBogusMoveEventDetector.onActualDownEvent(x, y);
        if (key != null && key.isModifier()) {
            if (sInGesture) {
                // Make sure not to interrupt an active gesture
                return;
            } else {
                // Before processing a down event of modifier key, all pointers
                // already being tracked should be released.
                sPointerTrackerQueue.releaseAllPointers(eventTime);
            }
        }
        sPointerTrackerQueue.add(this);
        onDownEventInternal(x, y, eventTime);
        if (!sGestureEnabler.shouldHandleGesture()) {
            return;
        }
        // A gesture should start only from a non-modifier key. Note that the gesture detection is
        // disabled when the key is repeating.
        mIsDetectingGesture = (mKeyboard != null) && mKeyboard.mId.isAlphabetKeyboard()
                && key != null && !key.isModifier();
        if (mIsDetectingGesture) {
            mBatchInputArbiter.addDownEventPoint(x, y, eventTime,
                    sTypingTimeRecorder.getLastLetterTypingTime(), getActivePointerTrackerCount());
            mGestureStrokeDrawingPoints.onDownEvent(
                    x, y, mBatchInputArbiter.getElapsedTimeSinceFirstDown(eventTime));
        }
    }

    /* package */ boolean isShowingMoreKeysPanel() {
        return (mMoreKeysPanel != null);
    }

    private void dismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.dismissMoreKeysPanel();
            mMoreKeysPanel = null;
        }
    }

    private void onDownEventInternal(final int x, final int y, final long eventTime) {
        Key key = onDownKey(x, y, eventTime);
        // Key selection by dragging finger is allowed when 1) key selection by dragging finger is
        // enabled by configuration, 2) this pointer starts dragging from modifier key, or 3) this
        // pointer's KeyDetector always allows key selection by dragging finger, such as
        // {@link MoreKeysKeyboard}.
        mIsAllowedDraggingFinger = sParams.mKeySelectionByDraggingFinger
                || (key != null && key.isModifier())
                || mKeyDetector.alwaysAllowsKeySelectionByDraggingFinger();
        mKeyboardLayoutHasBeenChanged = false;
        mIsTrackingForActionDisabled = false;
        resetKeySelectionByDraggingFinger();
        if (key != null) {
            // This onPress call may have changed keyboard layout. Those cases are detected at
            // {@link #setKeyboard}. In those cases, we should update key according to the new
            // keyboard layout.
            // Also height difference between keyboards needs to be considered.
            if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0 /* repeatCount */)) {
                final int yOffset = keyboardChangeOccupiedHeightDifference;
                keyboardChangeOccupiedHeightDifference = 0;
                CoordinateUtils.set(mDownCoordinates, x, y + yOffset);
                key = onDownKey(x, y + yOffset, eventTime);
            }

            startRepeatKey(key);
            startLongPressTimer(key);
            setPressedKeyGraphics(key, eventTime);
            mStartX = x;
            mStartY = y;
            mPreviousY = y;
            mStartTime = System.currentTimeMillis();
        }
    }

    private void startKeySelectionByDraggingFinger(final Key key) {
        if (!mIsInDraggingFinger) {
            mIsInSlidingKeyInput = key.isModifier();
        }
        mIsInDraggingFinger = true;
    }

    private void resetKeySelectionByDraggingFinger() {
        mIsInDraggingFinger = false;
        mIsInSlidingKeyInput = false;
        sDrawingProxy.showSlidingKeyInputPreview(null /* tracker */);
    }

    private void onGestureMoveEvent(final int x, final int y, final long eventTime,
            final boolean isMajorEvent, final Key key) {
        if (!mIsDetectingGesture) {
            return;
        }
        final boolean onValidArea = mBatchInputArbiter.addMoveEventPoint(
                x, y, eventTime, isMajorEvent, this);
        // If the move event goes out from valid batch input area, cancel batch input.
        if (!onValidArea) {
            cancelBatchInput();
            return;
        }
        mGestureStrokeDrawingPoints.onMoveEvent(
                x, y, mBatchInputArbiter.getElapsedTimeSinceFirstDown(eventTime));
        // If the MoreKeysPanel is showing then do not attempt to enter gesture mode. However,
        // the gestured touch points are still being recorded in case the panel is dismissed.
        if (isShowingMoreKeysPanel()) {
            return;
        }
        if (!sInGesture && key != null && Character.isLetter(key.getCode())
                && mBatchInputArbiter.mayStartBatchInput(this)) {
            sInGesture = true;
        }
        if (sInGesture) {
            if (key != null) {
                mBatchInputArbiter.updateBatchInput(eventTime, this);
            }
            showGestureTrail();
        }
    }

    private void onMoveEvent(final int x, final int y, final long eventTime, final MotionEvent me) {
        if (DEBUG_MOVE_EVENT) {
            printTouchEvent("onMoveEvent:", x, y, eventTime);
        }
        if (mIsTrackingForActionDisabled) {
            return;
        }

        if (sGestureEnabler.shouldHandleGesture() && me != null) {
            // Add historical points to gesture path.
            final int pointerIndex = me.findPointerIndex(mPointerId);
            final int historicalSize = me.getHistorySize();
            for (int h = 0; h < historicalSize; h++) {
                final int historicalX = (int)me.getHistoricalX(pointerIndex, h);
                final int historicalY = (int)me.getHistoricalY(pointerIndex, h);
                final long historicalTime = me.getHistoricalEventTime(h);
                onGestureMoveEvent(historicalX, historicalY, historicalTime,
                        false /* isMajorEvent */, null);
            }
        }

        if (isShowingMoreKeysPanel()) {
            final int translatedX = mMoreKeysPanel.translateX(x);
            final int translatedY = mMoreKeysPanel.translateY(y);
            mMoreKeysPanel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime);
            onMoveKey(x, y);
            if (mIsInSlidingKeyInput) {
                sDrawingProxy.showSlidingKeyInputPreview(this);
            }
            return;
        }
        onMoveEventInternal(x, y, eventTime);
    }

    private void processDraggingFingerInToNewKey(final Key newKey, final int x, final int y,
            final long eventTime) {
        // This onPress call may have changed keyboard layout. Those cases are detected
        // at {@link #setKeyboard}. In those cases, we should update key according
        // to the new keyboard layout.
        Key key = newKey;
        if (callListenerOnPressAndCheckKeyboardLayoutChange(key, 0 /* repeatCount */)) {
            key = onMoveKey(x, y);
        }
        onMoveToNewKey(key, x, y);
        if (mIsTrackingForActionDisabled) {
            return;
        }
        startLongPressTimer(key);
        setPressedKeyGraphics(key, eventTime);
    }

    private void processPhantomSuddenMoveHack(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        if (DEBUG_MODE) {
            Log.w(TAG, String.format("[%d] onMoveEvent:"
                    + " phantom sudden move event (distance=%d) is translated to "
                    + "up[%d,%d,%s]/down[%d,%d,%s] events", mPointerId,
                    getDistance(x, y, lastX, lastY),
                    lastX, lastY, Constants.printableCode(oldKey.getCode()),
                    x, y, Constants.printableCode(key.getCode())));
        }
        onUpEventInternal(x, y, eventTime);
        onDownEventInternal(x, y, eventTime);
    }

    private void processProximateBogusDownMoveUpEventHack(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        if (DEBUG_MODE) {
            final float keyDiagonal = (float)Math.hypot(
                    mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
            final float radiusRatio =
                    mBogusMoveEventDetector.getDistanceFromDownEvent(x, y)
                    / keyDiagonal;
            Log.w(TAG, String.format("[%d] onMoveEvent:"
                    + " bogus down-move-up event (raidus=%.2f key diagonal) is "
                    + " translated to up[%d,%d,%s]/down[%d,%d,%s] events",
                    mPointerId, radiusRatio,
                    lastX, lastY, Constants.printableCode(oldKey.getCode()),
                    x, y, Constants.printableCode(key.getCode())));
        }
        onUpEventInternal(x, y, eventTime);
        onDownEventInternal(x, y, eventTime);
    }

    private void processDraggingFingerOutFromOldKey(final Key oldKey) {
        setReleasedKeyGraphics(oldKey, true /* withAnimation */);
        callListenerOnRelease(oldKey, oldKey.getCode(), true /* withSliding */);
        startKeySelectionByDraggingFinger(oldKey);
        sTimerProxy.cancelKeyTimersOf(this);
    }

    private void dragFingerFromOldKeyToNewKey(final Key key, final int x, final int y,
            final long eventTime, final Key oldKey, final int lastX, final int lastY) {
        // The pointer has been slid in to the new key from the previous key, we must call
        // onRelease() first to notify that the previous key has been released, then call
        // onPress() to notify that the new key is being pressed.
        processDraggingFingerOutFromOldKey(oldKey);
        startRepeatKey(key);
        if (mIsAllowedDraggingFinger) {
            processDraggingFingerInToNewKey(key, x, y, eventTime);
        }
        // HACK: On some devices, quick successive touches may be reported as a sudden move by
        // touch panel firmware. This hack detects such cases and translates the move event to
        // successive up and down events.
        // TODO: Should find a way to balance gesture detection and this hack.
        else if (sNeedsPhantomSuddenMoveEventHack
                && getDistance(x, y, lastX, lastY) >= mPhantomSuddenMoveThreshold) {
            processPhantomSuddenMoveHack(key, x, y, eventTime, oldKey, lastX, lastY);
        }
        // HACK: On some devices, quick successive proximate touches may be reported as a bogus
        // down-move-up event by touch panel firmware. This hack detects such cases and breaks
        // these events into separate up and down events.
        else if (sTypingTimeRecorder.isInFastTyping(eventTime)
                && mBogusMoveEventDetector.isCloseToActualDownEvent(x, y)) {
            processProximateBogusDownMoveUpEventHack(key, x, y, eventTime, oldKey, lastX, lastY);
        }
        // HACK: If there are currently multiple touches, register the key even if the finger
        // slides off the key. This defends against noise from some touch panels when there are
        // close multiple touches.
        // Caveat: When in chording input mode with a modifier key, we don't use this hack.
        else if (getActivePointerTrackerCount() > 1
                && !sPointerTrackerQueue.hasModifierKeyOlderThan(this)) {
            if (DEBUG_MODE) {
                Log.w(TAG, String.format("[%d] onMoveEvent:"
                        + " detected sliding finger while multi touching", mPointerId));
            }
            onUpEvent(x, y, eventTime);
            cancelTrackingForAction();
            setReleasedKeyGraphics(oldKey, true /* withAnimation */);
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction();
            }
            setReleasedKeyGraphics(oldKey, true /* withAnimation */);
        }
    }

    private void dragFingerOutFromOldKey(final Key oldKey, final int x, final int y) {
        // The pointer has been slid out from the previous key, we must call onRelease() to
        // notify that the previous key has been released.
        processDraggingFingerOutFromOldKey(oldKey);
        if (mIsAllowedDraggingFinger) {
            onMoveToNewKey(null, x, y);
        } else {
            if (!mIsDetectingGesture) {
                cancelTrackingForAction();
            }
        }
    }

    private void onMoveEventInternal(final int x, final int y, final long eventTime) {
        final Key oldKey = mCurrentKey;
        final SettingsValues sv = Settings.getInstance().getCurrent();

        if (oldKey != null && oldKey.getCode() == Constants.CODE_SPACE) {
            int dX = x - mStartX;
            int dY = y - mStartY;
            // language switch: upwards movement
            if (!mCursorMoved && sv.mSpaceLanguageSlide && -dY > abs(dX) && dY / sPointerStep != 0) {
                List<InputMethodSubtype> subtypes = RichInputMethodManager.getInstance().getMyEnabledInputMethodSubtypeList(false);
                if (subtypes.size() > 1) { // only allow if we have more than one subtype
                    mLanguageSlideStarted = true;
                    if (abs(y - mPreviousY) / sPointerStep < 4)
                        // we want large enough steps between switches
                        return;

                    // decide next or previous dependent on up or down
                    InputMethodSubtype current = RichInputMethodManager.getInstance().getCurrentSubtype().getRawSubtype();
                    int wantedIndex = (subtypes.indexOf(current) + ((y - mPreviousY > 0) ? 1 : -1)) % subtypes.size();
                    if (wantedIndex < 0) wantedIndex += subtypes.size();
                    KeyboardSwitcher.getInstance().switchToSubtype(subtypes.get(wantedIndex));
                    mPreviousY = y;
                    return;
                }
            }
            // Pointer slider: sideways movement
            int steps = dX / sPointerStep;
            final int longpressTimeout = 2 * sv.mKeyLongpressTimeout / MULTIPLIER_FOR_LONG_PRESS_TIMEOUT_IN_SLIDING_INPUT;
            if (sv.mSpaceTrackpadEnabled && !mLanguageSlideStarted && steps != 0 && mStartTime + longpressTimeout < System.currentTimeMillis()) {
                mCursorMoved = true;
                mStartX += steps * sPointerStep;
                sListener.onMovePointer(steps);
            }
            return;
        }

        if (oldKey != null && oldKey.getCode() == Constants.CODE_DELETE && sv.mDeleteSwipeEnabled) {
            // Delete slider
            int steps = (x - mStartX) / sPointerStep;
            if (abs(steps) > 2 || (mCursorMoved && steps != 0)) {
                sTimerProxy.cancelKeyTimersOf(this);
                mCursorMoved = true;
                mStartX += steps * sPointerStep;
                sListener.onMoveDeletePointer(steps);
            }
            return;
        }

        final Key newKey = onMoveKey(x, y);
        final int lastX = mLastX;
        final int lastY = mLastY;

        if (sGestureEnabler.shouldHandleGesture()) {
            // Register move event on gesture tracker.
            onGestureMoveEvent(x, y, eventTime, true /* isMajorEvent */, newKey);
            if (sInGesture) {
                mCurrentKey = null;
                setReleasedKeyGraphics(oldKey, true /* withAnimation */);
                return;
            }
        }

        if (newKey != null) {
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerFromOldKeyToNewKey(newKey, x, y, eventTime, oldKey, lastX, lastY);
            } else if (oldKey == null) {
                // The pointer has been slid in to the new key, but the finger was not on any keys.
                // In this case, we must call onPress() to notify that the new key is being pressed.
                processDraggingFingerInToNewKey(newKey, x, y, eventTime);
            }
        } else { // newKey == null
            if (oldKey != null && isMajorEnoughMoveToBeOnNewKey(x, y, eventTime, newKey)) {
                dragFingerOutFromOldKey(oldKey, x, y);
            }
        }
        if (mIsInSlidingKeyInput) {
            sDrawingProxy.showSlidingKeyInputPreview(this);
        }
    }

    private void onUpEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onUpEvent  :", x, y, eventTime);
        }

        sTimerProxy.cancelUpdateBatchInputTimer(this);
        if (!sInGesture) {
            if (mCurrentKey != null && mCurrentKey.isModifier()) {
                // Before processing an up event of modifier key, all pointers already being
                // tracked should be released.
                sPointerTrackerQueue.releaseAllPointersExcept(this, eventTime);
            } else {
                sPointerTrackerQueue.releaseAllPointersOlderThan(this, eventTime);
            }
        }
        onUpEventInternal(x, y, eventTime);
        sPointerTrackerQueue.remove(this);
    }

    // Let this pointer tracker know that one of newer-than-this pointer trackers got an up event.
    // This pointer tracker needs to keep the key top graphics "pressed", but needs to get a
    // "virtual" up event.
    @Override
    public void onPhantomUpEvent(final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onPhntEvent:", mLastX, mLastY, eventTime);
        }
        onUpEventInternal(mLastX, mLastY, eventTime);
        cancelTrackingForAction();
    }

    private void onUpEventInternal(final int x, final int y, final long eventTime) {
        sTimerProxy.cancelKeyTimersOf(this);
        final boolean isInDraggingFinger = mIsInDraggingFinger;
        final boolean isInSlidingKeyInput = mIsInSlidingKeyInput;
        resetKeySelectionByDraggingFinger();
        mIsDetectingGesture = false;
        final Key currentKey = mCurrentKey;
        mCurrentKey = null;
        final int currentRepeatingKeyCode = mCurrentRepeatingKeyCode;
        mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;
        // Release the last pressed key.
        setReleasedKeyGraphics(currentKey, true /* withAnimation */);

        if(mCursorMoved && currentKey.getCode() == Constants.CODE_DELETE) {
            sListener.onUpWithDeletePointerActive();
        }

        if (isShowingMoreKeysPanel()) {
            if (!mIsTrackingForActionDisabled) {
                final int translatedX = mMoreKeysPanel.translateX(x);
                final int translatedY = mMoreKeysPanel.translateY(y);
                mMoreKeysPanel.onUpEvent(translatedX, translatedY, mPointerId, eventTime);
            }
            dismissMoreKeysPanel();
            return;
        }

        if (mCursorMoved || mLanguageSlideStarted) {
            mCursorMoved = false;
            mLanguageSlideStarted = false;
            return;
        }

        if (sInGesture) {
            if (currentKey != null) {
                callListenerOnRelease(currentKey, currentKey.getCode(), true /* withSliding */);
            }
            if (mBatchInputArbiter.mayEndBatchInput(
                    eventTime, getActivePointerTrackerCount(), this)) {
                sInGesture = false;
            }
            showGestureTrail();
            return;
        }

        if (mIsTrackingForActionDisabled) {
            return;
        }
        if (currentKey != null && currentKey.isRepeatable()
                && (currentKey.getCode() == currentRepeatingKeyCode) && !isInDraggingFinger) {
            return;
        }
        detectAndSendKey(currentKey, mKeyX, mKeyY, eventTime);
        if (isInSlidingKeyInput) {
            callListenerOnFinishSlidingInput();
        }
    }

    @Override
    public void cancelTrackingForAction() {
        if (isShowingMoreKeysPanel()) {
            return;
        }
        mIsTrackingForActionDisabled = true;
    }

    public boolean isInOperation() {
        return !mIsTrackingForActionDisabled;
    }

    public void onLongPressed() {
        sTimerProxy.cancelLongPressTimersOf(this);
        if (isShowingMoreKeysPanel()) {
            return;
        }
        if(mCursorMoved) {
            return;
        }
        final Key key = getKey();
        if (key == null) {
            return;
        }
        if (key.hasNoPanelAutoMoreKey()) {
            cancelKeyTracking();
            final int moreKeyCode = key.getMoreKeys()[0].mCode;
            sListener.onPressKey(moreKeyCode, 0 /* repeatCont */, true /* isSinglePointer */);
            sListener.onCodeInput(moreKeyCode, Constants.NOT_A_COORDINATE,
                    Constants.NOT_A_COORDINATE, false /* isKeyRepeat */);
            sListener.onReleaseKey(moreKeyCode, false /* withSliding */);
            return;
        }
        final int code = key.getCode();
        if (code == Constants.CODE_SPACE&&Settings.getInstance().getCurrent().mSpaceForLangChange || code == Constants.CODE_LANGUAGE_SWITCH) {
            // Long pressing the space key invokes IME switcher dialog.
            if (sListener.onCustomRequest(Constants.CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER)) {
                cancelKeyTracking();
                sListener.onReleaseKey(code, false /* withSliding */);
                return;
            }
        }

        setReleasedKeyGraphics(key, false /* withAnimation */);
        final MoreKeysPanel moreKeysPanel = sDrawingProxy.showMoreKeysKeyboard(key, this);
        if (moreKeysPanel == null) {
            return;
        }
        final int translatedX = moreKeysPanel.translateX(mLastX);
        final int translatedY = moreKeysPanel.translateY(mLastY);
        moreKeysPanel.onDownEvent(translatedX, translatedY, mPointerId, SystemClock.uptimeMillis());
        mMoreKeysPanel = moreKeysPanel;
    }

    private void cancelKeyTracking() {
        resetKeySelectionByDraggingFinger();
        cancelTrackingForAction();
        setReleasedKeyGraphics(mCurrentKey, true /* withAnimation */);
        sPointerTrackerQueue.remove(this);
    }

    private void onCancelEvent(final int x, final int y, final long eventTime) {
        if (DEBUG_EVENT) {
            printTouchEvent("onCancelEvt:", x, y, eventTime);
        }

        cancelBatchInput();
        cancelAllPointerTrackers();
        sPointerTrackerQueue.releaseAllPointers(eventTime);
        onCancelEventInternal();
    }

    private void onCancelEventInternal() {
        sTimerProxy.cancelKeyTimersOf(this);
        setReleasedKeyGraphics(mCurrentKey, true /* withAnimation */);
        resetKeySelectionByDraggingFinger();
        dismissMoreKeysPanel();
    }

    private boolean isMajorEnoughMoveToBeOnNewKey(final int x, final int y, final long eventTime,
            final Key newKey) {
        final Key curKey = mCurrentKey;
        if (newKey == curKey) {
            return false;
        }
        if (curKey == null /* && newKey != null */) {
            return true;
        }
        // Here curKey points to the different key from newKey.
        final int keyHysteresisDistanceSquared = mKeyDetector.getKeyHysteresisDistanceSquared(
                mIsInSlidingKeyInput);
        final int distanceFromKeyEdgeSquared = curKey.squaredDistanceToEdge(x, y);
        if (distanceFromKeyEdgeSquared >= keyHysteresisDistanceSquared) {
            if (DEBUG_MODE) {
                final float distanceToEdgeRatio = (float)Math.sqrt(distanceFromKeyEdgeSquared)
                        / mKeyboard.mMostCommonKeyWidth;
                Log.d(TAG, String.format("[%d] isMajorEnoughMoveToBeOnNewKey:"
                        +" %.2f key width from key edge", mPointerId, distanceToEdgeRatio));
            }
            return true;
        }
        if (!mIsAllowedDraggingFinger && sTypingTimeRecorder.isInFastTyping(eventTime)
                && mBogusMoveEventDetector.hasTraveledLongDistance(x, y)) {
            if (DEBUG_MODE) {
                final float keyDiagonal = (float)Math.hypot(
                        mKeyboard.mMostCommonKeyWidth, mKeyboard.mMostCommonKeyHeight);
                final float lengthFromDownRatio =
                        mBogusMoveEventDetector.getAccumulatedDistanceFromDownKey() / keyDiagonal;
                Log.d(TAG, String.format("[%d] isMajorEnoughMoveToBeOnNewKey:"
                        + " %.2f key diagonal from virtual down point",
                        mPointerId, lengthFromDownRatio));
            }
            return true;
        }
        return false;
    }

    private void startLongPressTimer(final Key key) {
        // Note that we need to cancel all active long press shift key timers if any whenever we
        // start a new long press timer for both non-shift and shift keys.
        sTimerProxy.cancelLongPressShiftKeyTimer();
        if (sInGesture) return;
        if (key == null) return;
        if (!key.isLongPressEnabled()) return;
        // Caveat: Please note that isLongPressEnabled() can be true even if the current key
        // doesn't have its more keys. (e.g. spacebar, globe key) If we are in the dragging finger
        // mode, we will disable long press timer of such key.
        // We always need to start the long press timer if the key has its more keys regardless of
        // whether or not we are in the dragging finger mode.
        if (mIsInDraggingFinger && key.getMoreKeys() == null) return;

        final int delay = getLongPressTimeout(key.getCode());
        if (delay <= 0) return;
        sTimerProxy.startLongPressTimerOf(this, delay);
    }

    private int getLongPressTimeout(final int code) {
        if (code == Constants.CODE_SHIFT) {
            return sParams.mLongPressShiftLockTimeout;
        }
        final int longpressTimeout = Settings.getInstance().getCurrent().mKeyLongpressTimeout;
        if (mIsInSlidingKeyInput) {
            // We use longer timeout for sliding finger input started from the modifier key.
            return longpressTimeout * MULTIPLIER_FOR_LONG_PRESS_TIMEOUT_IN_SLIDING_INPUT;
        }
        return longpressTimeout;
    }

    private void detectAndSendKey(final Key key, final int x, final int y, final long eventTime) {
        if (key == null) {
            callListenerOnCancelInput();
            return;
        }

        final int code = key.getCode();
        callListenerOnCodeInput(key, code, x, y, eventTime, false /* isKeyRepeat */);
        callListenerOnRelease(key, code, false /* withSliding */);
    }

    private void startRepeatKey(final Key key) {
        if (sInGesture) return;
        if (key == null) return;
        if (!key.isRepeatable()) return;
        // Don't start key repeat when we are in the dragging finger mode.
        if (mIsInDraggingFinger) return;
        final int startRepeatCount = 1;
        startKeyRepeatTimer(startRepeatCount);
    }

    public void onKeyRepeat(final int code, final int repeatCount) {
        final Key key = getKey();
        if (key == null || key.getCode() != code) {
            mCurrentRepeatingKeyCode = Constants.NOT_A_CODE;
            return;
        }
        mCurrentRepeatingKeyCode = code;
        mIsDetectingGesture = false;
        final int nextRepeatCount = repeatCount + 1;
        startKeyRepeatTimer(nextRepeatCount);
        callListenerOnPressAndCheckKeyboardLayoutChange(key, repeatCount);
        callListenerOnCodeInput(key, code, mKeyX, mKeyY, SystemClock.uptimeMillis(),
                true /* isKeyRepeat */);
    }

    private void startKeyRepeatTimer(final int repeatCount) {
        final int delay =
                (repeatCount == 1) ? sParams.mKeyRepeatStartTimeout : sParams.mKeyRepeatInterval;
        sTimerProxy.startKeyRepeatTimerOf(this, repeatCount, delay);
    }

    private void printTouchEvent(final String title, final int x, final int y,
            final long eventTime) {
        final Key key = mKeyDetector.detectHitKey(x, y);
        final String code = (key == null ? "none" : Constants.printableCode(key.getCode()));
        Log.d(TAG, String.format("[%d]%s%s %4d %4d %5d %s", mPointerId,
                (mIsTrackingForActionDisabled ? "-" : " "), title, x, y, eventTime, code));
    }
}
