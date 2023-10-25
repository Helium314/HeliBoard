/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.internal;

import android.content.Context;
import android.content.res.Resources;
import android.util.Log;

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardId;
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.XmlKeyboardParser;
import org.dslul.openboard.inputmethod.latin.R;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;

// TODO: Write unit tests for this class.
public class KeyboardBuilder<KP extends KeyboardParams> {
    private static final String BUILDER_TAG = "Keyboard.Builder";

    @NonNull
    protected final KP mParams;
    protected final Context mContext;
    protected final Resources mResources;

    private int mCurrentY = 0;
    // currently not used, but will be relevant when resizing a row or inserting a new key
    private float mCurrentX = 0f;
    private boolean mLeftEdge;
    private boolean mTopEdge;
    private Key mRightEdgeKey = null;
    private ArrayList<ArrayList<Key.KeyParams>> keysInRows;

    public KeyboardBuilder(final Context context, @NonNull final KP params) {
        mContext = context;
        final Resources res = context.getResources();
        mResources = res;

        mParams = params;

        params.GRID_WIDTH = res.getInteger(R.integer.config_keyboard_grid_width);
        params.GRID_HEIGHT = res.getInteger(R.integer.config_keyboard_grid_height);
    }

    public void setAllowRedundantMoreKeys(final boolean enabled) {
        mParams.mAllowRedundantMoreKeys = enabled;
    }

    public KeyboardBuilder<KP> loadFromXml(final int xmlId, final KeyboardId id) {
        mParams.mId = id;
        try (XmlKeyboardParser keyboardParser = new XmlKeyboardParser(xmlId, mParams, mContext)) {
            keysInRows = keyboardParser.parseKeyboard();
        } catch (XmlPullParserException e) {
            Log.w(BUILDER_TAG, "keyboard XML parse error", e);
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (IOException e) {
            Log.w(BUILDER_TAG, "keyboard XML parse error", e);
            throw new RuntimeException(e.getMessage(), e);
        }
        return this;
    }

    @UsedForTesting
    public void disableTouchPositionCorrectionDataForTest() {
        mParams.mTouchPositionCorrection.setEnabled(false);
    }

    public void setProximityCharsCorrectionEnabled(final boolean enabled) {
        mParams.mProximityCharsCorrectionEnabled = enabled;
    }

    @NonNull
    public Keyboard build() {
        addKeysToParams();
        return new Keyboard(mParams);
    }

    private void startKeyboard() {
        mCurrentY += mParams.mTopPadding;
        mTopEdge = true;
    }

    private void startRow() {
        addEdgeSpace(mParams.mLeftPadding);
        mLeftEdge = true;
        mRightEdgeKey = null;
    }

    private void endRow() {
        int lastKeyHeight = 0;
        if (mRightEdgeKey != null) {
            mRightEdgeKey.markAsRightEdge(mParams);
            lastKeyHeight = mRightEdgeKey.getHeight() + mRightEdgeKey.getVerticalGap();
            mRightEdgeKey = null;
        }
        addEdgeSpace(mParams.mRightPadding);
        mCurrentY += lastKeyHeight;
        mTopEdge = false;
    }

    private void endKey(@NonNull final Key key) {
        mParams.onAddKey(key);
        if (mLeftEdge) {
            key.markAsLeftEdge(mParams);
            mLeftEdge = false;
        }
        if (mTopEdge) {
            key.markAsTopEdge(mParams);
        }
        mRightEdgeKey = key;
    }

    private void endKeyboard() {
        mParams.removeRedundantMoreKeys();
        // {@link #parseGridRows(XmlPullParser,boolean)} may populate keyboard rows higher than
        // previously expected.
        final int actualHeight = mCurrentY - mParams.mVerticalGap + mParams.mBottomPadding;
        mParams.mOccupiedHeight = Math.max(mParams.mOccupiedHeight, actualHeight);
    }

    private void addKeysToParams() {
        // need to reset it, we need to sum it up to get the height nicely
        // (though in the end we could just not touch it at all, final used value is the same as the one before resetting)
        mCurrentY = 0;
        startKeyboard();
        for (ArrayList<Key.KeyParams> row : keysInRows) {
            startRow();
            for (Key.KeyParams keyParams : row) {
                endKey(keyParams.createKey());
            }
            endRow();
        }
        endKeyboard();
    }

    private void addEdgeSpace(final float width) {
        mCurrentX += width;
        mLeftEdge = false;
        mRightEdgeKey = null;
    }
}
