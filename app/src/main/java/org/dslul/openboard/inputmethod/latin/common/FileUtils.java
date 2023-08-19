/*
 * Copyright (C) 2013 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.dslul.openboard.inputmethod.latin.common;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * A simple class to help with removing directories recursively.
 */
public class FileUtils {
    private static final String TAG = "FileUtils";

    public static boolean deleteRecursively(final File path) {
        if (path.isDirectory()) {
            final File[] files = path.listFiles();
            if (files != null) {
                for (final File child : files) {
                    deleteRecursively(child);
                }
            }
        }
        return path.delete();
    }

    public static boolean deleteFilteredFiles(final File dir, final FilenameFilter fileNameFilter) {
        if (!dir.isDirectory()) {
            return false;
        }
        final File[] files = dir.listFiles(fileNameFilter);
        if (files == null) {
            return false;
        }
        boolean hasDeletedAllFiles = true;
        for (final File file : files) {
            if (!deleteRecursively(file)) {
                hasDeletedAllFiles = false;
            }
        }
        return hasDeletedAllFiles;
    }

    public static boolean renameTo(final File fromFile, final File toFile) {
        toFile.delete();
        return fromFile.renameTo(toFile);
    }

    public static void copyStreamToNewFile(InputStream in, File outfile) throws IOException {
        File parentFile = outfile.getParentFile();
        if (parentFile == null || (!parentFile.exists() && !parentFile.mkdirs())) {
            throw new IOException("could not create parent folder");
        }
        FileOutputStream out = new FileOutputStream(outfile);
        copyStreamToOtherStream(in, out);
    }

    public static void copyStreamToOtherStream(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[1024];
        int len;
        while ((len = in.read(buf)) > 0) {
            out.write(buf, 0, len);
        }
        out.flush();
    }

}
