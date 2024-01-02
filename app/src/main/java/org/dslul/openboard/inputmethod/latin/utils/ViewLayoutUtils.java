/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.MarginLayoutParams;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;

public final class ViewLayoutUtils {
    private ViewLayoutUtils() {
        // This utility class is not publicly instantiable.
    }

    public static MarginLayoutParams newLayoutParam(final ViewGroup placer, final int width,
            final int height) {
        if (placer instanceof FrameLayout) {
            return new FrameLayout.LayoutParams(width, height);
        } else if (placer instanceof RelativeLayout) {
            return new RelativeLayout.LayoutParams(width, height);
        } else if (placer == null) {
            throw new NullPointerException("placer is null");
        } else {
            throw new IllegalArgumentException("placer is neither FrameLayout nor RelativeLayout: "
                    + placer.getClass().getName());
        }
    }

    public static void placeViewAt(final View view, final int x, final int y, final int w,
            final int h) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof final MarginLayoutParams marginLayoutParams) {
            marginLayoutParams.width = w;
            marginLayoutParams.height = h;
            marginLayoutParams.setMargins(x, y, 0, 0);
        }
    }

    public static void updateLayoutHeightOf(final Window window, final int layoutHeight) {
        final WindowManager.LayoutParams params = window.getAttributes();
        if (params != null && params.height != layoutHeight) {
            params.height = layoutHeight;
            window.setAttributes(params);
        }
    }

    public static void updateLayoutHeightOf(final View view, final int layoutHeight) {
        final ViewGroup.LayoutParams params = view.getLayoutParams();
        if (params != null && params.height != layoutHeight) {
            params.height = layoutHeight;
            view.setLayoutParams(params);
        }
    }

    public static void updateLayoutGravityOf(final View view, final int layoutGravity) {
        final ViewGroup.LayoutParams lp = view.getLayoutParams();
        if (lp instanceof final LinearLayout.LayoutParams params) {
            if (params.gravity != layoutGravity) {
                params.gravity = layoutGravity;
                view.setLayoutParams(params);
            }
        } else if (lp instanceof final FrameLayout.LayoutParams params) {
            if (params.gravity != layoutGravity) {
                params.gravity = layoutGravity;
                view.setLayoutParams(params);
            }
        } else {
            throw new IllegalArgumentException("Layout parameter doesn't have gravity: "
                    + lp.getClass().getName());
        }
    }
}
