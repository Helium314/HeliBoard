/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.util.LruCache;

/**
 * Factory for instantiating DictionaryFacilitator objects.
 */
public class DictionaryFacilitatorProvider {
    public static DictionaryFacilitator getDictionaryFacilitator(boolean isNeededForSpellChecking) {
        final DictionaryFacilitator facilitator = new DictionaryFacilitatorImpl();
        if (isNeededForSpellChecking)
            facilitator.setValidSpellingWordReadCache(new LruCache<>(200));
        return facilitator;
    }
}
