/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings.Secure;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.inputmethod.InputMethodSubtype;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceScreen;

import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.FileUtils;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.utils.ApplicationUtils;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.DictionaryUtilsKt;
import org.dslul.openboard.inputmethod.latin.utils.ExecutorUtils;
import org.dslul.openboard.inputmethod.latin.utils.FeedbackUtils;
import org.dslul.openboard.inputmethod.latin.utils.JniUtils;

import java.util.List;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class SettingsFragment extends PreferenceFragmentCompat {
    // We don't care about menu grouping.
    private static final int NO_MENU_GROUP = Menu.NONE;
    // The first menu item id and order.
    private static final int MENU_ABOUT = Menu.FIRST;
    // The second menu item id and order.
    private static final int MENU_HELP_AND_FEEDBACK = Menu.FIRST + 1;
    // for storing crash report files, so onActivityResult can actually use them
    private final ArrayList<File> crashReportFiles = new ArrayList<>();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle bundle, @Nullable String s) {
        addPreferencesFromResource(R.xml.prefs);
        final PreferenceScreen preferenceScreen = getPreferenceScreen();
        preferenceScreen.setTitle(ApplicationUtils.getActivityTitleResId(getActivity(), SettingsActivity.class));
        if (!JniUtils.sHaveGestureLib) {
            final Preference gesturePreference = findPreference(Settings.SCREEN_GESTURE);
            preferenceScreen.removePreference(gesturePreference);
        }
        ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD)
                .execute(() -> DictionaryUtilsKt.cleanUnusedMainDicts(requireContext()));
    }

    @Override
    public void onResume() {
        super.onResume();
        final Activity activity = getActivity();
        if (activity instanceof AppCompatActivity) {
            final ActionBar actionBar = ((AppCompatActivity) activity).getSupportActionBar();
            final CharSequence screenTitle = getPreferenceScreen().getTitle();
            if (actionBar != null && screenTitle != null) {
                actionBar.setTitle(screenTitle);
            }
        }

        // sometimes wrong languages are returned when not initializing on creation of LatinIME
        // this might be a bug, at least it's not documented
        // but anyway, here is really rare (LatinIme should be loaded when the settings are opened)
        SubtypeSettingsKt.init(getActivity());

        findPreference("screen_languages").setSummary(getEnabledSubtypesLabel());
        if (BuildConfig.DEBUG || DebugFlags.DEBUG_ENABLED)
            askAboutCrashReports();
    }

    @Override
    public void onCreateOptionsMenu(@NonNull final Menu menu, @NonNull final MenuInflater inflater) {
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
    public boolean onOptionsItemSelected(@NonNull final MenuItem item) {
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

    private String getEnabledSubtypesLabel() {
        final List<InputMethodSubtype> subtypes = SubtypeSettingsKt.getEnabledSubtypes(DeviceProtectedUtils.getSharedPreferences(getActivity()), true);
        final StringBuilder sb = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(subtype.getDisplayName(getActivity(), requireContext().getPackageName(), requireContext().getApplicationInfo()));
        }
        return sb.toString();
    }

    private void askAboutCrashReports() {
        // find crash report files
        final File dir = requireContext().getExternalFilesDir(null);
        if (dir == null) return;
        final File[] allFiles = dir.listFiles();
        if (allFiles == null) return;
        crashReportFiles.clear();
        for (File file : allFiles) {
            if (file.getName().startsWith("crash_report"))
                crashReportFiles.add(file);
        }
        if (crashReportFiles.isEmpty()) return;
        new AlertDialog.Builder(requireContext())
                .setMessage("Crash report files found")
                .setPositiveButton("get", (dialogInterface, i) -> {
                    final Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                    intent.addCategory(Intent.CATEGORY_OPENABLE);
                    intent.putExtra(Intent.EXTRA_TITLE, "crash_reports.zip");
                    intent.setType("application/zip");
                    crashReportFilePicker.launch(intent);
                })
                .setNeutralButton("delete", (dialogInterface, i) -> {
                    for (File file : crashReportFiles) {
                        file.delete(); // don't care whether it fails, though user will complain
                    }
                })
                .setNegativeButton("ignore", null)
                .show();
    }

    final ActivityResultLauncher<Intent> crashReportFilePicker = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), (intent) -> {
        if (intent.getResultCode() != Activity.RESULT_OK || intent.getData() == null) return;
        final Uri uri = intent.getData().getData();
        if (uri != null)
            saveCrashReport(uri);
    });

    private void saveCrashReport(final Uri uri) {
        if (uri == null || crashReportFiles.isEmpty()) return;
        final OutputStream os;
        try {
            os = requireContext().getContentResolver().openOutputStream(uri);
            if (os == null) return;
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
