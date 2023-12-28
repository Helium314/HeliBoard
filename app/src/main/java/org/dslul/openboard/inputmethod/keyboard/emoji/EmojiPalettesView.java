/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard.emoji;

import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TabHost;
import android.widget.TabHost.OnTabChangeListener;
import android.widget.TabWidget;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.dslul.openboard.inputmethod.compat.TabHostCompat;
import org.dslul.openboard.inputmethod.keyboard.Key;
import org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener;
import org.dslul.openboard.inputmethod.keyboard.KeyboardLayoutSet;
import org.dslul.openboard.inputmethod.keyboard.KeyboardSwitcher;
import org.dslul.openboard.inputmethod.keyboard.KeyboardView;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyDrawParams;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyVisualAttributes;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet;
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodSubtype;
import org.dslul.openboard.inputmethod.latin.common.ColorType;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;

import org.jetbrains.annotations.NotNull;

import static org.dslul.openboard.inputmethod.latin.common.Constants.NOT_A_COORDINATE;

/**
 * View class to implement Emoji palettes.
 * The Emoji keyboard consists of group of views layout/emoji_palettes_view.
 * <ol>
 * <li> Emoji category tabs.
 * <li> Delete button.
 * <li> Emoji keyboard pages that can be scrolled by swiping horizontally or by selecting a tab.
 * <li> Back to main keyboard button and enter button.
 * </ol>
 * Because of the above reasons, this class doesn't extend {@link KeyboardView}.
 */
