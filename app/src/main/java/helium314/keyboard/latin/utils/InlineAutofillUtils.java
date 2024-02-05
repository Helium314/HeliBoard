/*
 * Copyright (C) 2019 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */

package helium314.keyboard.latin.utils;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.util.AttributeSet;
import android.util.Size;
import android.view.Choreographer;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.inputmethod.InlineSuggestion;
import android.view.inputmethod.InlineSuggestionsRequest;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.inline.InlineContentView;
import android.widget.inline.InlinePresentationSpec;

import androidx.annotation.AttrRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.autofill.inline.UiVersions;
import androidx.autofill.inline.UiVersions.StylesBuilder;
import androidx.autofill.inline.common.ImageViewStyle;
import androidx.autofill.inline.common.TextViewStyle;
import androidx.autofill.inline.common.ViewStyle;
import androidx.autofill.inline.v1.InlineSuggestionUi;
import androidx.autofill.inline.v1.InlineSuggestionUi.Style;

import java.util.ArrayList;
import java.util.List;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.settings.Settings;

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

        // Three presentationSpecs are required for some password managers
        final ArrayList<InlinePresentationSpec> presentationSpecs = new ArrayList<>();
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());
        presentationSpecs.add(new InlinePresentationSpec.Builder(min, max).setStyle(stylesBundle).build());

        return new InlineSuggestionsRequest.Builder(presentationSpecs)
                .setMaxSuggestionCount(6)
                .build();
    }

    public static InlineContentClipView createView(List<InlineSuggestion> inlineSuggestions, Context context) {
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
        inlineSuggestionView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        inlineSuggestionView.addView(container);

        InlineContentClipView mScrollableSuggestionsClip = new InlineContentClipView(context);
        mScrollableSuggestionsClip.addView(inlineSuggestionView);

        return mScrollableSuggestionsClip;
    }

    /**
     * This class is a container for showing {@link InlineContentView}s for cases
     * where you want to ensure they appear only in a given area in your app. An
     * example is having a scrollable list of items. Note that without this container
     * the InlineContentViews' surfaces would cover parts of your app as these surfaces
     * are owned by another process and always appearing on top of your app.
     */
    @RequiresApi(api = Build.VERSION_CODES.R)
    private static class InlineContentClipView extends FrameLayout {
        @NonNull
        private final ViewTreeObserver.OnDrawListener mOnDrawListener =
                this::clipDescendantInlineContentViews;
        @NonNull
        private final Rect mParentBounds = new Rect();
        @NonNull
        private final Rect mContentBounds = new Rect();
        @NonNull
        private final SurfaceView mBackgroundView;
        private int mBackgroundColor;
        public InlineContentClipView(@NonNull Context context) {
            this(context, /*attrs*/ null);
        }
        public InlineContentClipView(@NonNull Context context, @Nullable AttributeSet attrs) {
            this(context, attrs, /*defStyleAttr*/ 0);
        }
        public InlineContentClipView(@NonNull Context context, @Nullable AttributeSet attrs,
                                     @AttrRes int defStyleAttr) {
            super(context, attrs, defStyleAttr);
            mBackgroundView = new SurfaceView(context);
            mBackgroundView.setZOrderOnTop(true);
            mBackgroundView.getHolder().setFormat(PixelFormat.TRANSPARENT);
            mBackgroundView.setLayoutParams(new ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT));
            mBackgroundView.getHolder().addCallback(new SurfaceHolder.Callback() {
                @Override
                public void surfaceCreated(@NonNull SurfaceHolder holder) {
                    drawBackgroundColorIfReady();
                }
                @Override
                public void surfaceChanged(@NonNull SurfaceHolder holder, int format, int width,
                                           int height) { /*do nothing*/ }
                @Override
                public void surfaceDestroyed(@NonNull SurfaceHolder holder) {
                    /*do nothing*/
                }
            });
            addView(mBackgroundView);
        }
        @Override
        protected void onAttachedToWindow() {
            super.onAttachedToWindow();
            getViewTreeObserver().addOnDrawListener(mOnDrawListener);
        }
        @Override
        protected void onDetachedFromWindow() {
            super.onDetachedFromWindow();
            getViewTreeObserver().removeOnDrawListener(mOnDrawListener);
        }
        @Override
        public void setBackgroundColor(int color) {
            mBackgroundColor = color;
            Choreographer.getInstance().postFrameCallback((frameTimeNanos) ->
                    drawBackgroundColorIfReady());
        }
        private void drawBackgroundColorIfReady() {
            final Surface surface = mBackgroundView.getHolder().getSurface();
            if (surface.isValid()) {
                final Canvas canvas = surface.lockCanvas(null);
                try {
                    canvas.drawColor(mBackgroundColor);
                } finally {
                    surface.unlockCanvasAndPost(canvas);
                }
            }
        }

        private void clipDescendantInlineContentViews() {
            mParentBounds.right = getWidth();
            mParentBounds.bottom = getHeight();
            clipDescendantInlineContentViews(this);
        }
        private void clipDescendantInlineContentViews(@Nullable View root) {
            if (root == null) {
                return;
            }
            if (root instanceof InlineContentView inlineContentView) {
                mContentBounds.set(mParentBounds);
                offsetRectIntoDescendantCoords(inlineContentView, mContentBounds);
                inlineContentView.setClipBounds(mContentBounds);
                return;
            }
            if (root instanceof ViewGroup rootGroup) {
                final int childCount = rootGroup.getChildCount();
                for (int i = 0; i < childCount; i++) {
                    final View child = rootGroup.getChildAt(i);
                    clipDescendantInlineContentViews(child);
                }
            }
        }
    }

}