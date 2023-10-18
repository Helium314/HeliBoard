/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import org.dslul.openboard.inputmethod.latin.DictionaryDumpBroadcastReceiver;
import org.dslul.openboard.inputmethod.latin.DictionaryFacilitatorImpl;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ApplicationUtils;

/**
 * "Debug mode" settings sub screen.
 *
 * This settings sub screen handles a several preference options for debugging.
 */
public final class DebugSettingsFragment extends SubScreenFragment
        implements Preference.OnPreferenceClickListener {
    private static final String PREF_KEY_DUMP_DICTS = "pref_key_dump_dictionaries";
    private static final String PREF_KEY_DUMP_DICT_PREFIX = "pref_key_dump_dictionaries";

    private boolean mServiceNeedsRestart = false;
    private TwoStatePreference mDebugMode;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_debug);

        if (!Settings.SHOULD_SHOW_LXX_SUGGESTION_UI) {
            removePreference(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI);
        }

        final PreferenceGroup dictDumpPreferenceGroup = findPreference(PREF_KEY_DUMP_DICTS);
        for (final String dictName : DictionaryFacilitatorImpl.DICT_TYPE_TO_CLASS.keySet()) {
            final Preference pref = new DictDumpPreference(getActivity(), dictName);
            pref.setOnPreferenceClickListener(this);
            dictDumpPreferenceGroup.addPreference(pref);
        }

        mServiceNeedsRestart = false;
        mDebugMode = findPreference(DebugSettings.PREF_DEBUG_MODE);
        updateDebugMode();
    }

    private static class DictDumpPreference extends Preference {
        public final String mDictName;

        public DictDumpPreference(final Context context, final String dictName) {
            super(context);
            setKey(PREF_KEY_DUMP_DICT_PREFIX + dictName);
            setTitle("Dump " + dictName + " dictionary");
            mDictName = dictName;
        }
    }

    @Override
    public boolean onPreferenceClick(@NonNull final Preference pref) {
        if (pref instanceof DictDumpPreference) {
            final DictDumpPreference dictDumpPref = (DictDumpPreference)pref;
            final String dictName = dictDumpPref.mDictName;
            final Intent intent = new Intent(
                    DictionaryDumpBroadcastReceiver.DICTIONARY_DUMP_INTENT_ACTION);
            intent.putExtra(DictionaryDumpBroadcastReceiver.DICTIONARY_NAME_KEY, dictName);
            pref.getContext().sendBroadcast(intent);
            return true;
        }
        return true;
    }

    @Override
    public void onStop() {
        super.onStop();
        if (mServiceNeedsRestart) {
            Runtime.getRuntime().exit(0);
        }
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key.equals(DebugSettings.PREF_DEBUG_MODE) && mDebugMode != null) {
            mDebugMode.setChecked(prefs.getBoolean(DebugSettings.PREF_DEBUG_MODE, false));
            updateDebugMode();
            mServiceNeedsRestart = true;
        } else if (key.equals(DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH)) {
            mServiceNeedsRestart = true;
        }
    }

    private void updateDebugMode() {
        boolean isDebugMode = mDebugMode.isChecked();
        final String version = getString(
                R.string.version_text, ApplicationUtils.getVersionName(getActivity()));
        if (!isDebugMode) {
            mDebugMode.setTitle(version);
            mDebugMode.setSummary(null);
        } else {
            mDebugMode.setTitle(getString(R.string.prefs_debug_mode));
            mDebugMode.setSummary(version);
        }
    }

}
