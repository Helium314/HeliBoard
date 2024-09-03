/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.settings;

public class SettingsValuesForSuggestion {
    public final boolean mBlockPotentiallyOffensive;

    public SettingsValuesForSuggestion(
            final boolean blockPotentiallyOffensive,
            final boolean spaceAwareGesture
            ) {
        mBlockPotentiallyOffensive = blockPotentiallyOffensive;
        mSpaceAwareGesture = spaceAwareGesture;
    }

    public final boolean mSpaceAwareGesture;
}
