/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Handler;
import android.util.AttributeSet;
import com.oscar.aikeyboard.latin.utils.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.oscar.aikeyboard.accessibility.AccessibilityUtils;
import com.oscar.aikeyboard.accessibility.KeyboardAccessibilityDelegate;
import com.oscar.aikeyboard.keyboard.Key;
import com.oscar.aikeyboard.keyboard.KeyDetector;
import com.oscar.aikeyboard.keyboard.Keyboard;
import com.oscar.aikeyboard.keyboard.KeyboardView;
import com.oscar.aikeyboard.keyboard.PopupKeysKeyboard;
import com.oscar.aikeyboard.keyboard.PopupKeysKeyboardView;
import com.oscar.aikeyboard.keyboard.PopupKeysPanel;
import com.oscar.aikeyboard.keyboard.internal.PopupKeySpec;
import com.oscar.aikeyboard.R;
import com.oscar.aikeyboard.latin.common.CoordinateUtils;
import com.oscar.aikeyboard.latin.settings.Settings;

import java.util.WeakHashMap;

/**
 * This is an extended {@link KeyboardView} class that hosts an emoji page keyboard.
 * Multi-touch unsupported. No gesture support.
 */
public final class EmojiPageKeyboardView extends KeyboardView implements
        PopupKeysPanel.Controller {
    private static final String TAG = "EmojiPageKeyboardView";
    private static final boolean LOG = false;
    private static final long KEY_PRESS_DELAY_TIME = 250;  // msec
    private static final long KEY_RELEASE_DELAY_TIME = 30;  // msec

    private static final OnKeyEventListener EMPTY_LISTENER = new OnKeyEventListener() {
        @Override
        public void onPressKey(final Key key) {}
        @Override
        public void onReleaseKey(final Key key) {}
    };

    private OnKeyEventListener mListener = EMPTY_LISTENER;
    private final KeyDetector mKeyDetector = new KeyDetector();
    private KeyboardAccessibilityDelegate<EmojiPageKeyboardView> mAccessibilityDelegate;

    // Touch inputs
    private int mPointerId = MotionEvent.INVALID_POINTER_ID;
    private int mLastX, mLastY;
    private Key mCurrentKey;
    private Runnable mPendingKeyDown;
    private Runnable mPendingLongPress;
    private final Handler mHandler;

    // More keys keyboard
    private final View mPopupKeysKeyboardContainer;
    private final WeakHashMap<Key, Keyboard> mPopupKeysKeyboardCache = new WeakHashMap<>();
    private final boolean mConfigShowPopupKeysKeyboardAtTouchedPoint;
    private final ViewGroup mPopupKeysPlacerView;
    // More keys panel (used by popup keys keyboard view)
    // TODO: Consider extending to support multiple popup keys panels
    private PopupKeysPanel mPopupKeysPanel;

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();

        mPopupKeysPlacerView = new FrameLayout(context, attrs);

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int popupKeysKeyboardLayoutId = keyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_popupKeysKeyboardLayout, 0);
        mConfigShowPopupKeysKeyboardAtTouchedPoint = keyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showPopupKeysKeyboardAtTouchedPoint, false);
        keyboardViewAttr.recycle();

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        mPopupKeysKeyboardContainer = inflater.inflate(popupKeysKeyboardLayoutId, null);
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard instanceof DynamicGridKeyboard) {
            final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
            final int occupiedHeight = ((DynamicGridKeyboard) keyboard).getDynamicOccupiedHeight();
            final int height = occupiedHeight + getPaddingTop() + getPaddingBottom();
            setMeasuredDimension(width, height);
            return;
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        super.setHardwareAcceleratedDrawingEnabled(enabled);
        if (!enabled) return;
        final Paint layerPaint = new Paint();
        layerPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OVER));
        mPopupKeysPlacerView.setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    private void installPopupKeysPlacerView(final boolean uninstall) {
        final View rootView = getRootView();
        if (rootView == null) {
            Log.w(TAG, "Cannot find root view");
            return;
        }
        final ViewGroup windowContentView = rootView.findViewById(android.R.id.content);
        // Note: It'd be very weird if we get null by android.R.id.content.
        if (windowContentView == null) {
            Log.w(TAG, "Cannot find android.R.id.content view to add DrawingPreviewPlacerView");
            return;
        }

        if (uninstall) {
            windowContentView.removeView(mPopupKeysPlacerView);
        } else {
            windowContentView.addView(mPopupKeysPlacerView);
        }
    }

    public void setOnKeyEventListener(final OnKeyEventListener listener) {
        mListener = listener;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setKeyboard(@NonNull final Keyboard keyboard) {
        super.setKeyboard(keyboard);
        mKeyDetector.setKeyboard(keyboard, 0 /* correctionX */, 0 /* correctionY */);
        mPopupKeysKeyboardCache.clear();
        if (AccessibilityUtils.Companion.getInstance().isAccessibilityEnabled()) {
            if (mAccessibilityDelegate == null) {
                mAccessibilityDelegate = new KeyboardAccessibilityDelegate<>(this, mKeyDetector);
            }
            mAccessibilityDelegate.setKeyboard(keyboard);
        } else {
            mAccessibilityDelegate = null;
        }
    }

    @Nullable
    public PopupKeysPanel showPopupKeysKeyboard(@NonNull final Key key, final int lastX, final int lastY) {
        final PopupKeySpec[] popupKeys = key.getPopupKeys();
        if (popupKeys == null) {
            return null;
        }
        Keyboard popupKeysKeyboard = mPopupKeysKeyboardCache.get(key);
        if (popupKeysKeyboard == null) {
            final PopupKeysKeyboard.Builder builder = new PopupKeysKeyboard.Builder(
                    getContext(), key, getKeyboard(), false, 0, 0, newLabelPaint(key));
            popupKeysKeyboard = builder.build();
            mPopupKeysKeyboardCache.put(key, popupKeysKeyboard);
        }

        final View container = mPopupKeysKeyboardContainer;
        final PopupKeysKeyboardView popupKeysKeyboardView = container.findViewById(R.id.popup_keys_keyboard_view);
        popupKeysKeyboardView.setKeyboard(popupKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final int[] lastCoords = CoordinateUtils.newCoordinateArray(1, lastX, lastY);
        // The popup keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showPopupKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = mConfigShowPopupKeysKeyboardAtTouchedPoint
                ? CoordinateUtils.x(lastCoords)
                : key.getX() + key.getWidth() / 2;
        final int pointY = key.getY();
        popupKeysKeyboardView.showPopupKeysPanel(this, this, pointX, pointY, mListener);
        return popupKeysKeyboardView;
    }

    private void dismissPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            mPopupKeysPanel.dismissPopupKeysPanel();
        }
    }

    public boolean isShowingPopupKeysPanel() {
        return mPopupKeysPanel != null;
    }

    @Override
    public void onShowPopupKeysPanel(final PopupKeysPanel panel) {
        // install placer view only when needed instead of when this
        // view is attached to window
        installPopupKeysPlacerView(false /* uninstall */);
        panel.showInParent(mPopupKeysPlacerView);
        mPopupKeysPanel = panel;
    }

    @Override
    public void onDismissPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            mPopupKeysPanel.removeFromParent();
            mPopupKeysPanel = null;
            installPopupKeysPlacerView(true /* uninstall */);
        }
    }

    @Override
    public void onCancelPopupKeysPanel() {
        if (isShowingPopupKeysPanel()) {
            dismissPopupKeysPanel();
        }
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with all Emoji keys.
        return true;
    }

    private int getLongPressTimeout() {
        return Settings.getInstance().getCurrent().mKeyLongpressTimeout;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onHoverEvent(final MotionEvent event) {
        final KeyboardAccessibilityDelegate<EmojiPageKeyboardView> accessibilityDelegate = mAccessibilityDelegate;
        if (accessibilityDelegate != null && AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled()) {
            return accessibilityDelegate.onHoverEvent(event);
        }
        return super.onHoverEvent(event);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean onTouchEvent(final MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                mPointerId = e.getPointerId(0);
                return onDown(e);
            case MotionEvent.ACTION_UP:
                return onUp(e);
            case MotionEvent.ACTION_MOVE:
                return onMove(e);
            case MotionEvent.ACTION_CANCEL:
                return onCancel(e);
            default:
                return false;
        }
    }

    private Key getKey(final int x, final int y) {
        return mKeyDetector.detectHitKey(x, y);
    }

    private void onLongPressed(final Key key) {
        if (isShowingPopupKeysPanel()) {
            return;
        }

        if (key == null) {
            if (LOG) Log.d(TAG, "Long press ignored because detected key is null");
            return;
        }

        final int x = mLastX;
        final int y = mLastY;
        final PopupKeysPanel popupKeysPanel = showPopupKeysKeyboard(key, x, y);
        if (popupKeysPanel != null) {
            final int translatedX = popupKeysPanel.translateX(x);
            final int translatedY = popupKeysPanel.translateY(y);
            popupKeysPanel.onDownEvent(translatedX, translatedY, mPointerId, 0 /* nor used for now */);
            // No need of re-allowing parent later as we don't
            // want any scroll to append during this entire input.
            disallowParentInterceptTouchEvent(true);
        }
    }

    private void registerPress(final Key key) {
        // Do not trigger key-down effect right now in case this is actually a fling action.
        mPendingKeyDown = () -> callListenerOnPressKey(key);
        mHandler.postDelayed(mPendingKeyDown, KEY_PRESS_DELAY_TIME);
    }

    private void registerLongPress(final Key key) {
        mPendingLongPress = () -> onLongPressed(key);
        mHandler.postDelayed(mPendingLongPress, getLongPressTimeout());
    }

    void callListenerOnReleaseKey(final Key releasedKey, final boolean withKeyRegistering) {
        releasedKey.onReleased();
        invalidateKey(releasedKey);
        if (withKeyRegistering) {
            mListener.onReleaseKey(releasedKey);
        }
    }

    void callListenerOnPressKey(final Key pressedKey) {
        mPendingKeyDown = null;
        pressedKey.onPressed();
        invalidateKey(pressedKey);
        mListener.onPressKey(pressedKey);
    }

    public void releaseCurrentKey(final boolean withKeyRegistering) {
        mHandler.removeCallbacks(mPendingKeyDown);
        mPendingKeyDown = null;
        final Key currentKey = mCurrentKey;
        if (currentKey == null) {
            return;
        }
        callListenerOnReleaseKey(currentKey, withKeyRegistering);
        mCurrentKey = null;
    }

    public void cancelLongPress() {
        mHandler.removeCallbacks(mPendingLongPress);
        mPendingLongPress = null;
    }

    public boolean onDown(final MotionEvent e) {
        final int x = (int) e.getX();
        final int y = (int) e.getY();
        final Key key = getKey(x, y);
        releaseCurrentKey(false /* withKeyRegistering */);
        mCurrentKey = key;
        if (key == null) {
            return false;
        }
        registerPress(key);

        registerLongPress(key);

        mLastX = x;
        mLastY = y;
        return true;
    }

    public boolean onUp(final MotionEvent e) {
        final int x = (int) e.getX();
        final int y = (int) e.getY();
        final Key key = getKey(x, y);
        final Runnable pendingKeyDown = mPendingKeyDown;
        final Key currentKey = mCurrentKey;
        releaseCurrentKey(false /* withKeyRegistering */);

        final boolean isShowingPopupKeysPanel = isShowingPopupKeysPanel();
        if (isShowingPopupKeysPanel) {
            final long eventTime = e.getEventTime();
            final int translatedX = mPopupKeysPanel.translateX(x);
            final int translatedY = mPopupKeysPanel.translateY(y);
            mPopupKeysPanel.onUpEvent(translatedX, translatedY, mPointerId, eventTime);
            dismissPopupKeysPanel();
        } else if (key == currentKey && pendingKeyDown != null) {
            pendingKeyDown.run();
            // Trigger key-release event a little later so that a user can see visual feedback.
            mHandler.postDelayed(() -> callListenerOnReleaseKey(key, true), KEY_RELEASE_DELAY_TIME);
        } else if (key != null) {
            callListenerOnReleaseKey(key, true /* withRegistering */);
        }

        cancelLongPress();
        return true;
    }

    public boolean onCancel(final MotionEvent e) {
        releaseCurrentKey(false);
        dismissPopupKeysPanel();
        cancelLongPress();
        return true;
    }

    public boolean onMove(final MotionEvent e) {
        final int x = (int)e.getX();
        final int y = (int)e.getY();
        final Key key = getKey(x, y);
        final boolean isShowingPopupKeysPanel = isShowingPopupKeysPanel();

        // Touched key has changed, release previous key's callbacks and
        // re-register them for the new key.
        if (key != mCurrentKey && !isShowingPopupKeysPanel) {
            releaseCurrentKey(false);
            mCurrentKey = key;
            if (key == null) {
                return false;
            }
            registerPress(key);

            cancelLongPress();
            registerLongPress(key);
        }

        if (isShowingPopupKeysPanel) {
            final long eventTime = e.getEventTime();
            final int translatedX = mPopupKeysPanel.translateX(x);
            final int translatedY = mPopupKeysPanel.translateY(y);
            mPopupKeysPanel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime);
        }

        mLastX = x;
        mLastY = y;
        return true;
    }

    private void disallowParentInterceptTouchEvent(final boolean disallow) {
        final ViewParent parent = getParent();
        if (parent == null) {
            Log.w(TAG, "Cannot disallow touch event interception, no parent found.");
            return;
        }
        parent.requestDisallowInterceptTouchEvent(disallow);
    }
}
