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

import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;

import androidx.appcompat.app.AlertDialog;

import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SystemBroadcastReceiver;
import org.dslul.openboard.inputmethod.latin.define.JniLibName;
import java.io.File;
import java.io.FileOutputStream;
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

        final Resources res = getResources();
        final Context context = getActivity();

        // When we are called from the Settings application but we are not already running, some
        // singleton and utility classes may not have been initialized.  We have to call
        // initialization method of these classes here. See {@link LatinIME#onCreate()}.
        AudioAndHapticFeedbackManager.init(context);

        final SharedPreferences prefs = getSharedPreferences();

        if (!Settings.isInternal(prefs)) {
            removePreference(Settings.SCREEN_DEBUG);
        }

        setupKeyLongpressTimeoutSettings();
        final Preference bla = findPreference("load_gesture_library");
        if (bla != null) {
            bla.setOnPreferenceClickListener(new Preference.OnPreferenceClickListener() {
                @Override
                public boolean onPreferenceClick(Preference preference) {
                    // get architecture for telling user which file to use
                    String abi;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        abi = Build.SUPPORTED_ABIS[0];
                    } else {
                        abi = Build.CPU_ABI;
                    }
                    // show delete / add dialog
                    final AlertDialog.Builder builder = new AlertDialog.Builder(context)
                            .setTitle(R.string.load_gesture_library)
                            .setMessage(context.getString(R.string.load_gesture_library_message, abi))
                            .setPositiveButton(R.string.load_gesture_library_button_load, new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                                            .addCategory(Intent.CATEGORY_OPENABLE)
                                            .setType("application/octet-stream");
                                    startActivityForResult(intent, REQUEST_CODE_GESTURE_LIBRARY);
                                }
                            })
                            .setNegativeButton(android.R.string.cancel, null);
                    libfile = new File(context.getFilesDir().getAbsolutePath() + File.separator + JniLibName.JNI_LIB_IMPORT_FILE_NAME);
                    if (libfile.exists())
                        builder.setNeutralButton(R.string.load_gesture_library_button_delete, new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialogInterface, int i) {
                                libfile.delete();
                                Runtime.getRuntime().exit(0);
                            }
                        });
                    builder.show();
                    return true;
                }
            });
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode != REQUEST_CODE_GESTURE_LIBRARY || resultCode != Activity.RESULT_OK || resultData == null) return;
        if (resultData.getData() != null && libfile != null) {
            try {
                FileOutputStream out = new FileOutputStream(libfile);
                final InputStream in = getActivity().getContentResolver().openInputStream(resultData.getData());
                byte[] buf = new byte[1024];
                int len;
                while ((len = in.read(buf)) > 0) {
                    out.write(buf, 0, len);
                }
                out.flush();
                Runtime.getRuntime().exit(0);
            } catch (IOException e) {
                // should inform user
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
                return res.getString(R.string.abbreviation_unit_milliseconds, value);
            }

            @Override
            public void feedbackValue(final int value) {}
        });
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (key.equals(Settings.PREF_SHOW_SETUP_WIZARD_ICON)) {
            SystemBroadcastReceiver.toggleAppIcon(getActivity());
        }
    }
}
