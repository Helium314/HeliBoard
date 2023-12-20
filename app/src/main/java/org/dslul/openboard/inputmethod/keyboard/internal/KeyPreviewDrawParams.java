/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.res.TypedArray;
import android.view.View;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

public final class KeyPreviewDrawParams {
    // XML attributes of {@link MainKeyboardView}.
    public final int mPreviewOffset;
    public final int mPreviewHeight;
    public final int mPreviewBackgroundResId;
    private boolean mShowPopup = true;

    // The graphical geometry of the key preview.
    // <-width->
    // +-------+   ^
    // |       |   |
    // |preview| height (visible)
    // |       |   |
    // +       + ^ v
    //  \     /  |offset
    // +-\   /-+ v
    // |  +-+  |
    // |parent |
    // |    key|
    // +-------+
    // The background of a {@link TextView} being used for a key preview may have invisible
    // paddings. To align the more keys keyboard panel's visible part with the visible part of
    // the background, we need to record the width and height of key preview that don't include
    // invisible paddings.
    private int mVisibleWidth;
    private int mVisibleHeight;
    // The key preview may have an arbitrary offset and its background that may have a bottom
    // padding. To align the more keys keyboard and the key preview we also need to record the
    // offset between the top edge of parent key and the bottom of the visible part of key
    // preview background.
    private int mVisibleOffset;

    public KeyPreviewDrawParams(final TypedArray mainKeyboardViewAttr) {
        mPreviewOffset = mainKeyboardViewAttr.getDimensionPixelOffset(
                R.styleable.MainKeyboardView_keyPreviewOffset, 0);
        // crashes when too small (or just < 1?)
        final float heightScale = (float) Math.max(1f, Math.sqrt(Settings.getInstance().getCurrent().mKeyboardHeightScale));
        // todo: further scaling issue
        //  key height and thus text height (in pixels) don't change with display density,
        //  but keyPreviewHeight does -> how to do it right?
        mPreviewHeight = (int) (mainKeyboardViewAttr.getDimensionPixelSize(
                R.styleable.MainKeyboardView_keyPreviewHeight, 0) * heightScale);
        mPreviewBackgroundResId = mainKeyboardViewAttr.getResourceId(
                R.styleable.MainKeyboardView_keyPreviewBackground, 0);
    }

    public void setVisibleOffset(final int previewVisibleOffset) {
        mVisibleOffset = previewVisibleOffset;
    }

    public int getVisibleOffset() {
        return mVisibleOffset;
    }

    public void setGeometry(final View previewTextView) {
        final int previewWidth = previewTextView.getMeasuredWidth();
        // The width and height of visible part of the key preview background. The content marker
        // of the background 9-patch have to cover the visible part of the background.
        mVisibleWidth = previewWidth - previewTextView.getPaddingLeft() - previewTextView.getPaddingRight();
        mVisibleHeight = mPreviewHeight - previewTextView.getPaddingTop() - previewTextView.getPaddingBottom();
        // The distance between the top edge of the parent key and the bottom of the visible part
        // of the key preview background.
        setVisibleOffset(-previewTextView.getPaddingBottom() / 2);
    }

    public int getVisibleWidth() {
        return mVisibleWidth;
    }

    public int getVisibleHeight() {
        return mVisibleHeight;
    }

    public void setPopupEnabled(final boolean enabled) {
        mShowPopup = enabled;
    }

    public boolean isPopupEnabled() {
        return mShowPopup;
    }

}
