/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import org.dslul.openboard.inputmethod.latin.AssetFileAddress;
import org.dslul.openboard.inputmethod.latin.makedict.DictionaryHeader;

import java.io.File;

public class DictionaryHeaderUtils {

    public static int getContentVersion(AssetFileAddress fileAddress) {
        final DictionaryHeader header = DictionaryInfoUtils.getDictionaryFileHeaderOrNull(
                new File(fileAddress.mFilename), fileAddress.mOffset, fileAddress.mLength);
        return Integer.parseInt(header.mVersionString);
    }
}
