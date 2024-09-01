/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji;

import android.content.res.Resources;
import android.view.View;
import android.widget.LinearLayout;

import androidx.recyclerview.widget.RecyclerView;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.utils.ResourceUtils;

final class EmojiLayoutParams {
    private static final int DEFAULT_KEYBOARD_ROWS = 4;

    public final int mEmojiListHeight;
    private final int mEmojiListBottomMargin;
    public final int mEmojiKeyboardHeight;
    private final int mEmojiCategoryPageIdViewHeight;
    public final int mBottomRowKeyboardHeight;
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
        mBottomRowKeyboardHeight = baseheight / rows - (mKeyVerticalGap - mBottomPadding) / 2 + paddingScaleOffset / 2;
        mEmojiListHeight = defaultKeyboardHeight - mBottomRowKeyboardHeight - mEmojiCategoryPageIdViewHeight;
        mEmojiListBottomMargin = 0;
        // height calculation is not good enough, probably also because keyboard top padding might be off by a pixel
        final float offset = 3f * res.getDisplayMetrics().density * settingsValues.mKeyboardHeightScale;
        mEmojiKeyboardHeight = mEmojiListHeight - mEmojiListBottomMargin - ((int) offset);
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

    public int getBottomRowKeyboardHeight() {
        return mBottomRowKeyboardHeight - mBottomPadding;
    }
}
