/*
 * Copyright (C) 2014 The Android Open Source Project
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

import androidx.annotation.NonNull;

import org.dslul.openboard.inputmethod.keyboard.Key;

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
