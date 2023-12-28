/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Intent;
import android.os.Bundle;

/**
 * Test activity to use when testing preference fragments. <br/>
 * Usage: <br/>
 * Create an ActivityInstrumentationTestCase2 for this activity
 * and call setIntent() with an intent that specifies the fragment to load in the activity.
 * The fragment can then be obtained from this activity and used for testing/verification.
 */
public final class TestFragmentActivity extends Activity {
    /**
     * The fragment name that should be loaded when starting this activity.
     * This must be specified when starting this activity, as this activity is only
     * meant to test fragments from instrumentation tests.
     */
    public static final String EXTRA_SHOW_FRAGMENT = "show_fragment";

    public Fragment mFragment;

    @Override
    protected void onCreate(final Bundle savedState) {
        super.onCreate(savedState);
        final Intent intent = getIntent();
        final String fragmentName = intent.getStringExtra(EXTRA_SHOW_FRAGMENT);
        if (fragmentName == null) {
            throw new IllegalArgumentException("No fragment name specified for testing");
        }

        mFragment = Fragment.instantiate(this, fragmentName);
        FragmentManager fragmentManager = getFragmentManager();
        fragmentManager.beginTransaction().add(mFragment, fragmentName).commit();
    }
}
