/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.setup;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.inputmethod.InputMethodManager;

import helium314.keyboard.latin.utils.UncachedInputMethodManagerUtils;
import helium314.keyboard.settings.SettingsActivity;

public final class SetupActivity extends Activity {
    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final Intent intent = new Intent();
        final InputMethodManager imm = (InputMethodManager)getSystemService(INPUT_METHOD_SERVICE);
        if (UncachedInputMethodManagerUtils.isThisImeCurrent(this, imm))
            intent.setClass(this, SettingsActivity.class);
        else intent.setClass(this, SetupWizardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        if (!isFinishing()) {
            finish();
        }
    }
}
