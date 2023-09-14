/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Bundle;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import org.dslul.openboard.inputmethod.latin.DictionaryDumpBroadcastReceiver;
import org.dslul.openboard.inputmethod.latin.DictionaryFacilitatorImpl;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ApplicationUtils;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import java.util.Locale;

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
        final Resources res = getResources();
        setupKeyPreviewAnimationDuration(DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        setupKeyPreviewAnimationDuration(DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        final float defaultKeyPreviewShowUpStartScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_show_up_start_scale);
        final float defaultKeyPreviewDismissEndScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_dismiss_end_scale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE,
                defaultKeyPreviewShowUpStartScale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE,
                defaultKeyPreviewShowUpStartScale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE,
                defaultKeyPreviewDismissEndScale);
        setupKeyPreviewAnimationScale(DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE,
                defaultKeyPreviewDismissEndScale);

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

    private void setupKeyPreviewAnimationScale(final String prefKey, final float defaultValue) {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = findPreference(prefKey);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            private static final float PERCENTAGE_FLOAT = 100.0f;

            private float getValueFromPercentage(final int percentage) {
                return percentage / PERCENTAGE_FLOAT;
            }

            private int getPercentageFromValue(final float floatValue) {
                return (int)(floatValue * PERCENTAGE_FLOAT);
            }

            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putFloat(key, getValueFromPercentage(value)).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return getPercentageFromValue(
                        Settings.readKeyPreviewAnimationScale(prefs, key, defaultValue));
            }

            @Override
            public int readDefaultValue(final String key) {
                return getPercentageFromValue(defaultValue);
            }

            @Override
            public String getValueText(final int value) {
                if (value < 0) {
                    return res.getString(R.string.settings_system_default);
                }
                return String.format(Locale.ROOT, "%d%%", value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    private void setupKeyPreviewAnimationDuration(final String prefKey, final int defaultValue) {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = findPreference(prefKey);
        if (pref == null) {
            return;
        }
        pref.setInterface(new SeekBarDialogPreference.ValueProxy() {
            @Override
            public void writeValue(final int value, final String key) {
                prefs.edit().putInt(key, value).apply();
            }

            @Override
            public void writeDefaultValue(final String key) {
                prefs.edit().remove(key).apply();
            }

            @Override
            public int readValue(final String key) {
                return Settings.readKeyPreviewAnimationDuration(prefs, key, defaultValue);
            }

            @Override
            public int readDefaultValue(final String key) {
                return defaultValue;
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, Integer.toString(value));
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

}
