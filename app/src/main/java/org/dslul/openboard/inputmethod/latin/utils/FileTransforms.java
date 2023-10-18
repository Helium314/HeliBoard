/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.GZIPInputStream;

public final class FileTransforms {
    public static OutputStream getCryptedStream(OutputStream out) {
        // Crypt the stream.
        return out;
    }

    public static InputStream getDecryptedStream(InputStream in) {
        // Decrypt the stream.
        return in;
    }

    public static InputStream getUncompressedStream(InputStream in) throws IOException {
        return new GZIPInputStream(in);
    }
}
