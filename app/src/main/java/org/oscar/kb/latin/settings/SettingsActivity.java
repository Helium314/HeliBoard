/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.oscar.kb.latin.settings;

import static android.preference.PreferenceActivity.EXTRA_NO_HEADERS;
import static android.preference.PreferenceActivity.EXTRA_SHOW_FRAGMENT;

import android.content.Intent;
import android.os.Bundle;

import org.oscar.kb.latin.permissions.PermissionsManager;
import org.oscar.kb.latin.utils.ActivityThemeUtils;
import org.oscar.kb.latin.utils.NewDictionaryAdder;


import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

public final class SettingsActivity extends AppCompatActivity
        implements ActivityCompat.OnRequestPermissionsResultCallback {
    private static final String DEFAULT_FRAGMENT = SettingsFragment.class.getName();

    public static final String EXTRA_ENTRY_KEY = "entry";
    public static final String EXTRA_ENTRY_VALUE_APP_ICON = "app_icon";

    @Override
    protected void onCreate(final Bundle savedState) {
        super.onCreate(savedState);
        final ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeButtonEnabled(true);
        }
        final Intent i = getIntent();
        if (Intent.ACTION_VIEW.equals(i.getAction()) && i.getData() != null) {
            new NewDictionaryAdder(this, null).addDictionary(i.getData(), null);
            setIntent(new Intent()); // avoid opening again
        }
        if (getSupportFragmentManager().getFragments().isEmpty())
            getSupportFragmentManager().beginTransaction()
                    .replace(android.R.id.content, new SettingsFragment())
                    .commit();

        ActivityThemeUtils.setActivityTheme(this);
    }

    @Override
    public boolean onSupportNavigateUp() {
        onBackPressed();
        return true;
    }

    @Override
    public Intent getIntent() {
        final Intent intent = super.getIntent();
        final String fragment = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        if (fragment == null) {
            intent.putExtra(EXTRA_SHOW_FRAGMENT, DEFAULT_FRAGMENT);
        }
        intent.putExtra(EXTRA_NO_HEADERS, true);
        return intent;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        PermissionsManager.get(this).onRequestPermissionsResult(requestCode, permissions, grantResults);
    }
}
