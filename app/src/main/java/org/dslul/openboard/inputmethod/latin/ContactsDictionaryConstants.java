/*
 * Copyright (C) 2015 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.provider.BaseColumns;
import android.provider.ContactsContract.Contacts;

/**
 * Constants related to Contacts Content Provider.
 */
public class ContactsDictionaryConstants {
    public static final String[] PROJECTION = { BaseColumns._ID, Contacts.DISPLAY_NAME,
            Contacts.TIMES_CONTACTED, Contacts.LAST_TIME_CONTACTED, Contacts.IN_VISIBLE_GROUP };
    public static final String[] PROJECTION_ID_ONLY = { BaseColumns._ID };

    /**
     * Frequency for contacts information into the dictionary
     */
    public static final int FREQUENCY_FOR_CONTACTS = 40;
    public static final int FREQUENCY_FOR_CONTACTS_BIGRAM = 90;

    /**
     *  Do not attempt to query contacts if there are more than this many entries.
     */
    public static final int MAX_CONTACTS_PROVIDER_QUERY_LIMIT = 10000;

    /**
     * Index of the column for 'name' in content providers:
     * Contacts & ContactsContract.Profile.
     */
    public static final int NAME_INDEX = 1;
    public static final int TIMES_CONTACTED_INDEX = 2;
    public static final int LAST_TIME_CONTACTED_INDEX = 3;
    public static final int IN_VISIBLE_GROUP_INDEX = 4;
}
