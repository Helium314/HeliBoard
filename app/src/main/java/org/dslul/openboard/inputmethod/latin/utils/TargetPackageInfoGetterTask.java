/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.util.LruCache;

import org.dslul.openboard.inputmethod.compat.AppWorkaroundsUtils;

public final class TargetPackageInfoGetterTask extends
        AsyncTask<String, Void, PackageInfo> {
    private static final int MAX_CACHE_ENTRIES = 64; // arbitrary
    private static final LruCache<String, PackageInfo> sCache = new LruCache<>(MAX_CACHE_ENTRIES);

    public static PackageInfo getCachedPackageInfo(final String packageName) {
        if (null == packageName) return null;
        return sCache.get(packageName);
    }

    public static void removeCachedPackageInfo(final String packageName) {
        sCache.remove(packageName);
    }

    private Context mContext;
    private final AsyncResultHolder<AppWorkaroundsUtils> mResult;

    public TargetPackageInfoGetterTask(final Context context,
            final AsyncResultHolder<AppWorkaroundsUtils> result) {
        mContext = context;
        mResult = result;
    }

    @Override
    protected PackageInfo doInBackground(final String... packageName) {
        final PackageManager pm = mContext.getPackageManager();
        mContext = null; // Bazooka-powered anti-leak device
        try {
            final PackageInfo packageInfo = pm.getPackageInfo(packageName[0], 0 /* flags */);
            sCache.put(packageName[0], packageInfo);
            return packageInfo;
        } catch (android.content.pm.PackageManager.NameNotFoundException e) {
            return null;
        }
    }

    @Override
    protected void onPostExecute(final PackageInfo info) {
        mResult.set(new AppWorkaroundsUtils(info));
    }
}
