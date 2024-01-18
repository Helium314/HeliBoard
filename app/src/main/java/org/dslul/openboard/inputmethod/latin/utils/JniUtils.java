/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.annotation.SuppressLint;
import android.app.Application;
import android.os.Build;
import android.text.TextUtils;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import java.io.File;
import java.io.FileInputStream;

public final class JniUtils {
    private static final String TAG = JniUtils.class.getSimpleName();
    public static final String JNI_LIB_NAME = "jni_latinime";
    public static final String JNI_LIB_NAME_GOOGLE = "jni_latinimegoogle";
    public static final String JNI_LIB_IMPORT_FILE_NAME = "libjni_latinime.so";
    private static final String CHECKSUM_ARM64 = "b1049983e6ac5cfc6d1c66e38959751044fad213dff0637a6cf1d2a2703e754f";
    private static final String CHECKSUM_ARM32 = "442a2a8bfcb25489564bc9433a916fa4dc0dba9000fe6f6f03f5939b985091e6";
    private static final String CHECKSUM_X86_64 = "c882e12e6d48dd946e0b644c66868a720bd11ac3fecf152000e21a3d5abd59c9";
    private static final String CHECKSUM_X86 = "bd946d126c957b5a6dea3bafa07fa36a27950b30e2b684dffc60746d0a1c7ad8";

    public static String expectedDefaultChecksum() {
        final String abi = Build.SUPPORTED_ABIS[0];
        return switch (abi) {
            case "arm64-v8a" -> CHECKSUM_ARM64;
            case "armeabi-v7a" -> CHECKSUM_ARM32;
            case "x86_64" -> CHECKSUM_X86_64;
            case "x86" -> CHECKSUM_X86;
            default -> "-"; // invalid checksum that definitely will not match
        };
    }

    public static boolean sHaveGestureLib = false;
    static {
        // hardcoded default path, may not work on all phones
        @SuppressLint("SdCardPath") String filesDir = "/data/data/" + BuildConfig.APPLICATION_ID + "/files";
        Application app = null;
        try {
            // try using reflection to get (app)context: https://stackoverflow.com/a/38967293
            app = (Application) Class.forName("android.app.ActivityThread")
                    .getMethod("currentApplication").invoke(null, (Object[]) null);
            // and use the actual path if possible
            filesDir = app.getFilesDir().getAbsolutePath();
        } catch (Exception ignored) { }
        final File userSuppliedLibrary = new File(filesDir + File.separator + JNI_LIB_IMPORT_FILE_NAME);
        if (userSuppliedLibrary.exists()) {
            final String wantedChecksum = app == null ? expectedDefaultChecksum() : DeviceProtectedUtils.getSharedPreferences(app).getString("lib_checksum", "");
            try {
                final String checksum = ChecksumCalculator.INSTANCE.checksum(new FileInputStream(userSuppliedLibrary));
                if (TextUtils.equals(wantedChecksum, checksum)) {
                    // try loading the library
                    System.load(userSuppliedLibrary.getAbsolutePath());
                    sHaveGestureLib = true; // this is an assumption, any way to actually check?
                } else {
                    // delete if checksum doesn't match
                    // this actually is bad if we can't get the application and the user has a different library than expected
                    // todo: until the app is renamed, we continue loading the library anyway
//                    userSuppliedLibrary.delete();
                    System.load(userSuppliedLibrary.getAbsolutePath());
                    sHaveGestureLib = true;
                }
            } catch (Throwable t) { // catch everything, maybe provided library simply doesn't work
                Log.w(TAG, "Could not load user-supplied library", t);
            }
        }

        if (!sHaveGestureLib) {
            // try loading google library, will fail unless the library is in system and this is a system app
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
