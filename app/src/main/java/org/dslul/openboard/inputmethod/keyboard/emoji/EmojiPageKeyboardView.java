/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.emoji;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.os.Handler;
import android.util.AttributeSet;
import org.dslul.openboard.inputmethod.latin.utils.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;

import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.accessibility.AccessibilityUtils;
import org.dslul.openboard.inputmethod.accessibility.KeyboardAccessibilityDelegate;
import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.KeyDetector;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysKeyboard;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.keyboard.internal.MoreKeySpec;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.CoordinateUtils;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

import java.util.WeakHashMap;

/**
 * This is an extended {@link KeyboardView} class that hosts an emoji page keyboard.
 * Multi-touch unsupported. No gesture support.
 */
// TODO: Implement key popup preview.
public final class EmojiPageKeyboardView extends KeyboardView implements
        MoreKeysPanel.Controller {
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
    private final View mMoreKeysKeyboardContainer;
    private final WeakHashMap<Key, Keyboard> mMoreKeysKeyboardCache = new WeakHashMap<>();
    private final boolean mConfigShowMoreKeysKeyboardAtTouchedPoint;
    private final ViewGroup mMoreKeysPlacerView;
    // More keys panel (used by more keys keyboard view)
    // TODO: Consider extending to support multiple more keys panels
    private MoreKeysPanel mMoreKeysPanel;

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.keyboardViewStyle);
    }

    public EmojiPageKeyboardView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        mHandler = new Handler();

        mMoreKeysPlacerView = new FrameLayout(context, attrs);

        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.MainKeyboardView, defStyle, R.style.MainKeyboardView);
        final int moreKeysKeyboardLayoutId = keyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_moreKeysKeyboardLayout, 0);
        mConfigShowMoreKeysKeyboardAtTouchedPoint = keyboardViewAttr.getBoolean(
                R.styleable.MainKeyboardView_showMoreKeysKeyboardAtTouchedPoint, false);
        keyboardViewAttr.recycle();

        final LayoutInflater inflater = LayoutInflater.from(getContext());
        mMoreKeysKeyboardContainer = inflater.inflate(moreKeysKeyboardLayoutId, null);
    }

    // todo: maybe can be removed?
    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        final Keyboard keyboard = getKeyboard();
        if (keyboard instanceof DynamicGridKeyboard) {
            final int width = keyboard.mOccupiedWidth + getPaddingLeft() + getPaddingRight();
            final int occupiedHeight =
                    ((DynamicGridKeyboard) keyboard).getDynamicOccupiedHeight();
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
        mMoreKeysPlacerView.setLayerType(LAYER_TYPE_HARDWARE, layerPaint);
    }

    private void installMoreKeysPlacerView(final boolean uninstall) {
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
            windowContentView.removeView(mMoreKeysPlacerView);
        } else {
            windowContentView.addView(mMoreKeysPlacerView);
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
        mMoreKeysKeyboardCache.clear();
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
    public MoreKeysPanel showMoreKeysKeyboard(@NonNull final Key key, final int lastX, final int lastY) {
        final MoreKeySpec[] moreKeys = key.getMoreKeys();
        if (moreKeys == null) {
            return null;
        }
        Keyboard moreKeysKeyboard = mMoreKeysKeyboardCache.get(key);
        if (moreKeysKeyboard == null) {
            final MoreKeysKeyboard.Builder builder = new MoreKeysKeyboard.Builder(
                    getContext(), key, getKeyboard(), false, 0, 0, newLabelPaint(key));
            moreKeysKeyboard = builder.build();
            mMoreKeysKeyboardCache.put(key, moreKeysKeyboard);
        }

        final View container = mMoreKeysKeyboardContainer;
        final MoreKeysKeyboardView moreKeysKeyboardView =
                container.findViewById(R.id.more_keys_keyboard_view);
        moreKeysKeyboardView.setKeyboard(moreKeysKeyboard);
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final int[] lastCoords = CoordinateUtils.newCoordinateArray(1, lastX, lastY);
        // The more keys keyboard is usually horizontally aligned with the center of the parent key.
        // If showMoreKeysKeyboardAtTouchedPoint is true and the key preview is disabled, the more
        // keys keyboard is placed at the touch point of the parent key.
        final int pointX = mConfigShowMoreKeysKeyboardAtTouchedPoint
                ? CoordinateUtils.x(lastCoords)
                : key.getX() + key.getWidth() / 2;
        final int pointY = key.getY();
        moreKeysKeyboardView.showMoreKeysPanel(this, this,
                pointX, pointY, mListener);
        return moreKeysKeyboardView;
    }

    private void dismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.dismissMoreKeysPanel();
        }
    }

    public boolean isShowingMoreKeysPanel() {
        return mMoreKeysPanel != null;
    }

    @Override
    public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
        // install placer view only when needed instead of when this
        // view is attached to window
        installMoreKeysPlacerView(false /* uninstall */);
        panel.showInParent(mMoreKeysPlacerView);
        mMoreKeysPanel = panel;
    }

    @Override
    public void onDismissMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            mMoreKeysPanel.removeFromParent();
            mMoreKeysPanel = null;
            installMoreKeysPlacerView(true /* uninstall */);
        }
    }

    @Override
    public void onCancelMoreKeysPanel() {
        if (isShowingMoreKeysPanel()) {
            dismissMoreKeysPanel();
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
        final KeyboardAccessibilityDelegate<EmojiPageKeyboardView> accessibilityDelegate =
                mAccessibilityDelegate;
        if (accessibilityDelegate != null
                && AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled()) {
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
        if (isShowingMoreKeysPanel()) {
            return;
        }

        if (key == null) {
            if (LOG) Log.d(TAG, "Long press ignored because detected key is null");
            return;
        }

        final int x = mLastX;
        final int y = mLastY;
        final MoreKeysPanel moreKeysPanel = showMoreKeysKeyboard(key, x, y);
        if (moreKeysPanel != null) {
            final int translatedX = moreKeysPanel.translateX(x);
            final int translatedY = moreKeysPanel.translateY(y);
            moreKeysPanel.onDownEvent(translatedX, translatedY, mPointerId, 0 /* nor used for now */);
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

        final boolean isShowingMoreKeysPanel = isShowingMoreKeysPanel();
        if (isShowingMoreKeysPanel) {
            final long eventTime = e.getEventTime();
            final int translatedX = mMoreKeysPanel.translateX(x);
            final int translatedY = mMoreKeysPanel.translateY(y);
            mMoreKeysPanel.onUpEvent(translatedX, translatedY, mPointerId, eventTime);
            dismissMoreKeysPanel();
        } else if (key == currentKey && pendingKeyDown != null) {
            pendingKeyDown.run();
            // Trigger key-release event a little later so that a user can see visual feedback.
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    callListenerOnReleaseKey(key, true /* withRegistering */);
                }
            }, KEY_RELEASE_DELAY_TIME);
        } else if (key != null) {
            callListenerOnReleaseKey(key, true /* withRegistering */);
        }

        cancelLongPress();
        return true;
    }

    public boolean onCancel(final MotionEvent e) {
        releaseCurrentKey(false /* withKeyRegistering */);
        dismissMoreKeysPanel();
        cancelLongPress();
        return true;
    }

    public boolean onMove(final MotionEvent e) {
        final int x = (int)e.getX();
        final int y = (int)e.getY();
        final Key key = getKey(x, y);
        final boolean isShowingMoreKeysPanel = isShowingMoreKeysPanel();

        // Touched key has changed, release previous key's callbacks and
        // re-register them for the new key.
        if (key != mCurrentKey && !isShowingMoreKeysPanel) {
            releaseCurrentKey(false /* withKeyRegistering */);
            mCurrentKey = key;
            if (key == null) {
                return false;
            }
            registerPress(key);

            cancelLongPress();
            registerLongPress(key);
        }

        if (isShowingMoreKeysPanel) {
            final long eventTime = e.getEventTime();
            final int translatedX = mMoreKeysPanel.translateX(x);
            final int translatedY = mMoreKeysPanel.translateY(y);
            mMoreKeysPanel.onMoveEvent(translatedX, translatedY, mPointerId, eventTime);
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
