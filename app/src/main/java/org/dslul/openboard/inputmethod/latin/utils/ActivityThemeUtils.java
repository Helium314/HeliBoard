// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.latin.utils;

import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.appcompat.app.AppCompatActivity;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.setup.SetupWizardActivity;

public class ActivityThemeUtils {

    public static void setActivityTheme(final AppCompatActivity activity) {
        final boolean isNight = ResourceUtils.isNight(activity.getResources());

        // Set status bar color only in install views
        if (activity instanceof SetupWizardActivity) {
            activity.getWindow().setStatusBarColor(activity.getResources().getColor(R.color.setup_background));
        }

        // Set the icons of the status bar and the navigation bar light or dark
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController controller = activity.getWindow().getInsetsController();
            if (controller != null && !isNight) {
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS, WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS);
                controller.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS, WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            final View view = activity.getWindow().getDecorView();
            view.setSystemUiVisibility(!isNight ? View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR : 0);
        }
    }

}