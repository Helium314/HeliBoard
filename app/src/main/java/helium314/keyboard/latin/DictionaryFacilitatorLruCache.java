/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.content.Context;
import helium314.keyboard.latin.utils.Log;

import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Cache for dictionary facilitators of multiple locales.
 * This class automatically creates and releases up to 3 facilitator instances using LRU policy.
 */
public class DictionaryFacilitatorLruCache {
    private static final String TAG = "DictFacilitatorLruCache";
    private static final int WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS = 1000;
    private static final int MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT = 5;

    private final Context mContext;
    private final String mDictionaryNamePrefix;
    private final Object mLock = new Object();
    private final DictionaryFacilitator mDictionaryFacilitator;
    private boolean mUseContactsDictionary;
    private Locale mLocale;

    public DictionaryFacilitatorLruCache(final Context context, final String dictionaryNamePrefix) {
        mContext = context;
        mDictionaryNamePrefix = dictionaryNamePrefix;
        mDictionaryFacilitator = DictionaryFacilitatorProvider.getDictionaryFacilitator(
                true /* isNeededForSpellChecking */);
    }

    private static void waitForLoadingMainDictionary(
            final DictionaryFacilitator dictionaryFacilitator) {
        for (int i = 0; i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT; i++) {
            try {
                dictionaryFacilitator.waitForLoadingMainDictionaries(
                        WAIT_FOR_LOADING_MAIN_DICT_IN_MILLISECONDS, TimeUnit.MILLISECONDS);
                return;
            } catch (final InterruptedException e) {
                Log.i(TAG, "Interrupted during waiting for loading main dictionary.", e);
                if (i < MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT - 1) {
                    Log.i(TAG, "Retry", e);
                } else {
                    Log.w(TAG, "Give up retrying. Retried "
                            + MAX_RETRY_COUNT_FOR_WAITING_FOR_LOADING_DICT + " times.", e);
                }
            }
        }
    }

    private void resetDictionariesForLocaleLocked() {
        // Nothing to do if the locale is null.  This would be the case before any get() calls.
        if (mLocale != null) {
          // Note: Given that personalized dictionaries are not used here; we can pass null account.
          mDictionaryFacilitator.resetDictionaries(mContext, mLocale,
              mUseContactsDictionary, false /* usePersonalizedDicts */,
              false /* forceReloadMainDictionary */, null /* account */,
              mDictionaryNamePrefix, null /* listener */);
        }
    }

    public void setUseContactsDictionary(final boolean useContactsDictionary) {
        synchronized (mLock) {
            if (mUseContactsDictionary == useContactsDictionary) {
                // The value has not been changed.
                return;
            }
            mUseContactsDictionary = useContactsDictionary;
            resetDictionariesForLocaleLocked();
            waitForLoadingMainDictionary(mDictionaryFacilitator);
        }
    }

    public DictionaryFacilitator get(final Locale locale) {
        synchronized (mLock) {
            if (!mDictionaryFacilitator.isForLocale(locale)) {
                mLocale = locale;
                resetDictionariesForLocaleLocked();
            }
            waitForLoadingMainDictionary(mDictionaryFacilitator);
            return mDictionaryFacilitator;
        }
    }

    public void closeDictionaries() {
        synchronized (mLock) {
            mDictionaryFacilitator.closeDictionaries();
        }
    }
}