public final class EmojiPalettesView extends LinearLayout
        implements OnTabChangeListener, View.OnClickListener, View.OnTouchListener,
        OnKeyEventListener {
    private final int mFunctionalKeyBackgroundId;
    private final Drawable mSpacebarBackground;
    private final boolean mCategoryIndicatorEnabled;
    private final int mCategoryIndicatorDrawableResId;
    private final int mCategoryIndicatorBackgroundResId;
    private final int mCategoryPageIndicatorColor;
    private final Colors mColors;
    private EmojiPalettesAdapter mEmojiPalettesAdapter;
    private final EmojiLayoutParams mEmojiLayoutParams;
    private final DeleteKeyOnTouchListener mDeleteKeyOnTouchListener;
    private final LinearLayoutManager mEmojiLayoutManager;

    private ImageButton mDeleteKey;
    private TextView mAlphabetKeyLeft;
    private View mSpacebar;
    // TODO: Remove this workaround.
    private View mSpacebarIcon;
    private TabHostCompat mTabHost;
    private RecyclerView mEmojiRecyclerView;
    private EmojiCategoryPageIndicatorView mEmojiCategoryPageIndicatorView;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;

    private final EmojiCategory mEmojiCategory;

    private ImageView mCurrentTab = null;

    public EmojiPalettesView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.emojiPalettesViewStyle);
    }

    public EmojiPalettesView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        final TypedArray keyboardViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.KeyboardView, defStyle, R.style.KeyboardView);
        final int keyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_keyBackground, 0);
        mFunctionalKeyBackgroundId = keyboardViewAttr.getResourceId(
                R.styleable.KeyboardView_functionalKeyBackground, keyBackgroundId);
        mColors = Settings.getInstance().getCurrent().mColors;
        mSpacebarBackground = mColors.selectAndColorDrawable(keyboardViewAttr, ColorType.SPACE_BAR_BACKGROUND);
        keyboardViewAttr.recycle();
        final KeyboardLayoutSet.Builder builder = new KeyboardLayoutSet.Builder(context, null);
        final Resources res = context.getResources();
        mEmojiLayoutParams = new EmojiLayoutParams(res);
        builder.setSubtype(RichInputMethodSubtype.getEmojiSubtype());
        builder.setKeyboardGeometry(ResourceUtils.getKeyboardWidth(res, Settings.getInstance().getCurrent()),
                mEmojiLayoutParams.mEmojiKeyboardHeight);
        final KeyboardLayoutSet layoutSet = builder.build();
        final TypedArray emojiPalettesViewAttr = context.obtainStyledAttributes(attrs,
                R.styleable.EmojiPalettesView, defStyle, R.style.EmojiPalettesView);
        mEmojiCategory = new EmojiCategory(DeviceProtectedUtils.getSharedPreferences(context),
                res, layoutSet, emojiPalettesViewAttr);
        mCategoryIndicatorEnabled = emojiPalettesViewAttr.getBoolean(
                R.styleable.EmojiPalettesView_categoryIndicatorEnabled, false);
        mCategoryIndicatorDrawableResId = emojiPalettesViewAttr.getResourceId(
                R.styleable.EmojiPalettesView_categoryIndicatorDrawable, 0);
        mCategoryIndicatorBackgroundResId = emojiPalettesViewAttr.getResourceId(
                R.styleable.EmojiPalettesView_categoryIndicatorBackground, 0);
        mCategoryPageIndicatorColor = emojiPalettesViewAttr.getColor(
                R.styleable.EmojiPalettesView_categoryPageIndicatorColor, 0);
        emojiPalettesViewAttr.recycle();
        mDeleteKeyOnTouchListener = new DeleteKeyOnTouchListener();
        mEmojiLayoutManager = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
    }

    @Override
    protected void onMeasure(final int widthMeasureSpec, final int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        final Resources res = getContext().getResources();
        // The main keyboard expands to the entire this {@link KeyboardView}.
        final int width = ResourceUtils.getKeyboardWidth(res, Settings.getInstance().getCurrent())
                + getPaddingLeft() + getPaddingRight();
        final int height = ResourceUtils.getKeyboardHeight(res, Settings.getInstance().getCurrent())
                + getPaddingTop() + getPaddingBottom();
        setMeasuredDimension(width, height);
    }

    private void addTab(final TabHost host, final int categoryId) {
        final String tabId = EmojiCategory.getCategoryName(categoryId, 0 /* categoryPageId */);
        final TabHost.TabSpec tspec = host.newTabSpec(tabId);
        tspec.setContent(R.id.emoji_keyboard_dummy);
        final ImageView iconView = (ImageView) LayoutInflater.from(getContext()).inflate(
                R.layout.emoji_keyboard_tab_icon, null);
        mColors.setBackground(iconView, ColorType.EMOJI_CATEGORY_BACKGROUND);
        mColors.setColor(iconView, ColorType.EMOJI_CATEGORY);
        iconView.setImageResource(mEmojiCategory.getCategoryTabIcon(categoryId));
        iconView.setContentDescription(mEmojiCategory.getAccessibilityDescription(categoryId));
        tspec.setIndicator(iconView);
        host.addTab(tspec);
    }

    public void initialStart() { // needs to be delayed for access to EmojiTabStrip, which is not a child of this view
        mTabHost = KeyboardSwitcher.getInstance().getEmojiTabStrip().findViewById(R.id.emoji_category_tabhost);
        mTabHost.setup();
        for (final EmojiCategory.CategoryProperties properties : mEmojiCategory.getShownCategories()) {
            addTab(mTabHost, properties.mCategoryId);
        }
        mTabHost.setOnTabChangedListener(this);
        final TabWidget tabWidget = mTabHost.getTabWidget();
        tabWidget.setStripEnabled(mCategoryIndicatorEnabled);
        if (mCategoryIndicatorEnabled) {
            // On TabWidget's strip, what looks like an indicator is actually a background.
            // And what looks like a background are actually left and right drawables.
            tabWidget.setBackgroundResource(mCategoryIndicatorDrawableResId);
            tabWidget.setLeftStripDrawable(mCategoryIndicatorBackgroundResId);
            tabWidget.setRightStripDrawable(mCategoryIndicatorBackgroundResId);
            tabWidget.setBackgroundColor(mColors.get(ColorType.EMOJI_CATEGORY_SELECTED));
        }

        mEmojiPalettesAdapter = new EmojiPalettesAdapter(mEmojiCategory, this);

        mEmojiRecyclerView = findViewById(R.id.emoji_keyboard_list);
        mEmojiRecyclerView.setLayoutManager(mEmojiLayoutManager);
        mEmojiRecyclerView.setAdapter(mEmojiPalettesAdapter);
        mEmojiRecyclerView.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);
                // Ignore this message. Only want the actual page selected.
            }

            @Override
            public void onScrolled(@NonNull @NotNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                mEmojiPalettesAdapter.onPageScrolled();

                final int offset = recyclerView.computeVerticalScrollOffset();
                final int extent = recyclerView.computeVerticalScrollExtent();
                final int range = recyclerView.computeVerticalScrollRange();
                final float percentage = offset / (float) (range - extent);

                final int currentCategorySize = mEmojiCategory.getCurrentCategoryPageCount();
                final int a = (int) (percentage * currentCategorySize);
                final float b = percentage * currentCategorySize - a;
                mEmojiCategoryPageIndicatorView.setCategoryPageId(currentCategorySize, a, b);

                final int firstCompleteVisibleBoard = mEmojiLayoutManager.findFirstCompletelyVisibleItemPosition();
                final int firstVisibleBoard = mEmojiLayoutManager.findFirstVisibleItemPosition();
                mEmojiCategory.setCurrentCategoryPageId(
                        firstCompleteVisibleBoard > 0 ? firstCompleteVisibleBoard : firstVisibleBoard);
            }
        });

        mEmojiRecyclerView.setPersistentDrawingCache(PERSISTENT_NO_CACHE);
        mEmojiLayoutParams.setEmojiListProperties(mEmojiRecyclerView);

        mEmojiCategoryPageIndicatorView = findViewById(R.id.emoji_category_page_id_view);
        mEmojiCategoryPageIndicatorView.setColors(mCategoryPageIndicatorColor, mColors.get(ColorType.EMOJI_CATEGORY_BACKGROUND));
        mEmojiLayoutParams.setCategoryPageIdViewProperties(mEmojiCategoryPageIndicatorView);

        setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), mEmojiCategory.getCurrentCategoryPageId(), true);
        // Enable reselection after the first setCurrentCategoryAndPageId() init call
        mTabHost.setFireOnTabChangeListenerOnReselection(true);

        // deleteKey depends only on OnTouchListener.
        mDeleteKey = findViewById(R.id.key_delete);
        mDeleteKey.setBackgroundResource(mFunctionalKeyBackgroundId);
        mColors.setColor(mDeleteKey, ColorType.KEY_ICON);
        mDeleteKey.setTag(Constants.CODE_DELETE);
        mDeleteKey.setOnTouchListener(mDeleteKeyOnTouchListener);

        // {@link #mAlphabetKeyLeft} and spaceKey depend on
        // {@link View.OnClickListener} as well as {@link View.OnTouchListener}.
        // {@link View.OnTouchListener} is used as the trigger of key-press, while
        // {@link View.OnClickListener} is used as the trigger of key-release which does not occur
        // if the event is canceled by moving off the finger from the view.
        // The text on alphabet keys are set at
        // {@link #startEmojiPalettes(String,int,float,Typeface)}.
        mAlphabetKeyLeft = findViewById(R.id.key_alphabet);
        mAlphabetKeyLeft.setBackgroundResource(mFunctionalKeyBackgroundId);
        mAlphabetKeyLeft.setTag(Constants.CODE_ALPHA_FROM_EMOJI);
        mAlphabetKeyLeft.setOnTouchListener(this);
        mAlphabetKeyLeft.setOnClickListener(this);
        mSpacebar = findViewById(R.id.key_space);
        mSpacebar.setBackground(mSpacebarBackground);
        mSpacebar.setTag(Constants.CODE_SPACE);
        mSpacebar.setOnTouchListener(this);
        mSpacebar.setOnClickListener(this);

        mEmojiLayoutParams.setKeyProperties(mSpacebar);
        mSpacebarIcon = findViewById(R.id.key_space_icon);

        mColors.setBackground(mAlphabetKeyLeft, ColorType.FUNCTIONAL_KEY_BACKGROUND);
        mColors.setBackground(mDeleteKey, ColorType.FUNCTIONAL_KEY_BACKGROUND);
        mColors.setBackground(mSpacebar, ColorType.SPACE_BAR_BACKGROUND);
        mEmojiCategoryPageIndicatorView.setColors(mColors.get(ColorType.EMOJI_CATEGORY_SELECTED), mColors.get(ColorType.EMOJI_CATEGORY_BACKGROUND));
    }

    @Override
    public boolean dispatchTouchEvent(final MotionEvent ev) {
        // Add here to the stack trace to nail down the {@link IllegalArgumentException} exception
        // in MotionEvent that sporadically happens.
        // TODO: Remove this override method once the issue has been addressed.
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public void onTabChanged(final String tabId) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(
                Constants.CODE_UNSPECIFIED, this);
        final int categoryId = mEmojiCategory.getCategoryId(tabId);
        if (categoryId != mEmojiCategory.getCurrentCategoryId()) {
            setCurrentCategoryAndPageId(categoryId, 0, false /* force */);
            updateEmojiCategoryPageIdView();
        }
        if (mCurrentTab != null)
            mColors.setColor(mCurrentTab, ColorType.EMOJI_CATEGORY);
        mCurrentTab = (ImageView) mTabHost.getCurrentTabView();
        mColors.setColor(mCurrentTab, ColorType.EMOJI_CATEGORY_SELECTED);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link android.view.View.OnTouchListener}
     * interface to handle touch events from View-based elements such as the space bar.
     * Note that this method is used only for observing {@link MotionEvent#ACTION_DOWN} to trigger
     * {@link KeyboardActionListener#onPressKey}. {@link KeyboardActionListener#onReleaseKey} will
     * be covered by {@link #onClick} as long as the event is not canceled.
     */
    @Override
    public boolean onTouch(final View v, final MotionEvent event) {
        if (event.getActionMasked() != MotionEvent.ACTION_DOWN) {
            return false;
        }
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return false;
        }
        final int code = (Integer) tag;
        mKeyboardActionListener.onPressKey(
                code, 0 /* repeatCount */, true /* isSinglePointer */);
        // It's important to return false here. Otherwise, {@link #onClick} and touch-down visual
        // feedback stop working.
        return false;
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link android.view.View.OnClickListener}
     * interface to handle non-canceled touch-up events from View-based elements such as the space
     * bar.
     */
    @Override
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (!(tag instanceof Integer)) {
            return;
        }
        final int code = (Integer) tag;
        mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
        mKeyboardActionListener.onReleaseKey(code, false);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     */
    @Override
    public void onPressKey(final Key key) {
        final int code = key.getCode();
        mKeyboardActionListener.onPressKey(code, 0, true);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link org.dslul.openboard.inputmethod.keyboard.emoji.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     * This may be called without any prior call to {@link OnKeyEventListener#onPressKey(Key)}.
     */
    @Override
    public void onReleaseKey(final Key key) {
        mEmojiPalettesAdapter.addRecentKey(key);
        final int code = key.getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            mKeyboardActionListener.onTextInput(key.getOutputText());
        } else {
            mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
        }
        mKeyboardActionListener.onReleaseKey(code, false);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    private static void setupAlphabetKey(final TextView alphabetKey, final String label,
                                         final KeyDrawParams params) {
        alphabetKey.setText(label);
        alphabetKey.setTextColor(params.mFunctionalTextColor);
        alphabetKey.setTextSize(TypedValue.COMPLEX_UNIT_PX, params.mLabelSize);
        alphabetKey.setTypeface(params.mTypeface);
    }

    public void startEmojiPalettes(final String switchToAlphaLabel,
                                   final KeyVisualAttributes keyVisualAttr,
                                   final KeyboardIconsSet iconSet) {
        final int deleteIconResId = iconSet.getIconResourceId(KeyboardIconsSet.NAME_DELETE_KEY);
        if (deleteIconResId != 0) {
            mDeleteKey.setImageResource(deleteIconResId);
        }
        mEmojiLayoutParams.setActionBarProperties(findViewById(R.id.action_bar));
        final KeyDrawParams params = new KeyDrawParams();
        params.updateParams(mEmojiLayoutParams.getActionBarHeight(), keyVisualAttr);
        setupAlphabetKey(mAlphabetKeyLeft, switchToAlphaLabel, params);
        if (mEmojiRecyclerView.getAdapter() == null) {
            mEmojiRecyclerView.setAdapter(mEmojiPalettesAdapter);
            setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), mEmojiCategory.getCurrentCategoryPageId(), true);
        }
        mColors.setBackground(this, ColorType.EMOJI_BACKGROUND);
    }

    public void stopEmojiPalettes() {
        mEmojiPalettesAdapter.releaseCurrentKey(true);
        mEmojiPalettesAdapter.flushPendingRecentKeys();
        mEmojiRecyclerView.setAdapter(null);
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
        mDeleteKeyOnTouchListener.setKeyboardActionListener(listener);
    }

    private void updateEmojiCategoryPageIdView() {
        if (mEmojiCategoryPageIndicatorView == null) {
            return;
        }
        mEmojiCategoryPageIndicatorView.setCategoryPageId(
                mEmojiCategory.getCurrentCategoryPageCount(),
                mEmojiCategory.getCurrentCategoryPageId(), 0.0f);
    }

    private void setCurrentCategoryAndPageId(final int categoryId, final int categoryPageId,
                            final boolean force) {
        final int oldCategoryId = mEmojiCategory.getCurrentCategoryId();
        final int oldCategoryPageId = mEmojiCategory.getCurrentCategoryPageId();

        if (oldCategoryId == EmojiCategory.ID_RECENTS && categoryId != EmojiCategory.ID_RECENTS) {
            // Needs to save pending updates for recent keys when we get out of the recents
            // category because we don't want to move the recent emojis around while the user
            // is in the recents category.
            mEmojiPalettesAdapter.flushPendingRecentKeys();
        }

        if (force || oldCategoryId != categoryId || oldCategoryPageId != categoryPageId) {
            mEmojiCategory.setCurrentCategoryId(categoryId);
            mEmojiCategory.setCurrentCategoryPageId(categoryPageId);
            mEmojiPalettesAdapter.notifyDataSetChanged();
            mEmojiRecyclerView.scrollToPosition(categoryPageId);
        }

        final int newTabId = mEmojiCategory.getTabIdFromCategoryId(categoryId);
        if (force || mTabHost.getCurrentTab() != newTabId) {
            mTabHost.setCurrentTab(newTabId);
        }
    }

    private static class DeleteKeyOnTouchListener implements OnTouchListener {
        private KeyboardActionListener mKeyboardActionListener =
                KeyboardActionListener.EMPTY_LISTENER;

        public void setKeyboardActionListener(final KeyboardActionListener listener) {
            mKeyboardActionListener = listener;
        }

        @Override
        public boolean onTouch(final View v, final MotionEvent event) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    onTouchDown(v);
                    return true;
                case MotionEvent.ACTION_MOVE:
                    final float x = event.getX();
                    final float y = event.getY();
                    if (x < 0.0f || v.getWidth() < x || y < 0.0f || v.getHeight() < y) {
                        // Stop generating key events once the finger moves away from the view area.
                        onTouchCanceled(v);
                    }
                    return true;
                case MotionEvent.ACTION_CANCEL:
                case MotionEvent.ACTION_UP:
                    onTouchUp(v);
                    return true;
            }
            return false;
        }

        private void onTouchDown(final View v) {
            mKeyboardActionListener.onPressKey(Constants.CODE_DELETE, 0, true);
            v.setPressed(true /* pressed */);
        }

        private void onTouchUp(final View v) {
            mKeyboardActionListener.onCodeInput(Constants.CODE_DELETE, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
            mKeyboardActionListener.onReleaseKey(Constants.CODE_DELETE, false);
            v.setPressed(false /* pressed */);
        }

        private void onTouchCanceled(final View v) {
            v.setPressed(false);
        }
    }

    public void clearKeyboardCache() {
        mEmojiCategory.clearKeyboardCache();
    }
}