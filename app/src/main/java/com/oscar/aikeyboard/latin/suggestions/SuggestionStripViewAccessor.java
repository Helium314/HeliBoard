/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin.suggestions;


import com.oscar.aikeyboard.latin.SuggestedWords;

/**
 * An object that gives basic control of a suggestion strip and some info on it.
 */
public interface SuggestionStripViewAccessor {
    void setNeutralSuggestionStrip();
    void showSuggestionStrip(final SuggestedWords suggestedWords);
}
