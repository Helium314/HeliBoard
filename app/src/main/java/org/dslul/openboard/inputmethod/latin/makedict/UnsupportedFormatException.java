/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.makedict;

/**
 * Simple exception thrown when a file format is not recognized.
 */
public final class UnsupportedFormatException extends Exception {
    public UnsupportedFormatException(String description) {
        super(description);
    }
}
