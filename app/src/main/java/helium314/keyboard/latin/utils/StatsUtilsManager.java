/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */


package helium314.keyboard.latin.utils;

import android.content.Context;

import helium314.keyboard.latin.DictionaryFacilitator;
import helium314.keyboard.latin.settings.SettingsValues;

@SuppressWarnings("unused")
public class StatsUtilsManager {

    private static final StatsUtilsManager sInstance = new StatsUtilsManager();
    private static StatsUtilsManager sTestInstance = null;

    /**
     * @return the singleton instance of {@link StatsUtilsManager}.
     */
    public static StatsUtilsManager getInstance() {
        return sTestInstance != null ? sTestInstance : sInstance;
    }

    public static void setTestInstance(final StatsUtilsManager testInstance) {
        sTestInstance = testInstance;
    }

    public void onCreate(final Context context, final DictionaryFacilitator dictionaryFacilitator) {
    }

    public void onLoadSettings(final Context context, final SettingsValues settingsValues) {
    }

    public void onStartInputView() {
    }

    public void onFinishInputView() {
    }

    public void onDestroy(final Context context) {
    }
}
