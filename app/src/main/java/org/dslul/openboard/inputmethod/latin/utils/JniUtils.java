/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.app.Application;
import android.util.Log;

import org.dslul.openboard.inputmethod.latin.define.JniLibName;
import java.io.File;

public final class JniUtils {
    private static final String TAG = JniUtils.class.getSimpleName();

    // try loading keyboard libraries
    // first try user-provided library
    // then try google library for gesture typing (needs library in system, and app as system app)
    // finally fall back to internal library
    public static boolean sHaveGestureLib = false;
    static {
        try {
            // first try loading imported library, and fall back to default
            String filesDir;
            try {
                // try using reflection to get (app)context: https://stackoverflow.com/a/38967293
                final Application app = (Application) Class.forName("android.app.ActivityThread")
                        .getMethod("currentApplication").invoke(null, (Object[]) null);
                filesDir = app.getFilesDir().getAbsolutePath();
            } catch (Exception e) {
                // fall back to hardcoded default path, may not work on all phones
                filesDir = "/data/data/org.dslul.openboard.inputmethod.latin/files";
            }
            System.load(filesDir + File.separator + JniLibName.JNI_LIB_IMPORT_FILE_NAME);
            sHaveGestureLib = true; // this is an assumption, any way to actually check?
        } catch (Throwable t) { // catch everything, maybe provided library simply doesn't work
            Log.e(TAG, "Could not load native library " + JniLibName.JNI_LIB_IMPORT_FILE_NAME, t);
            try {
                System.loadLibrary(JniLibName.JNI_LIB_NAME_GOOGLE);
                sHaveGestureLib = true;
            } catch (UnsatisfiedLinkError ul) {
                Log.e(TAG, "Could not load native library " + JniLibName.JNI_LIB_NAME_GOOGLE, ul);
                try {
                    System.loadLibrary(JniLibName.JNI_LIB_NAME);
                } catch (UnsatisfiedLinkError ule) {
                    Log.e(TAG, "Could not load native library " + JniLibName.JNI_LIB_NAME, ule);
                }
            }
        }
    }

    private JniUtils() {
        // This utility class is not publicly instantiable.
    }

    public static void loadNativeLibrary() {
        // Ensures the static initializer is called
    }
}
