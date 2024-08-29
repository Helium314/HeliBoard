/*
 * Copyright (C) 2008 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.settings;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.inputmethod.InputMethodSubtype;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import org.oscar.kb.BuildConfig;
import org.oscar.kb.R;
import org.oscar.kb.latin.common.FileUtils;
import org.oscar.kb.latin.define.DebugFlags;
import org.oscar.kb.latin.utils.DictionaryUtilsKt;
import org.oscar.kb.latin.utils.SubtypeSettingsKt;
import org.oscar.kb.latin.utils.SubtypeUtilsKt;

import org.oscar.kb.latin.utils.DeviceProtectedUtils;
import org.oscar.kb.latin.utils.ExecutorUtils;
import org.oscar.kb.latin.utils.JniUtils;

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
    private final ArrayList<File> crashReportFiles = new ArrayList<>();

    @Override
    public void onCreate(final Bundle icicle) {
        super.onCreate(icicle);
        setHasOptionsMenu(true);
    }

    @Override
    public void onCreatePreferences(@Nullable Bundle bundle, @Nullable String s) {
        addPreferencesFromResource(R.xml.prefs);
        if (!JniUtils.sHaveGestureLib) {
            final Preference gesturePreference = findPreference(Settings.SCREEN_GESTURE);
            getPreferenceScreen().removePreference(gesturePreference);
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

    private String getEnabledSubtypesLabel() {
        final List<InputMethodSubtype> subtypes = SubtypeSettingsKt.getEnabledSubtypes(DeviceProtectedUtils.getSharedPreferences(getActivity()), true);
        final StringBuilder sb = new StringBuilder();
        for (final InputMethodSubtype subtype : subtypes) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(SubtypeUtilsKt.displayName(subtype, requireContext()));
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
            ExecutorUtils.getBackgroundExecutor(ExecutorUtils.KEYBOARD).execute(() -> saveCrashReport(uri));
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
