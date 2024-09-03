/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin;


/**
 * Factory for instantiating DictionaryFacilitator objects.
 */
public class DictionaryFacilitatorProvider {
    public static DictionaryFacilitator getDictionaryFacilitator(boolean isNeededForSpellChecking) {
        return new DictionaryFacilitatorImpl();
    }
}
