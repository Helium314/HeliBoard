/*
 * Copyright (C) 2019 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */

package org.dslul.openboard.inputmethod.latin.utils;

import static android.util.TypedValue.COMPLEX_UNIT_DIP;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.common.ImageViewStyle;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.ColorType;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

import java.util.ArrayList;
import java.util.List;

// Modified code from https://android.googlesource.com/platform/development/+/master/samples/AutofillKeyboard/
@RequiresApi(api = Build.VERSION_CODES.R)
public class InlineAutofillUtils {

    private static int toPixel(int dp, Context context) {
        return (int) TypedValue.applyDimension(COMPLEX_UNIT_DIP, dp,
                context.getResources().getDisplayMetrics());
    }

    private static int getHeight(Context context) {
        return context.getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
    }

    public static InlineSuggestionsRequest createInlineSuggestionRequest(Context context) {

        final Colors colors = Settings.getInstance().getCurrent().mColors;

        ColorType colorType;

        // if key borders are disabled then the background of the chip is not visible with AUTOFILL_BACKGROUND_CHIP
        if (DeviceProtectedUtils.getSharedPreferences(context).getBoolean(Settings.PREF_THEME_KEY_BORDERS, true)){
            colorType = ColorType.AUTOFILL_BACKGROUND_CHIP;
        } else {
            colorType = ColorType.BACKGROUND;
        }

        UiVersions.StylesBuilder stylesBuilder = UiVersions.newStylesBuilder();
        @SuppressLint("RestrictedApi") InlineSuggestionUi.Style style = InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                        new ViewStyle.Builder()
                                .setBackground(
                                        Icon.createWithResource(context,
                                                androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background)
                                                .setTint(colors.get(colorType)))
                                .setPadding(0, 0, 0, 0)
                                .build())
                .setChipStyle(
                        new ViewStyle.Builder()
                                .setBackground(
                                        Icon.createWithResource(context,
                                                androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background)
                                                .setTint(colors.get(colorType)))
                                .build())
                .setStartIconStyle(new ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
                .setTitleStyle(
                        new TextViewStyle.Builder()
                                .setLayoutMargin(toPixel(4, context), 0, toPixel(4, context), 0)
                                .setTextColor(colors.get(ColorType.KEY_TEXT))
                                .setTextSize(12)
                                .build())
                .setSubtitleStyle(
                        new TextViewStyle.Builder()
                                .setLayoutMargin(0, 0, toPixel(4, context), 0)
                                .setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
                                .setTextSize(10)
                                .build())
                .setEndIconStyle(new ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
                .build();
        stylesBuilder.addStyle(style);

        Bundle stylesBundle = stylesBuilder.build();

        Size min = new Size(100, getHeight(context));
        Size max = new Size(740, getHeight(context));

        final ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());

        return new InlineSuggestionsRequest.Builder(presentationSpecs)
                .setMaxSuggestionCount(6)
                .build();
    }

    public static HorizontalScrollView createView(List<InlineSuggestion> inlineSuggestions, Context context) {

        final int totalSuggestionsCount = inlineSuggestions.size();

        // A container to hold all views
        LinearLayout container = new LinearLayout(context);

        for (int i = 0; i < totalSuggestionsCount; i++) {
            final InlineSuggestion inlineSuggestion = inlineSuggestions.get(i);

            inlineSuggestion.inflate(context, new Size(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT), context.getMainExecutor(), (view) -> {
                if (view != null)
                    container.addView(view);
            });
        }

        HorizontalScrollView view = new HorizontalScrollView(context);
        view.setLayoutParams(new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        view.setHorizontalScrollBarEnabled(false);

        view.addView(container);

        return view;
    }
}
