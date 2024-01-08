/*
 * Copyright (C) 2019 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */

package org.dslul.openboard.inputmethod.latin.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.Size;
import android.view.ViewGroup;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.UiVersions.StylesBuilder;
import androidx.autofill.inline.common.ImageViewStyle;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import androidx.autofill.inline.v1.InlineSuggestionUi.Style;

import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.ColorType;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.settings.Settings;

import java.util.ArrayList;
import java.util.List;

@RequiresApi(api = Build.VERSION_CODES.R)
public class InlineAutofillUtils {

    public static InlineSuggestionsRequest createInlineSuggestionRequest(Context context) {

        final Colors colors = Settings.getInstance().getCurrent().mColors;

        StylesBuilder stylesBuilder = UiVersions.newStylesBuilder();
        @SuppressLint("RestrictedApi") Style style = InlineSuggestionUi.newStyleBuilder()
                .setSingleIconChipStyle(
                        new ViewStyle.Builder()
                                .setBackground(
                                        Icon.createWithResource(context,
                                                androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background)
                                                .setTint(colors.get(ColorType.AUTOFILL_BACKGROUND_CHIP)))
                                .setPadding(0, 0, 0, 0)
                                .build())
                .setChipStyle(
                        new ViewStyle.Builder()
                                .setBackground(
                                        Icon.createWithResource(context,
                                                androidx.autofill.R.drawable.autofill_inline_suggestion_chip_background)
                                                .setTint(colors.get(ColorType.AUTOFILL_BACKGROUND_CHIP)))
                                .build())
                .setStartIconStyle(new ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
                .setTitleStyle(
                        new TextViewStyle.Builder()
                                .setTextColor(colors.get(ColorType.KEY_TEXT))
                                .setTextSize(12)
                                .build())
                .setSubtitleStyle(
                        new TextViewStyle.Builder()
                                .setTextColor(colors.get(ColorType.KEY_HINT_TEXT))
                                .setTextSize(10)
                                .build())
                .setEndIconStyle(new ImageViewStyle.Builder().setLayoutMargin(0, 0, 0, 0).build())
                .build();
        stylesBuilder.addStyle(style);
        Bundle stylesBundle = stylesBuilder.build();

        final int height = context.getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_height);
        final Size min = new Size(100, height);
        final Size max = new Size(740, height);

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

        LinearLayout container = new LinearLayout(context);

        for (int i = 0; i < totalSuggestionsCount; i++) {
            final InlineSuggestion inlineSuggestion = inlineSuggestions.get(i);

            inlineSuggestion.inflate(context, new Size(ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT), context.getMainExecutor(), (view) -> {
                if (view != null)
                    container.addView(view);
            });
        }

        HorizontalScrollView inlineSuggestionView = new HorizontalScrollView(context);
        inlineSuggestionView.setHorizontalScrollBarEnabled(false);
        inlineSuggestionView.addView(container);

        return inlineSuggestionView;
    }
}
