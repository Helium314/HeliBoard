/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.samyarth.oskey.keyboard.emoji;

import android.content.res.Resources;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;
import org.samyarth.oskey.R;
import org.samyarth.oskey.latin.settings.Settings;
import org.samyarth.oskey.latin.settings.SettingsValues;
import org.samyarth.oskey.latin.utils.ResourceUtils;

final class EmojiLayoutParams {
    private static final int DEFAULT_KEYBOARD_ROWS = 4;

    public final int mEmojiListHeight;
    private final int mEmojiListBottomMargin;
    public final int mEmojiKeyboardHeight;
    private final int mEmojiCategoryPageIdViewHeight;
    public final int mEmojiActionBarHeight;
    public final int mKeyVerticalGap;
    private final int mKeyHorizontalGap;
    private final int mBottomPadding;
    private final int mTopPadding;

    public EmojiLayoutParams(final Resources res) {
        final SettingsValues settingsValues = Settings.getInstance().getCurrent();
        final int defaultKeyboardHeight = ResourceUtils.getKeyboardHeight(res, settingsValues);
        final int defaultKeyboardWidth = ResourceUtils.getKeyboardWidth(res, settingsValues);
        if (settingsValues.mNarrowKeyGaps) {
            mKeyVerticalGap = (int) res.getFraction(R.fraction.config_key_vertical_gap_holo_narrow,
                    defaultKeyboardHeight, defaultKeyboardHeight);
            mKeyHorizontalGap = (int) (res.getFraction(R.fraction.config_key_horizontal_gap_holo_narrow,
                    defaultKeyboardWidth, defaultKeyboardWidth));
        } else {
            mKeyVerticalGap = (int) res.getFraction(R.fraction.config_key_vertical_gap_holo,
                    defaultKeyboardHeight, defaultKeyboardHeight);
            mKeyHorizontalGap = (int) (res.getFraction(R.fraction.config_key_horizontal_gap_holo,
                    defaultKeyboardWidth, defaultKeyboardWidth));
        }
        final float defaultBottomPadding = res.getFraction(R.fraction.config_keyboard_bottom_padding_holo, defaultKeyboardHeight, defaultKeyboardHeight);
        mBottomPadding = (int) (defaultBottomPadding * settingsValues.mBottomPaddingScale);
        final int paddingScaleOffset = (int) (mBottomPadding - defaultBottomPadding);
        mTopPadding = (int) res.getFraction(R.fraction.config_keyboard_top_padding_holo, defaultKeyboardHeight, defaultKeyboardHeight);
        mEmojiCategoryPageIdViewHeight = (int) (res.getDimension(R.dimen.config_emoji_category_page_id_height));
        final int baseheight = defaultKeyboardHeight - mBottomPadding - mTopPadding + mKeyVerticalGap;
        final int rows = DEFAULT_KEYBOARD_ROWS + (settingsValues.mShowsNumberRow ? 1 : 0); // for proper size considering number row
        mEmojiActionBarHeight = baseheight / rows - (mKeyVerticalGap - mBottomPadding) / 2 + paddingScaleOffset / 2;
        mEmojiListHeight = defaultKeyboardHeight - mEmojiActionBarHeight - mEmojiCategoryPageIdViewHeight;
        mEmojiListBottomMargin = 0;
        mEmojiKeyboardHeight = mEmojiListHeight - mEmojiListBottomMargin - 1;
    }

    public void setEmojiListProperties(final RecyclerView vp) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) vp.getLayoutParams();
        lp.height = mEmojiKeyboardHeight;
        lp.bottomMargin = mEmojiListBottomMargin;
        vp.setLayoutParams(lp);
    }

    public void setCategoryPageIdViewProperties(final View v) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.height = mEmojiCategoryPageIdViewHeight;
        v.setLayoutParams(lp);
    }

    public int getActionBarHeight() {
        return mEmojiActionBarHeight - mBottomPadding;
    }

    public void setActionBarProperties(final LinearLayout ll) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) ll.getLayoutParams();
        lp.height = getActionBarHeight();
        lp.width = ResourceUtils.getKeyboardWidth(ll.getResources(), Settings.getInstance().getCurrent());
        ll.setLayoutParams(lp);
    }

    public void setKeyProperties(final View v) {
        final LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) v.getLayoutParams();
        lp.leftMargin = mKeyHorizontalGap / 2;
        lp.rightMargin = mKeyHorizontalGap / 2;
        v.setLayoutParams(lp);
    }
}
