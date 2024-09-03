/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import org.oscar.kb.BuildConfig;
import org.oscar.kb.R;
import org.oscar.kb.keyboard.KeyboardSwitcher;
import org.oscar.kb.latin.DictionaryDumpBroadcastReceiver;
import org.oscar.kb.latin.DictionaryFacilitator;

/**
 * "Debug mode" settings sub screen.
 * <p>
 * This settings sub screen handles a several preference options for debugging.
 */
public final class DebugSettingsFragment extends SubScreenFragment
        implements Preference.OnPreferenceClickListener {
    private static final String PREF_KEY_DUMP_DICTS = "dump_dictionaries";
    private static final String PREF_KEY_DUMP_DICT_PREFIX = "dump_dictionaries";

    private boolean mServiceNeedsRestart = false;
    private TwoStatePreference mDebugMode;

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_debug);

        final PreferenceGroup dictDumpPreferenceGroup = findPreference(PREF_KEY_DUMP_DICTS);
        for (final String dictName : DictionaryFacilitator.DYNAMIC_DICTIONARY_TYPES) {
            final Preference pref = new DictDumpPreference(getActivity(), dictName);
            pref.setOnPreferenceClickListener(this);
            dictDumpPreferenceGroup.addPreference(pref);
        }
        if (BuildConfig.DEBUG)
            removePreference(DebugSettings.PREF_SHOW_DEBUG_SETTINGS);

        mServiceNeedsRestart = false;
        mDebugMode = findPreference(DebugSettings.PREF_DEBUG_MODE);
        findPreference(DebugSettings.PREF_SHOW_SUGGESTION_INFOS).setVisible(mDebugMode.isChecked());
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
        if (pref instanceof final DictDumpPreference dictDumpPref) {
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
        if (DebugSettings.PREF_DEBUG_MODE.equals(key) && mDebugMode != null) {
            final boolean enabled = prefs.getBoolean(DebugSettings.PREF_DEBUG_MODE, false);
            mDebugMode.setChecked(enabled);
            findPreference(DebugSettings.PREF_SHOW_SUGGESTION_INFOS).setVisible(enabled);
            mServiceNeedsRestart = true;
        } else if (key.equals(DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH)) {
            mServiceNeedsRestart = true;
        } else if (key.equals(DebugSettings.PREF_SHOW_SUGGESTION_INFOS)) {
            KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(requireContext());
        } else if (key.equals(DebugSettings.PREF_SHOW_DEBUG_SETTINGS) && mDebugMode.isChecked()) {
            mDebugMode.setChecked(false);
        }
    }

    private void updateDebugMode() {
        final String version = getString(R.string.version_text, BuildConfig.VERSION_NAME);
        mDebugMode.setSummary(version);
    }

}
