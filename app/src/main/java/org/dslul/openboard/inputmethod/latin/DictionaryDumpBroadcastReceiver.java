/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import org.dslul.openboard.inputmethod.latin.utils.Log;

public class DictionaryDumpBroadcastReceiver extends BroadcastReceiver {
  private static final String TAG = DictionaryDumpBroadcastReceiver.class.getSimpleName();

    private static final String DOMAIN = "org.dslul.openboard.inputmethod.latin";
    public static final String DICTIONARY_DUMP_INTENT_ACTION = DOMAIN + ".DICT_DUMP";
    public static final String DICTIONARY_NAME_KEY = "dictName";

    final LatinIME mLatinIme;

    public DictionaryDumpBroadcastReceiver(final LatinIME latinIme) {
        mLatinIme = latinIme;
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        final String action = intent.getAction();
        if (action.equals(DICTIONARY_DUMP_INTENT_ACTION)) {
            final String dictName = intent.getStringExtra(DICTIONARY_NAME_KEY);
            if (dictName == null) {
                Log.e(TAG, "Received dictionary dump intent action " +
                      "but the dictionary name is not set.");
                return;
            }
            mLatinIme.dumpDictionaryForDebug(dictName);
        }
    }
}
