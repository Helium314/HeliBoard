/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.latin.personalization;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;


import org.samyarth.oskey.latin.common.FileUtils;
import org.samyarth.oskey.latin.utils.Log;

import java.io.File;
import java.io.FilenameFilter;
import java.lang.ref.SoftReference;
import java.util.Locale;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Helps handle and manage personalized dictionaries such as {@link org.samyarth.oskey.latin.personalization.UserHistoryDictionary}.
 */
public class PersonalizationHelper {
    private static final String TAG = PersonalizationHelper.class.getSimpleName();
    private static final boolean DEBUG = false;

    private static final ConcurrentHashMap<String, SoftReference<UserHistoryDictionary>>
            sLangUserHistoryDictCache = new ConcurrentHashMap<>();

    @NonNull
    public static UserHistoryDictionary getUserHistoryDictionary(
            final Context context, final Locale locale, @Nullable final String accountName) {
        String lookupStr = locale.toString();
        if (accountName != null) {
            lookupStr += "." + accountName;
        }
        synchronized (sLangUserHistoryDictCache) {
            if (sLangUserHistoryDictCache.containsKey(lookupStr)) {
                final SoftReference<UserHistoryDictionary> ref =
                        sLangUserHistoryDictCache.get(lookupStr);
                final UserHistoryDictionary dict = ref == null ? null : ref.get();
                if (dict != null) {
                    if (DEBUG) {
                        Log.d(TAG, "Use cached UserHistoryDictionary with lookup: " + lookupStr);
                    }
                    dict.reloadDictionaryIfRequired();
                    return dict;
                }
            }
            final UserHistoryDictionary dict = new UserHistoryDictionary(
                    context, locale, accountName);
            sLangUserHistoryDictCache.put(lookupStr, new SoftReference<>(dict));
            return dict;
        }
    }

    public static void removeAllUserHistoryDictionaries(final Context context) {
        synchronized (sLangUserHistoryDictCache) {
            for (final ConcurrentHashMap.Entry<String, SoftReference<UserHistoryDictionary>> entry
                    : sLangUserHistoryDictCache.entrySet()) {
                if (entry.getValue() != null) {
                    final UserHistoryDictionary dict = entry.getValue().get();
                    if (dict != null) {
                        dict.clear();
                    }
                }
            }
            sLangUserHistoryDictCache.clear();
            final File filesDir = context.getFilesDir();
            if (filesDir == null) {
                Log.e(TAG, "context.getFilesDir() returned null.");
                return;
            }
            final boolean filesDeleted = FileUtils.deleteFilteredFiles(
                    filesDir, new DictFilter(UserHistoryDictionary.NAME));
            if (!filesDeleted) {
                Log.e(TAG, "Cannot remove dictionary files. filesDir: " + filesDir.getAbsolutePath()
                        + ", dictNamePrefix: " + UserHistoryDictionary.NAME);
            }
        }
    }

    private static class DictFilter implements FilenameFilter {
        private final String mName;

        DictFilter(final String name) {
            mName = name;
        }

        @Override
        public boolean accept(final File dir, final String name) {
            return name.startsWith(mName);
        }
    }
}
