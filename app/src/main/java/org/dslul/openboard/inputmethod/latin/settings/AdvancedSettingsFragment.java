/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;

import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet;
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SystemBroadcastReceiver;
import org.dslul.openboard.inputmethod.latin.common.FileUtils;
import org.dslul.openboard.inputmethod.latin.define.JniLibName;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * "Advanced" settings sub screen.
 *
 * This settings sub screen handles the following advanced preferences.
 * - Key popup dismiss delay
 * - Keypress vibration duration
 * - Keypress sound volume
 * - Show app icon
 * - Improve keyboard
 * - Debug settings
 */
public final class AdvancedSettingsFragment extends SubScreenFragment {
    private final int REQUEST_CODE_GESTURE_LIBRARY = 570289;
    File libfile = null;
    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        addPreferencesFromResource(R.xml.prefs_screen_advanced);

        final Context context = requireContext();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context);

        if (!BuildConfig.DEBUG) {
            removePreference(Settings.SCREEN_DEBUG);
        }

        setupKeyLongpressTimeoutSettings();
        final Preference loadGestureLibrary = findPreference("load_gesture_library");
        if (loadGestureLibrary != null) {
            loadGestureLibrary.setOnPreferenceClickListener(preference -> {
                // get architecture for telling user which file to use
                String abi;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    abi = Build.SUPPORTED_ABIS[0];
                } else {
                    abi = Build.CPU_ABI;
                }
                // show delete / add dialog
                final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                        .setTitle(R.string.load_gesture_library)
                        .setMessage(context.getString(R.string.load_gesture_library_message, abi))
                        .setPositiveButton(R.string.load_gesture_library_button_load, (dialogInterface, i) -> {
                            final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                    .addCategory(Intent.CATEGORY_OPENABLE)
                                    .setType("application/octet-stream");
                            startActivityForResult(intent, REQUEST_CODE_GESTURE_LIBRARY);
                        })
                        .setNegativeButton(android.R.string.cancel, null);
                libfile = new File(context.getFilesDir().getAbsolutePath() + File.separator + JniLibName.JNI_LIB_IMPORT_FILE_NAME);
                if (libfile.exists())
                    builder.setNeutralButton(R.string.load_gesture_library_button_delete, (dialogInterface, i) -> {
                        libfile.delete();
                        Runtime.getRuntime().exit(0);
                    });
                builder.show();
                return true;
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode != REQUEST_CODE_GESTURE_LIBRARY || resultCode != Activity.RESULT_OK || resultData == null) return;
        if (resultData.getData() != null && libfile != null) {
            try {
                final InputStream in = requireContext().getContentResolver().openInputStream(resultData.getData());
                FileUtils.copyStreamToNewFile(in, libfile);
                Runtime.getRuntime().exit(0); // exit will restart the app, so library will be loaded
            } catch (IOException e) {
                // should inform user, but probably the issues will only come when reading the library
            }
        }
    }


    private void setupKeyLongpressTimeoutSettings() {
        final SharedPreferences prefs = getSharedPreferences();
        final Resources res = getResources();
        final SeekBarDialogPreference pref = (SeekBarDialogPreference)findPreference(
                Settings.PREF_KEY_LONGPRESS_TIMEOUT);
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
                return Settings.readKeyLongpressTimeout(prefs, res);
            }

            @Override
            public int readDefaultValue(final String key) {
                return Settings.readDefaultKeyLongpressTimeout(res);
            }

            @Override
            public String getValueText(final int value) {
                return res.getString(R.string.abbreviation_unit_milliseconds, Integer.toString(value));
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key.equals(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            SystemBroadcastReceiver.toggleAppIcon(requireContext());
        } else if (key.equals(Settings.PREF_SHOW_ALL_MORE_KEYS)) {
            KeyboardLayoutSet.onKeyboardThemeChanged();
        }
    }
}
