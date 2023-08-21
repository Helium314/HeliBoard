/*
 * Copyright (C) 2008 The Android Open Source Project
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

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.Preference;
import android.preference.PreferenceScreen;
import android.provider.Settings.Secure;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.FileUtils;
import org.dslul.openboard.inputmethod.latin.utils.ApplicationUtils;
import org.dslul.openboard.inputmethod.latin.utils.FeedbackUtils;
import org.dslul.openboard.inputmethod.latin.utils.JniUtils;
import org.dslul.openboard.inputmethodcommon.InputMethodSettingsFragment;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SettingsFragment extends InputMethodSettingsFragment {
    // We don't care about menu grouping.
    private static final int NO_MENU_GROUP = Menu.NONE;
    // The first menu item id and order.
    private static final int MENU_ABOUT = Menu.FIRST;
    // The second menu item id and order.
    private static final int MENU_HELP_AND_FEEDBACK = Menu.FIRST + 1;
    private static final int CRASH_REPORT_REQUEST_CODE = 985287532;
    // for storing crash report files, so onActivityResult can actually use them
    private final ArrayList<File> crashReportFiles = new ArrayList<>();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
        setInputMethodSettingsCategoryTitle(R.string.language_selection_title);
        setSubtypeEnablerTitle(R.string.select_language);
        setSubtypeEnablerIcon(R.drawable.ic_settings_languages);
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.setTitle(
                ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));
        if (!JniUtils.sHaveGestureLib) {
            final Preference gesturePreference = findPreference(Settings.SCREEN_GESTURE);
            preferenceScreen.removePreference(gesturePreference);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        final ActionBar actionBar = getActivity().getActionBar();
        final CharSequence screenTitle = getPreferenceScreen().getTitle();
        if (actionBar != null && screenTitle != null) {
            actionBar.setTitle(screenTitle);
        }
        if (BuildConfig.DEBUG)
            askAboutCrashReports();
    }

    @Override
    public void onCreateOptionsMenu(final Menu menu, final MenuInflater inflater) {
        if (FeedbackUtils.isHelpAndFeedbackFormSupported()) {
            menu.add(NO_MENU_GROUP, MENU_HELP_AND_FEEDBACK /* itemId */,
                    MENU_HELP_AND_FEEDBACK /* order */, R.string.help_and_feedback);
        }
        final int aboutResId = FeedbackUtils.getAboutKeyboardTitleResId();
        if (aboutResId != 0) {
            menu.add(NO_MENU_GROUP, MENU_ABOUT /* itemId */, MENU_ABOUT /* order */, aboutResId);
        }
    }

    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        final Activity activity = getActivity();
        if (!isUserSetupComplete(activity)) {
            // If setup is not complete, it's not safe to launch Help or other activities
            // because they might go to the Play Store.  See b/19866981.
            return true;
        }
        final int itemId = item.getItemId();
        if (itemId == MENU_HELP_AND_FEEDBACK) {
            FeedbackUtils.showHelpAndFeedbackForm(activity);
            return true;
        }
        if (itemId == MENU_ABOUT) {
            final Intent aboutIntent = FeedbackUtils.getAboutKeyboardIntent(activity);
            if (aboutIntent != null) {
                startActivity(aboutIntent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    private static boolean isUserSetupComplete(final Activity activity) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            return true;
        }
        return Secure.getInt(activity.getContentResolver(), "user_setup_complete", 0) != 0;
    }

    private void askAboutCrashReports() {
        // find crash report files
        final File dir = getActivity().getExternalFilesDir(null);
        if (dir == null) return;
//        final File[] files = dir.listFiles((file, s) -> file.getName().startsWith("crash_report"));
        final File[] allFiles = dir.listFiles();
        if (allFiles == null) return;
        crashReportFiles.clear();
        for (File file : allFiles) {
            if (file.getName().startsWith("crash_report"))
                crashReportFiles.add(file);
        }
        if (crashReportFiles.isEmpty()) return;
        new AlertDialog.Builder(getActivity())
                .setMessage("Crash report files found")
                .setPositiveButton("get", (dialogInterface, i) -> {
                    final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_TITLE, "crash_reports.zip");
                    intent.setType("application/zip");
                    startActivityForResult(intent, CRASH_REPORT_REQUEST_CODE);
                })
                .setNeutralButton("delete", (dialogInterface, i) -> {
                    for (File file : crashReportFiles) {
                        file.delete(); // don't care whether it fails, though user will complain
                    }
                })
                .setNegativeButton("ignore", null)
                .show();
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != Activity.RESULT_OK || data == null) return;
        if (requestCode != CRASH_REPORT_REQUEST_CODE) return;
        if (crashReportFiles.isEmpty()) return;
        final Uri uri = data.getData();
        if (uri == null) return;
        final OutputStream os;
        try {
            os = getActivity().getContentResolver().openOutputStream(uri);
            final BufferedOutputStream bos = new BufferedOutputStream(os);
            final ZipOutputStream z = new ZipOutputStream(bos);
            for (File file : crashReportFiles) {
                FileInputStream f = new FileInputStream(file);
                z.putNextEntry(new ZipEntry(file.getName()));
                FileUtils.copyStreamToOtherStream(f, z);
                f.close();
                z.closeEntry();
            }
            z.close();
            bos.close();
            os.close();
            for (File file : crashReportFiles) {
                file.delete();
            }
        } catch (IOException ignored) { }
    }
}
