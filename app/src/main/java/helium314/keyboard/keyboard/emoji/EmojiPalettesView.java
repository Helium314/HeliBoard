/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard.emoji;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.keyboard.KeyboardId;
import helium314.keyboard.keyboard.KeyboardLayoutSet;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.keyboard.KeyboardView;
import helium314.keyboard.keyboard.MainKeyboardView;
import helium314.keyboard.keyboard.PointerTracker;
import helium314.keyboard.keyboard.internal.KeyDrawParams;
import helium314.keyboard.keyboard.internal.KeyVisualAttributes;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.AudioAndHapticFeedbackManager;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.RichInputMethodSubtype;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.utils.ResourceUtils;

import org.jetbrains.annotations.NotNull;

import static helium314.keyboard.latin.common.Constants.NOT_A_COORDINATE;

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
        implements View.OnClickListener, OnKeyEventListener {
    private boolean initialized = false;
    // keep the indicator in case emoji view is changed to tabs / viewpager
    private final boolean mCategoryIndicatorEnabled;
    private final int mCategoryIndicatorDrawableResId;
    private final int mCategoryIndicatorBackgroundResId;
    private final int mCategoryPageIndicatorColor;
    private final Colors mColors;
    private EmojiPalettesAdapter mEmojiPalettesAdapter;
    private final EmojiLayoutParams mEmojiLayoutParams;
    private final LinearLayoutManager mEmojiLayoutManager;

    private LinearLayout mTabStrip;
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
        mColors = Settings.getInstance().getCurrent().mColors;
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
        mCategoryPageIndicatorColor = emojiPalettesViewAttr.getColor( // todo: remove this and related attr
                R.styleable.EmojiPalettesView_categoryPageIndicatorColor, 0);
        emojiPalettesViewAttr.recycle();
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

    // todo (maybe): bring back the holo indicator thing?
    //  just some 2 dp high strip
    //  would probably need a vertical linear layout
    //  better not, would complicate stuff again
    //  when decided to definitely not bring it back:
    //   remove mCategoryIndicatorEnabled, mCategoryIndicatorDrawableResId, mCategoryIndicatorBackgroundResId
    //   and the attrs categoryIndicatorDrawable, categoryIndicatorEnabled, categoryIndicatorBackground (and the connected drawables)
    private void addTab(final LinearLayout host, final int categoryId) {
        final ImageView iconView = new ImageView(getContext());
        mColors.setBackground(iconView, ColorType.STRIP_BACKGROUND);
        mColors.setColor(iconView, ColorType.EMOJI_CATEGORY);
        iconView.setScaleType(ImageView.ScaleType.CENTER);
        iconView.setImageResource(mEmojiCategory.getCategoryTabIcon(categoryId));
        iconView.setContentDescription(mEmojiCategory.getAccessibilityDescription(categoryId));
        iconView.setTag((long) categoryId); // use long for simple difference to int used for key codes
        host.addView(iconView);
        iconView.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        iconView.setOnClickListener(this);
    }

    @SuppressLint("ClickableViewAccessibility")
    public void initialize() { // needs to be delayed for access to EmojiTabStrip, which is not a child of this view
        if (initialized) return;
        mEmojiCategory.initialize();
        mTabStrip = (LinearLayout) KeyboardSwitcher.getInstance().getEmojiTabStrip();
        for (final EmojiCategory.CategoryProperties properties : mEmojiCategory.getShownCategories()) {
            addTab(mTabStrip, properties.mCategoryId);
        }
//        mTabStrip.setOnTabChangedListener(this);  // now onClickListener
/*        final TabWidget tabWidget = mTabStrip.getTabWidget();
        tabWidget.setStripEnabled(mCategoryIndicatorEnabled);
        if (mCategoryIndicatorEnabled) {
            // On TabWidget's strip, what looks like an indicator is actually a background.
            // And what looks like a background are actually left and right drawables.
            tabWidget.setBackgroundResource(mCategoryIndicatorDrawableResId);
            tabWidget.setLeftStripDrawable(mCategoryIndicatorBackgroundResId);
            tabWidget.setRightStripDrawable(mCategoryIndicatorBackgroundResId);
            tabWidget.setBackgroundColor(mColors.get(ColorType.EMOJI_CATEGORY_SELECTED));
        }
*/
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
        mEmojiLayoutParams.setCategoryPageIdViewProperties(mEmojiCategoryPageIndicatorView);

        setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), mEmojiCategory.getCurrentCategoryPageId(), true);

        // {@link #mAlphabetKeyLeft} and spaceKey depend on
        // {@link View.OnClickListener} as well as {@link View.OnTouchListener}.
        // {@link View.OnTouchListener} is used as the trigger of key-press, while
        // {@link View.OnClickListener} is used as the trigger of key-release which does not occur
        // if the event is canceled by moving off the finger from the view.
        // The text on alphabet keys are set at
        // {@link #startEmojiPalettes(String,int,float,Typeface)}.

        mEmojiCategoryPageIndicatorView.setColors(mColors.get(ColorType.EMOJI_CATEGORY_SELECTED), mColors.get(ColorType.STRIP_BACKGROUND));
        initialized = true;
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through {@link android.view.View.OnClickListener}
     * interface to handle non-canceled touch-up events from View-based elements such as the space
     * bar.
     */
    @Override
    public void onClick(View v) {
        final Object tag = v.getTag();
        if (tag instanceof Long) {
            AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this);
            final int categoryId = ((Long) tag).intValue();
            if (categoryId != mEmojiCategory.getCurrentCategoryId()) {
                setCurrentCategoryAndPageId(categoryId, 0, false);
                updateEmojiCategoryPageIdView();
            }
        }
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link helium314.keyboard.keyboard.emoji.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     */
    @Override
    public void onPressKey(final Key key) {
        final int code = key.getCode();
        mKeyboardActionListener.onPressKey(code, 0, true);
    }

    /**
     * Called from {@link EmojiPageKeyboardView} through
     * {@link helium314.keyboard.keyboard.emoji.OnKeyEventListener}
     * interface to handle touch events from non-View-based elements such as Emoji buttons.
     * This may be called without any prior call to {@link OnKeyEventListener#onPressKey(Key)}.
     */
    @Override
    public void onReleaseKey(final Key key) {
        mEmojiPalettesAdapter.addRecentKey(key);
        final int code = key.getCode();
        if (code == KeyCode.MULTIPLE_CODE_POINTS) {
            mKeyboardActionListener.onTextInput(key.getOutputText());
        } else {
            mKeyboardActionListener.onCodeInput(code, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
        }
        mKeyboardActionListener.onReleaseKey(code, false);
        if (Settings.getInstance().getCurrent().mAlphaAfterEmojiInEmojiView)
            mKeyboardActionListener.onCodeInput(KeyCode.ALPHA, NOT_A_COORDINATE, NOT_A_COORDINATE, false);
    }

    public void setHardwareAcceleratedDrawingEnabled(final boolean enabled) {
        if (!enabled) return;
        // TODO: Should use LAYER_TYPE_SOFTWARE when hardware acceleration is off?
        setLayerType(LAYER_TYPE_HARDWARE, null);
    }

    public void startEmojiPalettes(final KeyVisualAttributes keyVisualAttr, final EditorInfo editorInfo) {
        initialize();

        setupBottomRowKeyboard(editorInfo);
        final KeyDrawParams params = new KeyDrawParams();
        params.updateParams(mEmojiLayoutParams.getBottomRowKeyboardHeight(), keyVisualAttr);
        if (mEmojiRecyclerView.getAdapter() == null) {
            mEmojiRecyclerView.setAdapter(mEmojiPalettesAdapter);
            setCurrentCategoryAndPageId(mEmojiCategory.getCurrentCategoryId(), mEmojiCategory.getCurrentCategoryPageId(), true);
        }
    }

    private void setupBottomRowKeyboard(final EditorInfo editorInfo) {
        MainKeyboardView keyboardView = findViewById(R.id.bottom_row_keyboard);
        PointerTracker.switchTo(keyboardView);
        final KeyboardLayoutSet kls = KeyboardLayoutSet.Builder.buildEmojiClipBottomRow(getContext(), editorInfo);
        final Keyboard keyboard = kls.getKeyboard(KeyboardId.ELEMENT_EMOJI_BOTTOM_ROW);
        keyboardView.setKeyboard(keyboard);
    }

    public void stopEmojiPalettes() {
        if (!initialized) return;
        mEmojiPalettesAdapter.releaseCurrentKey(true);
        mEmojiPalettesAdapter.flushPendingRecentKeys();
        mEmojiRecyclerView.setAdapter(null);
    }

    public void setKeyboardActionListener(final KeyboardActionListener listener) {
        mKeyboardActionListener = listener;
    }

    private void updateEmojiCategoryPageIdView() {
        if (mEmojiCategoryPageIndicatorView == null) {
            return;
        }
        mEmojiCategoryPageIndicatorView.setCategoryPageId(
                mEmojiCategory.getCurrentCategoryPageCount(),
                mEmojiCategory.getCurrentCategoryPageId(), 0.0f);
    }

    private void setCurrentCategoryAndPageId(final int categoryId, final int categoryPageId, final boolean force) {
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

        final View old = mTabStrip.findViewWithTag((long) oldCategoryId);
        final View current = mTabStrip.findViewWithTag((long) categoryId);

        if (old instanceof ImageView)
            Settings.getInstance().getCurrent().mColors.setColor((ImageView) old, ColorType.EMOJI_CATEGORY);
        if (current instanceof ImageView)
            Settings.getInstance().getCurrent().mColors.setColor((ImageView) current, ColorType.EMOJI_CATEGORY_SELECTED);
    }

    public void clearKeyboardCache() {
        mEmojiCategory.clearKeyboardCache();
    }
}