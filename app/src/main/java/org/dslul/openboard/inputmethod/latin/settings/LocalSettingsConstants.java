/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

/**
 * Collection of device specific preference constants.
 */
public class LocalSettingsConstants {
    // Preference file for storing preferences that are tied to a device
    // and are not backed up.
    public static final String PREFS_FILE = "local_prefs";

    // Preference key for the current account.
    // Do not restore.
    public static final String PREF_ACCOUNT_NAME = "pref_account_name";
    // Preference key for enabling cloud sync feature.
    // Do not restore.
    public static final String PREF_ENABLE_CLOUD_SYNC = "pref_enable_cloud_sync";

    // List of preference keys to skip from being restored by backup agent.
    // These preferences are tied to a device and hence should not be restored.
    // e.g. account name.
    // Ideally they could have been kept in a separate file that wasn't backed up
    // however the preference UI currently only deals with the default
    // shared preferences which makes it non-trivial to move these out to
    // a different shared preferences file.
    public static final String[] PREFS_TO_SKIP_RESTORING = new String[] {
        PREF_ACCOUNT_NAME,
        PREF_ENABLE_CLOUD_SYNC,
        // The debug settings are not restored on a new device.
        // If a feature relies on these, it should ensure that the defaults are
        // correctly set for it to work on a new device.
        DebugSettings.PREF_DEBUG_MODE,
        DebugSettings.PREF_FORCE_NON_DISTINCT_MULTITOUCH,
        DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI,
        DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW
    };
}
