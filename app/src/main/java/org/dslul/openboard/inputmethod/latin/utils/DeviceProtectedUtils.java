/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.preference.PreferenceManager;
import org.dslul.openboard.inputmethod.latin.utils.Log;

import androidx.annotation.RequiresApi;

public final class DeviceProtectedUtils {

    static final String TAG = DeviceProtectedUtils.class.getSimpleName();
    private static SharedPreferences prefs;

    public static SharedPreferences getSharedPreferences(final Context context) {
        if (prefs != null)
            return prefs;
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs;
        }
        Context deviceProtectedContext = getDeviceProtectedContext(context);
        prefs = PreferenceManager.getDefaultSharedPreferences(deviceProtectedContext);
        if (prefs.getAll().isEmpty()) {
            Log.i(TAG, "Device encrypted storage is empty, copying values from credential encrypted storage");
            deviceProtectedContext.moveSharedPreferencesFrom(context, PreferenceManager.getDefaultSharedPreferencesName(context));
        }
        return prefs;
    }

    @RequiresApi(api = Build.VERSION_CODES.N)
    private static Context getDeviceProtectedContext(final Context context) {
        return context.isDeviceProtectedStorage()
                ? context : context.createDeviceProtectedStorageContext();
    }

    private DeviceProtectedUtils() {
        // This utility class is not publicly instantiable.
    }
}
