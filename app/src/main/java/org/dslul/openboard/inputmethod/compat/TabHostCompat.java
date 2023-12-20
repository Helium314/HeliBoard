/*
 * Copyright (C) 2006 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.compat;

import android.content.Context;
import android.content.res.Resources;
import android.util.AttributeSet;
import android.widget.TabHost;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

/*
 * Custom version of {@link TabHost} that triggers its {@link TabHost.OnTabChangeListener} when
 * a tab is reselected. It is hacky but it avoids importing material widgets lib.
 * See https://github.com/aosp-mirror/platform_frameworks_base/blob/8551ec363dcd7c2d7c82c45e89db4922156766ab/core/java/android/widget/TabHost.java#L428
 */
public class TabHostCompat extends TabHost implements TabHost.OnTabChangeListener {

    private boolean mFireOnTabChangeListenerOnReselection;
    private OnTabChangeListener mOnTabChangeListener;

    public TabHostCompat(Context context) {
        super(context);
    }

    public TabHostCompat(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    @Override
    public void setOnTabChangedListener(OnTabChangeListener l) {
        super.setOnTabChangedListener(l != null ? this : null);
        mOnTabChangeListener = l;
    }

    @Override
    public void setCurrentTab(int index) {
        super.setCurrentTab(index);
        if (index < 0 || index >= getTabWidget().getTabCount()) {
            return;
        }
        if (mOnTabChangeListener != null) {
            if (getCurrentTab() != index || mFireOnTabChangeListenerOnReselection) {
                mOnTabChangeListener.onTabChanged(getCurrentTabTag());
            }
        }
    }

    @Override
    public void onTabChanged(String s) {
        // Ignored
    }

    public void setFireOnTabChangeListenerOnReselection(boolean whether) {
        mFireOnTabChangeListenerOnReselection = whether;
    }

    @Override public void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Resources res = getContext().getResources();
        // fill full width, otherwise the layout is messed up
        final int width = ResourceUtils.getDefaultKeyboardWidth(res);
        final int height = res.getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
        setMeasuredDimension(width, height);
    }
}
