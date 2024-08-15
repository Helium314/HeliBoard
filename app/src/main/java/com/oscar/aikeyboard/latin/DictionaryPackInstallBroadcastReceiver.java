/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package com.oscar.aikeyboard.latin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import com.oscar.aikeyboard.latin.utils.Log;

import com.oscar.aikeyboard.dictionarypack.DictionaryPackConstants;

/**
 * Receives broadcasts pertaining to dictionary management and takes the appropriate action.
 * <p>
 * This object receives three types of broadcasts.
 * - Package installed/added. When a dictionary provider application is added or removed, we
 * need to query the dictionaries.
 * - New dictionary broadcast. The dictionary provider broadcasts new dictionary availability. When
 * this happens, we need to re-query the dictionaries.
 * - Unknown client. If the dictionary provider is in urgent need of data about some client that
 * it does not know, it sends this broadcast. When we receive this, we need to tell the dictionary
 * provider about ourselves. This happens when the settings for the dictionary pack are accessed,
 * but Latin IME never got a chance to register itself.
 */
public final class DictionaryPackInstallBroadcastReceiver extends BroadcastReceiver {
    private static final String TAG = DictionaryPackInstallBroadcastReceiver.class.getSimpleName();

    final LatinIME mService;

    public DictionaryPackInstallBroadcastReceiver() {
        // This empty constructor is necessary for the system to instantiate this receiver.
        // This happens when the dictionary pack says it can't find a record for our client,
        // which happens when the dictionary pack settings are called before the keyboard
        // was ever started once.
        Log.i(TAG, "Latin IME dictionary broadcast receiver instantiated from the framework.");
        mService = null;
    }

    public DictionaryPackInstallBroadcastReceiver(final LatinIME service) {
        mService = service;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        final PackageManager manager = context.getPackageManager();

        // We need to reread the dictionary if a new dictionary package is installed.
        if (Intent.ACTION_PACKAGE_ADDED.equals(action)) {
            if (null == mService) {
                Log.e(TAG, "Called with intent " + action + " but we don't know the service: this "
                        + "should never happen");
                return;
            }
            final Uri packageUri = intent.getData();
            if (null == packageUri) return; // No package name : we can't do anything
            final String packageName = packageUri.getSchemeSpecificPart();
            if (null == packageName) return;
            final PackageInfo packageInfo;
            try {
                packageInfo = manager.getPackageInfo(packageName, PackageManager.GET_PROVIDERS);
            } catch (android.content.pm.PackageManager.NameNotFoundException e) {
                return; // No package info : we can't do anything
            }
            final ProviderInfo[] providers = packageInfo.providers;
            if (null == providers) return; // No providers : it is not a dictionary.

            // Search for some dictionary pack in the just-installed package. If found, reread.
            for (ProviderInfo info : providers) {
                if (DictionaryPackConstants.AUTHORITY.equals(info.authority)) {
                    mService.resetSuggestMainDict();
                    return;
                }
            }
            // If we come here none of the authorities matched the one we searched for.
            // We can exit safely.
        } else if (Intent.ACTION_PACKAGE_REMOVED.equals(action)
                && !intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
            if (null == mService) {
                Log.e(TAG, "Called with intent " + action + " but we don't know the service: this "
                        + "should never happen");
                return;
            }
            // When the dictionary package is removed, we need to reread dictionary (to use the
            // next-priority one, or stop using a dictionary at all if this was the only one,
            // since this is the user request).
            // If we are replacing the package, we will receive ADDED right away so no need to
            // remove the dictionary at the moment, since we will do it when we receive the
            // ADDED broadcast.
            mService.resetSuggestMainDict();
        } else if (DictionaryPackConstants.NEW_DICTIONARY_INTENT_ACTION.equals(action)) {
            if (null == mService) {
                Log.e(TAG, "Called with intent " + action + " but we don't know the service: this "
                        + "should never happen");
                return;
            }
            mService.resetSuggestMainDict();
        }
    }
}
