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

package org.dslul.openboard.inputmethod.keyboard.emoji;

import android.text.TextUtils;
import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.internal.MoreKeySpec;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * This is a Keyboard class where you can add keys dynamically shown in a grid layout
 */
public class DynamicGridKeyboard extends Keyboard {

    private static final String TAG = "DynamicGridKeyboard";
    private static final int TEMPLATE_KEY_CODE_0 = 0x30;
    private static final int TEMPLATE_KEY_CODE_1 = 0x31;
    private final Object mLock = new Object();

    private final int mHorizontalStep;
    private final int mHorizontalGap;
    private final int mVerticalStep;
    private final int mColumnsNum;
    private final LinkedList<GridKey> mGridKeys = new LinkedList<>();

    private List<Key> mCachedGridKeys;

    public DynamicGridKeyboard(final Keyboard templateKeyboard) {
        super(templateKeyboard);
        final Key key0 = getTemplateKey(TEMPLATE_KEY_CODE_0);
        final Key key1 = getTemplateKey(TEMPLATE_KEY_CODE_1);
        mHorizontalGap = Math.abs(key1.getX() - key0.getX()) - key0.getWidth();
        mHorizontalStep = key0.getWidth() + mHorizontalGap;
        mVerticalStep = key0.getHeight() + mVerticalGap;
        mColumnsNum = mBaseWidth / mHorizontalStep;
    }

    private Key getTemplateKey(final int code) {
        for (final Key key : super.getSortedKeys()) {
            if (key.getCode() == code) {
                return key;
            }
        }
        throw new RuntimeException("Can't find template key: code=" + code);
    }

    public int getDynamicOccupiedHeight() {
        final int row = (mGridKeys.size() - 1) / mColumnsNum + 1;
        return row * mVerticalStep;
    }

    public int getColumnsCount() {
        return mColumnsNum;
    }

    public int getKeyCount() {
        return mGridKeys.size();
    }

    public void addKeyFrom(final Key baseKey) {
        if (baseKey == null) {
            return;
        }
        addKey(makeGridKey(baseKey));
    }

    public void addKey(final GridKey key) {
        if (key == null) {
            return;
        }
        synchronized (mLock) {
            mCachedGridKeys = null;
            while (mGridKeys.remove(key)) {
                // Remove duplicate keys.
            }
            mGridKeys.add(key);
        }
        updateKeysCoordinates();
    }

    public void removeLastKey() {
        mGridKeys.removeLast();
    }

    public void sortKeys(Comparator<GridKey> comparator) {
        Collections.sort(mGridKeys, comparator);
        updateKeysCoordinates();
    }

    protected GridKey makeGridKey(Key baseKey) {
        return new GridKey(baseKey,
                baseKey.getMoreKeys(),
                baseKey.getHintLabel(),
                baseKey.getBackgroundType());
    }

    private void updateKeysCoordinates() {
        int index = 0;
        for (final GridKey gridKey : mGridKeys) {
            final int keyX0 = getKeyX0(index);
            final int keyY0 = getKeyY0(index);
            final int keyX1 = getKeyX1(index);
            final int keyY1 = getKeyY1(index);
            gridKey.updateCoordinates(keyX0, keyY0, keyX1, keyY1);
            index++;
        }
    }

    private int getKeyX0(final int index) {
        final int column = index % mColumnsNum;
        return column * mHorizontalStep + mHorizontalGap / 2;
    }

    private int getKeyX1(final int index) {
        final int column = index % mColumnsNum + 1;
        return column * mHorizontalStep + mHorizontalGap / 2;
    }

    private int getKeyY0(final int index) {
        final int row = index / mColumnsNum;
        return row * mVerticalStep + mVerticalGap / 2;
    }

    private int getKeyY1(final int index) {
        final int row = index / mColumnsNum + 1;
        return row * mVerticalStep + mVerticalGap / 2;
    }

    @Override
    public List<Key> getSortedKeys() {
        synchronized (mLock) {
            if (mCachedGridKeys != null) {
                return mCachedGridKeys;
            }
            final ArrayList<Key> cachedKeys = new ArrayList<>(mGridKeys);
            mCachedGridKeys = Collections.unmodifiableList(cachedKeys);
            return mCachedGridKeys;
        }
    }

    @Override
    public List<Key> getNearestKeys(final int x, final int y) {
        // TODO: Calculate the nearest key index in mGridKeys from x and y.
        return getSortedKeys();
    }

    protected static class GridKey extends Key {
        private int mCurrentX;
        private int mCurrentY;

        public GridKey(@Nonnull final Key originalKey, @Nullable final MoreKeySpec[] moreKeys,
             @Nullable final String labelHint, final int backgroundType) {
            super(originalKey, moreKeys, labelHint, backgroundType);
        }

        public void updateCoordinates(final int x0, final int y0, final int x1, final int y1) {
            mCurrentX = x0;
            mCurrentY = y0;
            getHitBox().set(x0, y0, x1, y1);
        }

        @Override
        public int getX() {
            return mCurrentX;
        }

        @Override
        public int getY() {
            return mCurrentY;
        }

        @Override
        public boolean equals(final Object o) {
            if (!(o instanceof Key)) return false;
            final Key key = (Key)o;
            if (getCode() != key.getCode()) return false;
            if (!TextUtils.equals(getLabel(), key.getLabel())) return false;
            return TextUtils.equals(getOutputText(), key.getOutputText());
        }

        @Override
        public int hashCode() {
            return Objects.hash(getCode(), getLabel(), getOutputText());
        }

        @Override
        public String toString() {
            return "GridKey: " + super.toString();
        }
    }
}
