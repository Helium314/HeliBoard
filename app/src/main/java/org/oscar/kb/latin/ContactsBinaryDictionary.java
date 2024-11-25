/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin;

import android.Manifest;
import android.content.Context;
import android.net.Uri;
import android.provider.ContactsContract;
import android.provider.ContactsContract.Contacts;
import org.oscar.kb.latin.utils.Log;

import androidx.annotation.Nullable;


import com.android.inputmethod.latin.BinaryDictionary;

import org.oscar.kb.latin.ContactsManager.ContactsChangedListener;
import org.oscar.kb.latin.common.StringUtils;
import org.oscar.kb.latin.permissions.PermissionsUtil;

import java.io.File;
import java.util.ArrayList;
import java.util.Locale;


public class ContactsBinaryDictionary extends ExpandableBinaryDictionary
        implements ContactsChangedListener {
    private static final String TAG = ContactsBinaryDictionary.class.getSimpleName();
    private static final String NAME = "contacts";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DUMP = false;

    /**
     * Whether to use "firstname lastname" in bigram predictions.
     */
    private final boolean mUseFirstLastBigrams;
    private final ContactsManager mContactsManager;

    protected ContactsBinaryDictionary(final Context context, final Locale locale,
            final File dictFile, final String name) {
        super(context, getDictName(name, locale, dictFile), locale, TYPE_CONTACTS,
                dictFile);
        mUseFirstLastBigrams = ContactsDictionaryUtils.useFirstLastBigramsForLocale(locale);
        mContactsManager = new ContactsManager(context);
        mContactsManager.registerForUpdates(this /* listener */);
        reloadDictionaryIfRequired();
    }

    public static ContactsBinaryDictionary getDictionary(final Context context, final Locale locale,
            final File dictFile, final String dictNamePrefix, @Nullable final String account) {
        return new ContactsBinaryDictionary(context, locale, dictFile, dictNamePrefix + NAME);
    }

    @Override
    public synchronized void close() {
        mContactsManager.close();
        super.close();
    }

    /**
     * Typically called whenever the dictionary is created for the first time or
     * recreated when we think that there are updates to the dictionary.
     * This is called asynchronously.
     */
    @Override
    public void loadInitialContentsLocked() {
        loadDictionaryForUriLocked(ContactsContract.Profile.CONTENT_URI);
        // TODO: Switch this URL to the newer ContactsContract too
        loadDictionaryForUriLocked(Contacts.CONTENT_URI);
    }

    /**
     * Loads data within content providers to the dictionary.
     */
    private void loadDictionaryForUriLocked(final Uri uri) {
        if (!PermissionsUtil.checkAllPermissionsGranted(
                mContext, Manifest.permission.READ_CONTACTS)) {
            Log.i(TAG, "No permission to read contacts. Not loading the Dictionary.");
        }

        final ArrayList<String> validNames = mContactsManager.getValidNames(uri);
        for (final String name : validNames) {
            addNameLocked(name);
        }
        if (uri.equals(Contacts.CONTENT_URI)) {
            // Since we were able to add content successfully, update the local
            // state of the manager.
            mContactsManager.updateLocalState(validNames);
        }
    }

    /**
     * Adds the words in a name (e.g., firstname/lastname) to the binary dictionary along with their
     * bigrams depending on locale.
     */
    private void addNameLocked(final String name) {
        int len = StringUtils.codePointCount(name);
        NgramContext ngramContext = NgramContext.getEmptyPrevWordsContext(
                BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM);
        // TODO: Better tokenization for non-Latin writing systems
        for (int i = 0; i < len; i++) {
            if (Character.isLetter(name.codePointAt(i))) {
                int end = ContactsDictionaryUtils.getWordEndPosition(name, len, i);
                String word = name.substring(i, end);
                if (DEBUG_DUMP) {
                    Log.d(TAG, "addName word = " + word);
                }
                i = end - 1;
                // Don't add single letter words, possibly confuses
                // capitalization of i.
                final int wordLen = StringUtils.codePointCount(word);
                if (wordLen <= MAX_WORD_LENGTH && wordLen > 1) {
                    if (DEBUG) {
                        Log.d(TAG, "addName " + name + ", " + word + ", "  + ngramContext);
                    }
                    runGCIfRequiredLocked(true /* mindsBlockByGC */);
                    addUnigramLocked(word, ContactsDictionaryConstants.FREQUENCY_FOR_CONTACTS,
                            null /* shortcut */, 0 /* shortcutFreq */, false /* isNotAWord */,
                            false /* isPossiblyOffensive */,
                            BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                    if (ngramContext.isValid() && mUseFirstLastBigrams) {
                        runGCIfRequiredLocked(true /* mindsBlockByGC */);
                        addNgramEntryLocked(ngramContext,
                                word,
                                ContactsDictionaryConstants.FREQUENCY_FOR_CONTACTS_BIGRAM,
                                BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                    }
                    ngramContext = ngramContext.getNextNgramContext(
                            new NgramContext.WordInfo(word));
                }
            }
        }
    }

    @Override
    public void onContactsChange() {
        setNeedsToRecreate();
    }
}
