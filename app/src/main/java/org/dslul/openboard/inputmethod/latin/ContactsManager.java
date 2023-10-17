/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteException;
import android.net.Uri;
import android.provider.ContactsContract.Contacts;
import android.text.TextUtils;
import android.util.Log;

import org.dslul.openboard.inputmethod.latin.common.Constants;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Manages all interactions with Contacts DB.
 *
 * The manager provides an API for listening to meaning full updates by keeping a
 * measure of the current state of the content provider.
 */
public class ContactsManager {
    private static final String TAG = "ContactsManager";

    /**
     * Use at most this many of the highest affinity contacts.
     */
    public static final int MAX_CONTACT_NAMES = 200;

    protected static class RankedContact {
        public final String mName;
        public final long mLastContactedTime;
        public final int mTimesContacted;
        public final boolean mInVisibleGroup;

        private float mAffinity = 0.0f;

        RankedContact(final Cursor cursor) {
            mName = cursor.getString(
                    ContactsDictionaryConstants.NAME_INDEX);
            mTimesContacted = cursor.getInt(
                    ContactsDictionaryConstants.TIMES_CONTACTED_INDEX);
            mLastContactedTime = cursor.getLong(
                    ContactsDictionaryConstants.LAST_TIME_CONTACTED_INDEX);
            mInVisibleGroup = cursor.getInt(
                    ContactsDictionaryConstants.IN_VISIBLE_GROUP_INDEX) == 1;
        }

        float getAffinity() {
            return mAffinity;
        }

        /**
         * Calculates the affinity with the contact based on:
         * - How many times it has been contacted
         * - How long since the last contact.
         * - Whether the contact is in the visible group (i.e., Contacts list).
         *
         * Note: This affinity is limited by the fact that some apps currently do not update the
         * LAST_TIME_CONTACTED or TIMES_CONTACTED counters. As a result, a frequently messaged
         * contact may still have 0 affinity.
         */
        void computeAffinity(final int maxTimesContacted, final long currentTime) {
            final float timesWeight = ((float) mTimesContacted + 1) / (maxTimesContacted + 1);
            final long timeSinceLastContact = Math.min(
                    Math.max(0, currentTime - mLastContactedTime),
                    TimeUnit.MILLISECONDS.convert(180, TimeUnit.DAYS));
            final float lastTimeWeight = (float) Math.pow(0.5,
                    timeSinceLastContact / (TimeUnit.MILLISECONDS.convert(10, TimeUnit.DAYS)));
            final float visibleWeight = mInVisibleGroup ? 1.0f : 0.0f;
            mAffinity = (timesWeight + lastTimeWeight + visibleWeight) / 3;
        }
    }

    private static class AffinityComparator implements Comparator<RankedContact> {
        @Override
        public int compare(RankedContact contact1, RankedContact contact2) {
            return Float.compare(contact2.getAffinity(), contact1.getAffinity());
        }
    }

    /**
     * Interface to implement for classes interested in getting notified for updates
     * to Contacts content provider.
     */
    public interface ContactsChangedListener {
        void onContactsChange();
    }

    /**
     * The number of contacts observed in the most recent instance of
     * contacts content provider.
     */
    private AtomicInteger mContactCountAtLastRebuild = new AtomicInteger(0);

    /**
     * The hash code of list of valid contacts names in the most recent dictionary
     * rebuild.
     */
    private AtomicInteger mHashCodeAtLastRebuild = new AtomicInteger(0);

    private final Context mContext;
    private final ContactsContentObserver mObserver;

    public ContactsManager(final Context context) {
        mContext = context;
        mObserver = new ContactsContentObserver(this /* ContactsManager */, context);
    }

    // TODO: This was synchronized in previous version. Why?
    public void registerForUpdates(final ContactsChangedListener listener) {
        mObserver.registerObserver(listener);
    }

    public int getContactCountAtLastRebuild() {
        return mContactCountAtLastRebuild.get();
    }

    public int getHashCodeAtLastRebuild() {
        return mHashCodeAtLastRebuild.get();
    }

    /**
     * Returns all the valid names in the Contacts DB. Callers should also
     * call {@link #updateLocalState(ArrayList)} after they are done with result
     * so that the manager can cache local state for determining updates.
     *
     * These names are sorted by their affinity to the user, with favorite
     * contacts appearing first.
     */
    public ArrayList<String> getValidNames(final Uri uri) {
        // Check all contacts since it's not possible to find out which names have changed.
        // This is needed because it's possible to receive extraneous onChange events even when no
        // name has changed.
        final Cursor cursor = mContext.getContentResolver().query(uri,
                ContactsDictionaryConstants.PROJECTION, null, null, null);
        final ArrayList<RankedContact> contacts = new ArrayList<>();
        int maxTimesContacted = 0;
        if (cursor != null) {
            try {
                if (cursor.moveToFirst()) {
                    while (!cursor.isAfterLast()) {
                        final String name = cursor.getString(
                                ContactsDictionaryConstants.NAME_INDEX);
                        if (isValidName(name)) {
                            final int timesContacted = cursor.getInt(
                                    ContactsDictionaryConstants.TIMES_CONTACTED_INDEX);
                            if (timesContacted > maxTimesContacted) {
                                maxTimesContacted = timesContacted;
                            }
                            contacts.add(new RankedContact(cursor));
                        }
                        cursor.moveToNext();
                    }
                }
            } finally {
                cursor.close();
            }
        }
        final long currentTime = System.currentTimeMillis();
        for (RankedContact contact : contacts) {
            contact.computeAffinity(maxTimesContacted, currentTime);
        }
        Collections.sort(contacts, new AffinityComparator());
        final HashSet<String> names = new HashSet<>();
        for (int i = 0; i < contacts.size() && names.size() < MAX_CONTACT_NAMES; ++i) {
            names.add(contacts.get(i).mName);
        }
        return new ArrayList<>(names);
    }

    /**
     * Returns the number of contacts in contacts content provider.
     */
    public int getContactCount() {
        // TODO: consider switching to a rawQuery("select count(*)...") on the database if
        // performance is a bottleneck.
        Cursor cursor = null;
        try {
            cursor = mContext.getContentResolver().query(Contacts.CONTENT_URI,
                    ContactsDictionaryConstants.PROJECTION_ID_ONLY, null, null, null);
            if (null == cursor) {
                return 0;
            }
            return cursor.getCount();
        } catch (final SQLiteException e) {
            Log.e(TAG, "SQLiteException in the remote Contacts process.", e);
        } finally {
            if (null != cursor) {
                cursor.close();
            }
        }
        return 0;
    }

    private static boolean isValidName(final String name) {
        if (TextUtils.isEmpty(name) || name.indexOf(Constants.CODE_COMMERCIAL_AT) != -1) {
            return false;
        }
        final boolean hasSpace = name.indexOf(Constants.CODE_SPACE) != -1;
        if (!hasSpace) {
            // Only allow an isolated word if it does not contain a hyphen.
            // This helps to filter out mailing lists.
            return name.indexOf(Constants.CODE_DASH) == -1;
        }
        return true;
    }

    /**
     * Updates the local state of the manager. This should be called when the callers
     * are done with all the updates of the content provider successfully.
     */
    public void updateLocalState(final ArrayList<String> names) {
        mContactCountAtLastRebuild.set(getContactCount());
        mHashCodeAtLastRebuild.set(names.hashCode());
    }

    /**
     * Performs any necessary cleanup.
     */
    public void close() {
        mObserver.unregister();
    }
}
