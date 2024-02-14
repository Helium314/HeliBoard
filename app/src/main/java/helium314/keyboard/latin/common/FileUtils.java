/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.common;

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

    public static void copyStreamToNewFile(InputStream in, File outfile) throws IOException {
        File parentFile = outfile.getParentFile();
        if (parentFile == null || (!parentFile.exists() && !parentFile.mkdirs())) {
            throw new IOException("could not create parent folder");
        }
        FileOutputStream out = new FileOutputStream(outfile);
        copyStreamToOtherStream(in, out);
        out.close();
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
