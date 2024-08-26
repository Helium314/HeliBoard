/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.latin;

import android.Manifest;
import android.content.ContentResolver;
import android.content.Context;
import android.database.ContentObserver;
import android.os.SystemClock;
import android.provider.ContactsContract.Contacts;
import org.samyarth.oskey.latin.utils.Log;

import org.samyarth.oskey.latin.ContactsManager.ContactsChangedListener;
import org.samyarth.oskey.latin.define.DebugFlags;
import org.samyarth.oskey.latin.permissions.PermissionsUtil;
import org.samyarth.oskey.latin.utils.ExecutorUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A content observer that listens to updates to content provider {@link Contacts#CONTENT_URI}.
 */
public class ContactsContentObserver implements Runnable {
    private static final String TAG = "ContactsContentObserver";

    private final Context mContext;
    private final ContactsManager mManager;
    private final AtomicBoolean mRunning = new AtomicBoolean(false);

    private ContentObserver mContentObserver;
    private ContactsChangedListener mContactsChangedListener;

    public ContactsContentObserver(final ContactsManager manager, final Context context) {
        mManager = manager;
        mContext = context;
    }

    public void registerObserver(final ContactsChangedListener listener) {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Not registering the observer.");
            // do nothing if we do not have the permission to read contacts.
            return;
        }

        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "registerObserver()");
        }
        mContactsChangedListener = listener;
        mContentObserver = new ContentObserver(null /* handler */) {
            @Override
            public void onChange(boolean self) {
                ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
                        .execute(ContactsContentObserver.this);
            }
        };
        final ContentResolver contentResolver = mContext.getContentResolver();
        contentResolver.registerContentObserver(Contacts.CONTENT_URI, true, mContentObserver);
    }

    @Override
    public void run() {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Not updating the contacts.");
            unregister();
            return;
        }

        if (!mRunning.compareAndSet(false /* expect */, true /* update */)) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(TAG, "run() : Already running. Don't waste time checking again.");
            }
            return;
        }
        if (haveContentsChanged()) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(TAG, "run() : Contacts have changed. Notifying listeners.");
            }
            mContactsChangedListener.onContactsChange();
        }
        mRunning.set(false);
    }

    boolean haveContentsChanged() {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Marking contacts as not changed.");
            return false;
        }

        final long startTime = SystemClock.uptimeMillis();
        final int contactCount = mManager.getContactCount();
        if (contactCount > ContactsDictionaryConstants.MAX_CONTACTS_PROVIDER_QUERY_LIMIT) {
            // If there are too many contacts then return false. In this rare case it is impossible
            // to include all of them anyways and the cost of rebuilding the dictionary is too high.
            // TODO: Sort and check only the most recent contacts?
            return false;
        }
        if (contactCount != mManager.getContactCountAtLastRebuild()) {
            if (DebugFlags.DEBUG_ENABLED) {
                Log.d(TAG, "haveContentsChanged() : Count changed from "
                        + mManager.getContactCountAtLastRebuild() + " to " + contactCount);
            }
            return true;
        }
        final ArrayList<String> names = mManager.getValidNames(Contacts.CONTENT_URI);
        if (names.hashCode() != mManager.getHashCodeAtLastRebuild()) {
            return true;
        }
        if (DebugFlags.DEBUG_ENABLED) {
            Log.d(TAG, "haveContentsChanged() : No change detected in "
                    + (SystemClock.uptimeMillis() - startTime) + " ms)");
        }
        return false;
    }

    public void unregister() {
        mContext.getContentResolver().unregisterContentObserver(mContentObserver);
    }
}
