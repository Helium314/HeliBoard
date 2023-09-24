/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.suggestions;

import android.annotation.SuppressLint;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.widget.ImageButton;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.dslul.openboard.inputmethod.accessibility.AccessibilityUtils;
import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView;
import org.dslul.openboard.inputmethod.keyboard.MoreKeysPanel;
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.SuggestedWords;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.common.BackgroundType;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.settings.Settings;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValues;
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestionsView.MoreSuggestionsListener;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.DialogUtils;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.appcompat.widget.PopupMenu;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener {
    public interface Listener {
        void pickSuggestionManually(SuggestedWordInfo word);
        void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);
        void onTextInput(final String rawText);
        void removeSuggestion(final String word);
        CharSequence getSelection();
    }

    static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.0f;
    private static final String VOICE_KEY_TAG = "voice_key";
    private static final String CLIPBOARD_KEY_TAG = "clipboard_key";
    private static final String SETTINGS_KEY_TAG = "settings_key";
    private static final String SELECT_ALL_KEY_TAG = "select_all_key";
    private static final String ONE_HANDED_KEY_TAG = "one_handed_key";
    private static final String LEFT_KEY_TAG = "left_key";
    private static final String RIGHT_KEY_TAG = "right_key";
    private static final String UP_KEY_TAG = "up_key";
    private static final String DOWN_KEY_TAG = "down_key";

    private final ViewGroup mSuggestionsStrip;
    private final ImageButton mOtherKey;
    private final Drawable mIncognitoIcon;
    private final Drawable mToolbarArrowIcon;
    private final ViewGroup mToolbar;
    private final ViewGroup mPinnedKeys;
    private final GradientDrawable mEnabledToolKeyBackground = new GradientDrawable();
    private final Drawable mDefaultBackground;
    MainKeyboardView mMainKeyboardView;

    private final View mMoreSuggestionsContainer;
    private final MoreSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;
    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;

    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;

        public StripVisibilityGroup(final View suggestionStripView,
                final ViewGroup suggestionsStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final boolean isRtlLanguage) {
            final int layoutDirection = isRtlLanguage ? ViewCompat.LAYOUT_DIRECTION_RTL
                    : ViewCompat.LAYOUT_DIRECTION_LTR;
            ViewCompat.setLayoutDirection(mSuggestionStripView, layoutDirection);
            ViewCompat.setLayoutDirection(mSuggestionsStrip, layoutDirection);
        }

        public void showSuggestionsStrip() {
            mSuggestionsStrip.setVisibility(VISIBLE);
        }

    }

    /**
     * Construct a {@link SuggestionStripView} for showing suggestions to be picked by the user.
     */
    public SuggestionStripView(final Context context, final AttributeSet attrs) {
        this(context, attrs, R.attr.suggestionStripViewStyle);
    }

    public SuggestionStripView(final Context context, final AttributeSet attrs,
            final int defStyle) {
        super(context, attrs, defStyle);
        final Colors colors = Settings.getInstance().getCurrent().mColors;

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = findViewById(R.id.suggestions_strip);
        mOtherKey = findViewById(R.id.suggestions_strip_other_key);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip);
        mPinnedKeys = findViewById(R.id.pinned_keys);
        mToolbar = findViewById(R.id.toolbar);
        final ImageButton voiceKey = findViewById(R.id.suggestions_strip_voice_key);
        final ImageButton clipboardKey = findViewById(R.id.suggestions_strip_clipboard_key);
        final ImageButton selectAllKey = findViewById(R.id.suggestions_strip_select_all_key);
        final ImageButton settingsKey = findViewById(R.id.suggestions_strip_settings_key);
        final ImageButton oneHandedKey = findViewById(R.id.suggestions_strip_one_handed_key);

        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setContentDescription(getResources().getString(R.string.spoken_empty_suggestion));
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            colors.setBackgroundColor(word.getBackground(), BackgroundType.SUGGESTION);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(Color.WHITE);
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(
                context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = mMoreSuggestionsContainer
                .findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(context, mMoreSuggestionsSlidingListener);

        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        mIncognitoIcon = keyboardAttr.getDrawable(R.styleable.Keyboard_iconIncognitoKey);
        voiceKey.setImageDrawable(keyboardAttr.getDrawable(R.styleable.Keyboard_iconShortcutKey));
        clipboardKey.setImageDrawable(keyboardAttr.getDrawable(R.styleable.Keyboard_iconClipboardNormalKey));
        settingsKey.setImageDrawable(keyboardAttr.getDrawable(R.styleable.Keyboard_iconSettingsKey));
        selectAllKey.setImageDrawable(ContextCompat.getDrawable(getContext(), R.drawable.ic_select_all));
        oneHandedKey.setImageDrawable(keyboardAttr.getDrawable(R.styleable.Keyboard_iconStartOneHandedMode));
        keyboardAttr.recycle();

        mToolbarArrowIcon = ContextCompat.getDrawable(context, R.drawable.ic_arrow_right);
        mDefaultBackground = mOtherKey.getBackground();
        colors.setBackgroundColor(mDefaultBackground, BackgroundType.SUGGESTION);
        mEnabledToolKeyBackground.setColors(new int[] {colors.getAccent(), Color.TRANSPARENT});
        mEnabledToolKeyBackground.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        mEnabledToolKeyBackground.setGradientRadius(mOtherKey.getLayoutParams().height / 2f); // nothing else has a usable height at this state

        mOtherKey.setOnClickListener(this);
        mOtherKey.setImageDrawable(Settings.getInstance().getCurrent().mIncognitoModeEnabled ? mIncognitoIcon : mToolbarArrowIcon);
        mOtherKey.setColorFilter(colors.getKeyText()); // maybe different color?
        mOtherKey.setBackground(new ShapeDrawable(new OvalShape())); // ShapeDrawable color is black, need src_atop filter
        mOtherKey.getBackground().setColorFilter(colors.getDoubleAdjustedBackground(), PorterDuff.Mode.SRC_ATOP);
        mOtherKey.getLayoutParams().height *= 0.82; // shrink the whole key a little (drawable not affected)
        mOtherKey.getLayoutParams().width *= 0.82;

        for (int i = 0; i < mToolbar.getChildCount(); i++) {
            setupKey((ImageButton) mToolbar.getChildAt(i), colors);
        }
        for (final String pinnedKey : Settings.getInstance().getCurrent().mPinnedKeys) {
            mToolbar.findViewWithTag(pinnedKey).setBackground(mEnabledToolKeyBackground);
            addKeyToPinnedKeys(pinnedKey, inflater);
        }
        mToolbar.setVisibility(GONE);

        colors.setKeyboardBackground(this);
    }

    /**
     * A connection back to the input method.
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = inputView.findViewById(R.id.keyboard_view);
    }

    public void updateVisibility(final boolean shouldBeVisible, final boolean isFullscreenMode) {
        final int visibility = shouldBeVisible ? VISIBLE : (isFullscreenMode ? GONE : INVISIBLE);
        setVisibility(visibility);
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        mToolbar.findViewWithTag(VOICE_KEY_TAG).setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        final View pinnedVoiceKey = mPinnedKeys.findViewWithTag(VOICE_KEY_TAG);
        if (pinnedVoiceKey != null)
            pinnedVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        mOtherKey.setImageDrawable(currentSettingsValues.mIncognitoModeEnabled ? mIncognitoIcon : mToolbarArrowIcon);
        mOtherKey.setScaleX(currentSettingsValues.mIncognitoModeEnabled || mToolbar.getVisibility() != VISIBLE ? 1f : -1f);
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        mStripVisibilityGroup.setLayoutDirection(isRtlLanguage);
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    @SuppressLint("ClickableViewAccessibility") // why would "null" need to call View#performClick?
    public void clear() {
        mSuggestionsStrip.removeAllViews();
        removeAllDebugInfoViews();
        if (mToolbar.getVisibility() != VISIBLE)
            mStripVisibilityGroup.showSuggestionsStrip();
        dismissMoreSuggestionsPanel();
        for (final TextView word : mWordViews) {
            word.setOnTouchListener(null);
        }
    }

    private void removeAllDebugInfoViews() {
        // The debug info views may be placed as children views of this {@link SuggestionStripView}.
        for (final View debugInfoView : mDebugInfoViews) {
            final ViewParent parent = debugInfoView.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup)parent).removeView(debugInfoView);
            }
        }
    }

    private final MoreSuggestionsListener mMoreSuggestionsListener = new MoreSuggestionsListener() {
        @Override
        public void onSuggestionSelected(final SuggestedWordInfo wordInfo) {
            mListener.pickSuggestionManually(wordInfo);
            dismissMoreSuggestionsPanel();
        }

        @Override
        public void onCancelInput() {
            dismissMoreSuggestionsPanel();
        }
    };

    private final MoreKeysPanel.Controller mMoreSuggestionsController =
            new MoreKeysPanel.Controller() {
        @Override
        public void onDismissMoreKeysPanel() {
            mMainKeyboardView.onDismissMoreKeysPanel();
        }

        @Override
        public void onShowMoreKeysPanel(final MoreKeysPanel panel) {
            mMainKeyboardView.onShowMoreKeysPanel(panel);
        }

        @Override
        public void onCancelMoreKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissMoreKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.NOT_A_CODE, this);
        if (mToolbar.findViewWithTag(view.getTag()) != null) {
            onLongClickToolKey(view);
            return true;
        }
        if (view instanceof TextView && mWordViews.contains(view)) {
            return onLongClickSuggestion((TextView) view);
        } else return showMoreSuggestions();
    }

    private void onLongClickToolKey(final View view) {
        if (CLIPBOARD_KEY_TAG.equals(view.getTag()) && view.getParent() == mPinnedKeys) {
            onLongClickClipboardKey(); // long click pinned clipboard key
        } else if (view.getParent() == mToolbar) {
            final String tag = (String) view.getTag();
            final View pinnedKeyView = mPinnedKeys.findViewWithTag(tag);
            if (pinnedKeyView == null) {
                addKeyToPinnedKeys(tag, LayoutInflater.from(getContext()));
                mToolbar.findViewWithTag(tag).setBackground(mEnabledToolKeyBackground);
                Settings.addPinnedKey(DeviceProtectedUtils.getSharedPreferences(getContext()), tag);
            } else {
                Settings.removePinnedKey(DeviceProtectedUtils.getSharedPreferences(getContext()), tag);
                mToolbar.findViewWithTag(tag).setBackground(mDefaultBackground);
                mPinnedKeys.removeView(pinnedKeyView);
            }
        }
    }

    private void onLongClickClipboardKey() {
        ClipboardManager clipboardManager = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
        ClipData clipData = clipboardManager.getPrimaryClip();
        if (clipData != null && clipData.getItemCount() > 0 && clipData.getItemAt(0) != null) {
            String clipString = clipData.getItemAt(0).coerceToText(getContext()).toString();
            if (clipString.length() == 1) {
                mListener.onTextInput(clipString);
            } else if (clipString.length() > 1) {
                //awkward workaround
                mListener.onTextInput(clipString.substring(0, clipString.length() - 1));
                mListener.onTextInput(clipString.substring(clipString.length() - 1));
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we return false mostly anyway
    private boolean onLongClickSuggestion(final TextView wordView) {
        final Drawable icon = ContextCompat.getDrawable(getContext(), R.drawable.ic_delete);
        icon.setColorFilter(Settings.getInstance().getCurrent().mColors.getKeyTextFilter());
        int w = icon.getIntrinsicWidth();
        int h = icon.getIntrinsicWidth();
        wordView.setCompoundDrawablesWithIntrinsicBounds(icon, null, null, null);
        wordView.setEllipsize(TextUtils.TruncateAt.END);
        AtomicBoolean downOk = new AtomicBoolean(false);
        wordView.setOnTouchListener((view1, motionEvent) -> {
            if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                final float x = motionEvent.getX();
                final float y = motionEvent.getY();
                if (0 < x && x < w && 0 < y && y < h) {
                    removeSuggestion(wordView);
                    wordView.cancelLongPress();
                    wordView.setPressed(false);
                    return true;
                }
            } else if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                final float x = motionEvent.getX();
                final float y = motionEvent.getY();
                if (0 < x && x < w && 0 < y && y < h) {
                    downOk.set(true);
                }
            }
            return false;
        });
        if (BuildConfig.DEBUG && (isShowingMoreSuggestionPanel() || !showMoreSuggestions())) {
            showSourceDict(wordView);
            return true;
        } else return showMoreSuggestions();
    }

    private void showSourceDict(final TextView wordView) {
        final String word = wordView.getText().toString();
        final int index;
        if (wordView.getTag() instanceof Integer) {
            index = (int) wordView.getTag();
        } else return;
        if (index >= mSuggestedWords.size()) return;
        final SuggestedWordInfo info = mSuggestedWords.getInfo(index);
        if (!info.getWord().equals(word)) return;
        final String text = info.mSourceDict.mDictType + ":" + info.mSourceDict.mLocale;
        // apparently toast is not working on some Android versions, probably
        // Android 13 with the notification permission
        // Toast.makeText(getContext(), text, Toast.LENGTH_LONG).show();
        final PopupMenu uglyWorkaround = new PopupMenu(DialogUtils.getPlatformDialogThemeContext(getContext()), wordView);
        uglyWorkaround.getMenu().add(Menu.NONE, 1, Menu.NONE, text);
        uglyWorkaround.show();
    }

    private void removeSuggestion(TextView wordView) {
        final String word = wordView.getText().toString();
        mListener.removeSuggestion(word);
        mMoreSuggestionsView.dismissMoreKeysPanel();
        // show suggestions, but without the removed word
        final ArrayList<SuggestedWordInfo> sw = new ArrayList<>();
        for (int i = 0; i < mSuggestedWords.size(); i ++) {
            final SuggestedWordInfo info = mSuggestedWords.getInfo(i);
            if (!info.getWord().equals(word))
                sw.add(info);
        }
        ArrayList<SuggestedWordInfo> rs = null;
        if (mSuggestedWords.mRawSuggestions != null) {
            rs = mSuggestedWords.mRawSuggestions;
            for (int i = 0; i < rs.size(); i ++) {
                if (rs.get(i).getWord().equals(word)) {
                    rs.remove(i);
                    break;
                }
            }
        }
        // copied code from setSuggestions, but without the Rtl part
        clear();
        mSuggestedWords = new SuggestedWords(sw, rs, mSuggestedWords.getTypedWordInfo(),
                mSuggestedWords.mTypedWordValid, mSuggestedWords.mWillAutoCorrect,
                mSuggestedWords.mIsObsoleteSuggestions, mSuggestedWords.mInputStyle,
                mSuggestedWords.mSequenceNumber);
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, SuggestionStripView.this);
        mStripVisibilityGroup.showSuggestionsStrip();
    }

    boolean showMoreSuggestions() {
        final Keyboard parentKeyboard = mMainKeyboardView.getKeyboard();
        if (parentKeyboard == null) {
            return false;
        }
        final SuggestionStripLayoutHelper layoutHelper = mLayoutHelper;
        if (mSuggestedWords.size() <= mStartIndexOfMoreSuggestions) {
            return false;
        }
        final int stripWidth = getWidth();
        final View container = mMoreSuggestionsContainer;
        final int maxWidth = stripWidth - container.getPaddingLeft() - container.getPaddingRight();
        final MoreSuggestions.Builder builder = mMoreSuggestionsBuilder;
        builder.layout(mSuggestedWords, mStartIndexOfMoreSuggestions, maxWidth,
                (int)(maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
                layoutHelper.getMaxMoreSuggestionsRow(), parentKeyboard);
        mMoreSuggestionsView.setKeyboard(builder.build());
        container.measure(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        final int pointX = stripWidth / 2;
        final int pointY = -layoutHelper.mMoreSuggestionsBottomGap;
        mMoreSuggestionsView.showMoreKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
                mMoreSuggestionsListener);
        mOriginX = mLastX;
        mOriginY = mLastY;
        for (int i = 0; i < mStartIndexOfMoreSuggestions; i++) {
            mWordViews.get(i).setPressed(false);
        }
        return true;
    }

    // Working variables for {@link onInterceptTouchEvent(MotionEvent)} and
    // {@link onTouchEvent(MotionEvent)}.
    private int mLastX;
    private int mLastY;
    private int mOriginX;
    private int mOriginY;
    private final int mMoreSuggestionsModalTolerance;
    private boolean mNeedsToTransformTouchEventToHoverEvent;
    private boolean mIsDispatchingHoverEventToMoreSuggestions;
    private final GestureDetector mMoreSuggestionsSlidingDetector;
    private final GestureDetector.OnGestureListener mMoreSuggestionsSlidingListener =
            new GestureDetector.SimpleOnGestureListener() {
        @Override
        public boolean onScroll(MotionEvent down, MotionEvent me, float deltaX, float deltaY) {
            final float dy = me.getY() - down.getY();
            if (deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {
        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int)me.getX();
            mLastY = (int)me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int)me.getX(index);
        final int y = (int)me.getY(index);
        if (Math.abs(x - mOriginX) >= mMoreSuggestionsModalTolerance
                || mOriginY - y >= mMoreSuggestionsModalTolerance) {
            // Decided to be in the sliding suggestion mode only when the touch point has been moved
            // upward. Further {@link MotionEvent}s will be delivered to
            // {@link #onTouchEvent(MotionEvent)}.
            mNeedsToTransformTouchEventToHoverEvent =
                    AccessibilityUtils.Companion.getInstance().isTouchExplorationEnabled();
            mIsDispatchingHoverEventToMoreSuggestions = false;
            return true;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            // Decided to be in the modal input mode.
            mMoreSuggestionsView.setModalMode();
        }
        return false;
    }

    @Override
    public boolean dispatchPopulateAccessibilityEvent(final AccessibilityEvent event) {
        // Don't populate accessibility event with suggested words and voice key.
        return true;
    }

    @Override
    @SuppressLint("ClickableViewAccessibility") // ok, perform click again, but why?
    public boolean onTouchEvent(final MotionEvent me) {
        if (!mMoreSuggestionsView.isShowingInParent()) {
            // Ignore any touch event while more suggestions panel hasn't been shown.
            // Detecting sliding up is done at {@link #onInterceptTouchEvent}.
            return true;
        }
        // In the sliding input mode. {@link MotionEvent} should be forwarded to
        // {@link MoreSuggestionsView}.
        final int index = me.getActionIndex();
        final int x = mMoreSuggestionsView.translateX((int)me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int)me.getY(index));
        me.setLocation(x, y);
        if (!mNeedsToTransformTouchEventToHoverEvent) {
            mMoreSuggestionsView.onTouchEvent(me);
            return true;
        }
        // In sliding suggestion mode with accessibility mode on, a touch event should be
        // transformed to a hover event.
        final int width = mMoreSuggestionsView.getWidth();
        final int height = mMoreSuggestionsView.getHeight();
        final boolean onMoreSuggestions = (x >= 0 && x < width && y >= 0 && y < height);
        if (!onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Just drop this touch event because dispatching hover event isn't started yet and
            // the touch event isn't on {@link MoreSuggestionsView}.
            return true;
        }
        final int hoverAction;
        if (onMoreSuggestions && !mIsDispatchingHoverEventToMoreSuggestions) {
            // Transform this touch event to a hover enter event and start dispatching a hover
            // event to {@link MoreSuggestionsView}.
            mIsDispatchingHoverEventToMoreSuggestions = true;
            hoverAction = MotionEvent.ACTION_HOVER_ENTER;
        } else if (me.getActionMasked() == MotionEvent.ACTION_UP) {
            // Transform this touch event to a hover exit event and stop dispatching a hover event
            // after this.
            mIsDispatchingHoverEventToMoreSuggestions = false;
            mNeedsToTransformTouchEventToHoverEvent = false;
            hoverAction = MotionEvent.ACTION_HOVER_EXIT;
        } else {
            // Transform this touch event to a hover move event.
            hoverAction = MotionEvent.ACTION_HOVER_MOVE;
        }
        me.setAction(hoverAction);
        mMoreSuggestionsView.onHoverEvent(me);
        return true;
    }

    @Override
    public void onClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.CODE_UNSPECIFIED, this);
        final Object tag = view.getTag();
        if (tag instanceof String) {
            switch ((String) tag) {
                case VOICE_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_SHORTCUT, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case CLIPBOARD_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_CLIPBOARD, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case SELECT_ALL_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_SELECT_ALL, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case ONE_HANDED_KEY_TAG:
                    final boolean oneHandedEnabled = Settings.getInstance().getCurrent().mOneHandedModeEnabled;
                    mListener.onCodeInput(oneHandedEnabled ? Constants.CODE_STOP_ONE_HANDED_MODE : Constants.CODE_START_ONE_HANDED_MODE,
                            Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case SETTINGS_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_SETTINGS, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case LEFT_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_LEFT, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case RIGHT_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_RIGHT, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case UP_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_UP, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
                case DOWN_KEY_TAG:
                    mListener.onCodeInput(Constants.CODE_DOWN, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                    return;
            }
        }
        if (view == mOtherKey) {
            if (mToolbar.getVisibility() == VISIBLE) {
                mToolbar.setVisibility(GONE);
                mSuggestionsStrip.setVisibility(VISIBLE);
                mPinnedKeys.setVisibility(VISIBLE);
            } else {
                mToolbar.setVisibility(VISIBLE);
                mSuggestionsStrip.setVisibility(GONE);
                mPinnedKeys.setVisibility(GONE);
            }
            mOtherKey.setScaleX(mOtherKey.getDrawable() == mIncognitoIcon || mToolbar.getVisibility() != VISIBLE ? 1f : -1f);
        }


        // {@link Integer} tag is set at
        // {@link SuggestionStripLayoutHelper#setupWordViewsTextAndColor(SuggestedWords,int)} and
        // {@link SuggestionStripLayoutHelper#layoutPunctuationSuggestions(SuggestedWords,ViewGroup}
        if (tag instanceof Integer) {
            final int index = (Integer) tag;
            if (index >= mSuggestedWords.size()) {
                return;
            }
            final SuggestedWordInfo wordInfo = mSuggestedWords.getInfo(index);
            mListener.pickSuggestionManually(wordInfo);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
    }

    @Override
    protected void onSizeChanged(final int w, final int h, final int oldw, final int oldh) {
        // Called by the framework when the size is known. Show the important notice if applicable.
        // This may be overriden by showing suggestions later, if applicable.
    }

    private void addKeyToPinnedKeys(final String pinnedKey, final LayoutInflater inflater) {
        final int resId = getKeyLayoutIdForTag(pinnedKey);
        if (resId == 0) return;
        final ImageButton view = (ImageButton) inflater.inflate(resId, null);
        view.setImageDrawable(((ImageButton) mToolbar.findViewWithTag(pinnedKey)).getDrawable());
        view.setLayoutParams(mToolbar.findViewWithTag(pinnedKey).getLayoutParams());
        setupKey(view, Settings.getInstance().getCurrent().mColors);
        mPinnedKeys.addView(view);
    }

    private void setupKey(final ImageButton view, final Colors colors) {
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        view.setColorFilter(colors.getKeyTextFilter());
        colors.setBackgroundColor(view.getBackground(), BackgroundType.SUGGESTION);
    }

    private static int getKeyLayoutIdForTag(final String tag) {
        switch (tag) {
            case VOICE_KEY_TAG:
                return R.layout.suggestions_strip_voice_key;
            case SETTINGS_KEY_TAG:
                return R.layout.suggestions_strip_settings_key;
            case CLIPBOARD_KEY_TAG:
                return R.layout.suggestions_strip_clipboard_key;
            case SELECT_ALL_KEY_TAG:
                return R.layout.suggestions_strip_select_all_key;
            case ONE_HANDED_KEY_TAG:
                return R.layout.suggestions_strip_one_handed_key;
            case LEFT_KEY_TAG:
                return R.layout.suggestions_strip_left_key;
            case RIGHT_KEY_TAG:
                return R.layout.suggestions_strip_right_key;
            case UP_KEY_TAG:
                return R.layout.suggestions_strip_up_key;
            case DOWN_KEY_TAG:
                return R.layout.suggestions_strip_down_key;
        }
        return 0;
    }
}
