// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.latin.utils;

import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.ColorDrawable;
import android.os.Build;
import android.view.View;
import android.view.WindowInsetsController;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import org.dslul.openboard.inputmethod.latin.R;

public class ColorSettingsUtils {

    public static void setSettingColor(final AppCompatActivity activity) {
        final ActionBar actionBar = activity.getSupportActionBar();
        final boolean isNight = ResourceUtils.isNight(activity.getResources());
        final ColorDrawable actionBarColor = new ColorDrawable(ContextCompat.getColor(activity, R.color.action_bar_color));
        final int backgroundColor = ContextCompat.getColor(activity, R.color.setup_background);

        if (actionBar == null) {
            return;
        }
        actionBar.setBackgroundDrawable(actionBarColor);
        // Settings background color
        activity.getWindow().getDecorView().getBackground().setColorFilter(backgroundColor, PorterDuff.Mode.SRC);

        // Set the status bar color
        if (actionBar.isShowing()) {
            activity.getWindow().setStatusBarColor(actionBarColor.getColor());
        } else {
            activity.getWindow().setStatusBarColor(backgroundColor);
        }

        // Navigation bar colors
        if (!isNight && !(Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)) {
            activity.getWindow().setNavigationBarColor(ColorUtils.setAlphaComponent(Color.GRAY, 180));
        } else {
            activity.getWindow().setNavigationBarColor(backgroundColor);
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