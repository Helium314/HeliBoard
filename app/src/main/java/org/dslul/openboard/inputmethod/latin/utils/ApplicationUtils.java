/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import org.dslul.openboard.inputmethod.latin.utils.Log;

public final class ApplicationUtils {
    private static final String TAG = ApplicationUtils.class.getSimpleName();

    private ApplicationUtils() {
        // This utility class is not publicly instantiable.
    }

    public static int getActivityTitleResId(final Context context,
            final Class<? extends Activity> cls) {
        final ComponentName cn = new ComponentName(context, cls);
        try {
            final ActivityInfo ai = context.getPackageManager().getActivityInfo(cn, 0);
            if (ai != null) {
                return ai.labelRes;
            }
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Failed to get settings activity title res id.", e);
        }
        return 0;
    }

    /**
     * A utility method to get the application's PackageInfo.versionName
     * @return the application's PackageInfo.versionName
     */
    public static String getVersionName(final Context context) {
        try {
            if (context == null) {
                return "";
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionName;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return "";
    }

    /**
     * A utility method to get the application's PackageInfo.versionCode
     * @return the application's PackageInfo.versionCode
     */
    public static int getVersionCode(final Context context) {
        try {
            if (context == null) {
                return 0;
            }
            final String packageName = context.getPackageName();
            final PackageInfo info = context.getPackageManager().getPackageInfo(packageName, 0);
            return info.versionCode;
        } catch (final NameNotFoundException e) {
            Log.e(TAG, "Could not find version info.", e);
        }
        return 0;
    }
}
