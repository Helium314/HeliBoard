/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.app.Application;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import java.io.File;

public final class JniUtils {
    private static final String TAG = JniUtils.class.getSimpleName();
    public static final String JNI_LIB_NAME = "jni_latinime";
    public static final String JNI_LIB_NAME_GOOGLE = "jni_latinimegoogle";
    public static final String JNI_LIB_IMPORT_FILE_NAME = "libjni_latinime.so";


    public static boolean sHaveGestureLib = false;
    static {
        String filesDir;
        try {
            // try using reflection to get (app)context: https://stackoverflow.com/a/38967293
            final Application app = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null, (Object[]) null);
            filesDir = app.getFilesDir().getAbsolutePath();
        } catch (Exception e) {
            // fall back to hardcoded default path, may not work on all phones
            filesDir = "/data/data/" + BuildConfig.APPLICATION_ID + "/files";
        }
        final File userSuppliedLibrary = new File(filesDir + File.separator + JNI_LIB_IMPORT_FILE_NAME);
        if (userSuppliedLibrary.exists()) {
            try {
                System.load(filesDir + File.separator + JNI_LIB_IMPORT_FILE_NAME);
                sHaveGestureLib = true; // this is an assumption, any way to actually check?
            } catch (Throwable t) { // catch everything, maybe provided library simply doesn't work
                Log.w(TAG, "Could not load user-supplied library", t);
            }
        }

        if (!sHaveGestureLib) {
            // try loading google library, will fail unless it's in system and this is a system app
            try {
                System.loadLibrary(JNI_LIB_NAME_GOOGLE);
                sHaveGestureLib = true;
            } catch (UnsatisfiedLinkError ul) {
                Log.w(TAG, "Could not load system glide typing library " + JNI_LIB_NAME_GOOGLE, ul);
            }
        }
        if (!sHaveGestureLib) {
            // try loading built-in library
            try {
                System.loadLibrary(JNI_LIB_NAME);
            } catch (UnsatisfiedLinkError ul) {
                Log.w(TAG, "Could not load native library " + JNI_LIB_NAME, ul);
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
