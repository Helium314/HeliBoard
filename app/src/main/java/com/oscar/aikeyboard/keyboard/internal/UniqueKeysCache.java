/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.keyboard.internal;

import androidx.annotation.NonNull;

import com.oscar.aikeyboard.keyboard.Key;

import java.util.HashMap;

public abstract class UniqueKeysCache {
    public abstract void setEnabled(boolean enabled);
    public abstract void clear();
    public abstract @NonNull Key getUniqueKey(@NonNull Key key);

    @NonNull
    public static final UniqueKeysCache NO_CACHE = new UniqueKeysCache() {
        @Override
        public void setEnabled(boolean enabled) {}

        @Override
        public void clear() {}

        @NonNull
        @Override
        public Key getUniqueKey(@NonNull Key key) { return key; }
    };

    @NonNull
    public static UniqueKeysCache newInstance() {
        return new UniqueKeysCacheImpl();
    }

    private static final class UniqueKeysCacheImpl extends UniqueKeysCache {
        private final HashMap<Key, Key> mCache;

        private boolean mEnabled;

        UniqueKeysCacheImpl() {
            mCache = new HashMap<>();
        }

        @Override
        public void setEnabled(final boolean enabled) {
            mEnabled = enabled;
        }

        @Override
        public void clear() {
            mCache.clear();
        }

        @NonNull
        @Override
        public Key getUniqueKey(@NonNull final Key key) {
            if (!mEnabled) {
                return key;
            }
            final Key existingKey = mCache.get(key);
            if (existingKey != null) {
                // Reuse the existing object that equals to "key" without adding "key" to
                // the cache.
                return existingKey;
            }
            mCache.put(key, key);
            return key;
        }
    }
}
