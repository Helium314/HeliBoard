/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.suggestions;

import static android.app.PendingIntent.getActivity;
import static androidx.core.app.ActivityCompat.startActivityForResult;
import static helium314.keyboard.latin.utils.ToolbarUtilsKt.*;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.KeyguardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.os.Build;
import android.os.Bundle;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.view.accessibility.AccessibilityEvent;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.ExtractedTextRequest;
import android.view.inputmethod.InputConnection;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import helium314.keyboard.AIEngine.SharedViewModel;
import helium314.keyboard.AIEngine.SummarizeViewModel;
import helium314.keyboard.AIEngine.SummarizeViewModelFactory;
import helium314.keyboard.accessibility.AccessibilityUtils;
import helium314.keyboard.gemini.GeminiClient;
import helium314.keyboard.keyboard.Key;
import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.keyboard.KeyboardSwitcher;
import helium314.keyboard.keyboard.MainKeyboardView;
import helium314.keyboard.keyboard.PopupKeysPanel;
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode;
import helium314.keyboard.latin.AudioAndHapticFeedbackManager;
import helium314.keyboard.latin.Dictionary;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.RichInputConnection;
import helium314.keyboard.latin.SuggestedWords;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.common.ColorType;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.define.DebugFlags;
import helium314.keyboard.latin.settings.DebugSettings;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.settings.SettingsValues;
import helium314.keyboard.latin.setup.MainActivity;
import helium314.keyboard.latin.suggestions.PopupSuggestionsView.MoreSuggestionsListener;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.ToolbarKey;
import helium314.keyboard.latin.utils.ToolbarUtilsKt;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.content.res.AppCompatResources;
import androidx.core.app.ActivityCompat;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.Observer;
import androidx.lifecycle.ViewModelProvider;

import com.google.ai.client.generativeai.GenerativeModel;

public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener , SummarizeTextProvider, RecognitionListener {

    private SharedViewModel mSharedViewModel;

    private Key mCurrenteKey;

    @NonNull
    @Override
    public String getSummarizeText() {
        return "";
    }

    @Override
    public void setSummarizeText(@NonNull String text) {

    }

    @Override
    public void onReadyForSpeech(Bundle params) {
        Log.e("RecognitionListener", "onReadyForSpeech $params");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.e("RecognitionListener", "onBeginningOfSpeech");
    }

    @Override
    public void onRmsChanged(float rmsdB) {

    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.e("RecognitionListener", "onBufferReceived $buffer");

    }

    @Override
    public void onEndOfSpeech() {
        Log.e("RecognitionListener", "onEndOfSpeech");
    }

    @Override
    public void onError(int error) {
        speechRecognizer.cancel();
        if (error == 7 || (error == 5 && !manualStopRecord)) {
            //comment.text = this.getString(R.string.error_7)
            startRecord();
        } else {
            Toast.makeText(getContext(), "Error in speech recognition", Toast.LENGTH_SHORT).show();

        }
        Log.e("RecognitionListener", "onError $error");
    }

    @Override
    public void onResults(Bundle results) {
        ArrayList<String> data = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if (data != null) {
            setSummarizeText(data.get(0));
        }
    }

    @Override
    public void onPartialResults(Bundle partialResults) {

    }

    @Override
    public void onEvent(int eventType, Bundle params) {

    }

//    private String summarizedText = "";
    //private final SummarizeTextProvider summarizeTextProvider;

//    public SuggestionStripView(Context context, SummarizeTextProvider summarizeTextProvider) {
//        super(context);
//        this.summarizeTextProvider = summarizeTextProvider;
//    }



    public interface Listener {
        void pickSuggestionManually(SuggestedWordInfo word);
        void onCodeInput(int primaryCode, int x, int y, boolean isKeyRepeat);
        void removeSuggestion(final String word);
    }

    public static boolean DEBUG_SUGGESTIONS;
    private static final float DEBUG_INFO_TEXT_SIZE_IN_DIP = 6.5f;
    private static final String TAG = SuggestionStripView.class.getSimpleName();

    private final ViewGroup mSuggestionsStrip;
    private final ImageButton mToolbarExpandKey;
    private final Drawable mIncognitoIcon;
    private final Drawable mToolbarArrowIcon;
    private final Drawable mBinIcon;
    private final ViewGroup mToolbar;
    private final View mToolbarContainer;
    private final ViewGroup mPinnedKeys;
    private final GradientDrawable mEnabledToolKeyBackground = new GradientDrawable();
    private final Drawable mDefaultBackground;
    MainKeyboardView mMainKeyboardView;

    Keyboard mKeyboard;

    private final ImageView mIvOscar;
    private final ImageView mSurajVoiceInputIcon;

    private final View mMoreSuggestionsContainer;
    private final PopupSuggestionsView mMoreSuggestionsView;
    private final MoreSuggestions.Builder mMoreSuggestionsBuilder;

    private final ArrayList<TextView> mWordViews = new ArrayList<>();
    private final ArrayList<TextView> mDebugInfoViews = new ArrayList<>();
    private final ArrayList<View> mDividerViews = new ArrayList<>();

    Listener mListener;

    private static KeyboardActionListener sListener = KeyboardActionListener.EMPTY_LISTENER;



    private SuggestedWords mSuggestedWords = SuggestedWords.getEmptyInstance();
    private int mStartIndexOfMoreSuggestions;
    private int mRtl = 1; // 1 if LTR, -1 if RTL

    private final SuggestionStripLayoutHelper mLayoutHelper;
    private final StripVisibilityGroup mStripVisibilityGroup;
    private boolean isExternalSuggestionVisible = false; // Required to disable the more suggestions if other suggestions are visible



    private static class StripVisibilityGroup {
        private final View mSuggestionStripView;
        private final View mSuggestionsStrip;


        public StripVisibilityGroup(final View suggestionStripView,
                final ViewGroup suggestionsStrip) {
            mSuggestionStripView = suggestionStripView;
            mSuggestionsStrip = suggestionsStrip;
            showSuggestionsStrip();
        }

        public void setLayoutDirection(final int layoutDirection) {
            mSuggestionStripView.setLayoutDirection(layoutDirection);
            mSuggestionsStrip.setLayoutDirection(layoutDirection);
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

    @SuppressLint("InflateParams") // does not seem suitable here
    public SuggestionStripView(final Context context, final AttributeSet attrs, final int defStyle) {
        super(context, attrs, defStyle);
        final Colors colors = Settings.getInstance().getCurrent().mColors;
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(context);
        DEBUG_SUGGESTIONS = prefs.getBoolean(DebugSettings.PREF_SHOW_SUGGESTION_INFOS, false);

        final LayoutInflater inflater = LayoutInflater.from(context);
        inflater.inflate(R.layout.suggestions_strip, this);

        mSuggestionsStrip = findViewById(R.id.suggestions_strip);
        mToolbarExpandKey = findViewById(R.id.suggestions_strip_toolbar_key);
        mStripVisibilityGroup = new StripVisibilityGroup(this, mSuggestionsStrip);
        mPinnedKeys = findViewById(R.id.pinned_keys);
        mToolbar = findViewById(R.id.toolbar);
        mToolbarContainer = findViewById(R.id.toolbar_container);
        mIvOscar = findViewById(R.id.iv_oscar_keyboard_ai);
        mSurajVoiceInputIcon = findViewById(R.id.iv_voiceInput);


        for (int pos = 0; pos < SuggestedWords.MAX_SUGGESTIONS; pos++) {
            final TextView word = new TextView(context, null, R.attr.suggestionWordStyle);
            word.setContentDescription(getResources().getString(R.string.spoken_empty_suggestion));
            word.setOnClickListener(this);
            word.setOnLongClickListener(this);
            colors.setBackground(word, ColorType.STRIP_BACKGROUND);
            mWordViews.add(word);
            final View divider = inflater.inflate(R.layout.suggestion_divider, null);
            mDividerViews.add(divider);
            final TextView info = new TextView(context, null, R.attr.suggestionWordStyle);
            info.setTextColor(colors.get(ColorType.KEY_TEXT));
            info.setTextSize(TypedValue.COMPLEX_UNIT_DIP, DEBUG_INFO_TEXT_SIZE_IN_DIP);
            mDebugInfoViews.add(info);
        }

        mLayoutHelper = new SuggestionStripLayoutHelper(context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = mMoreSuggestionsContainer.findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(context, mMoreSuggestionsSlidingListener);

        @SuppressLint("CustomViewStyleable")
        final TypedArray keyboardAttr = context.obtainStyledAttributes(attrs, R.styleable.Keyboard, defStyle, R.style.SuggestionStripView);
        mIncognitoIcon = keyboardAttr.getDrawable(R.styleable.Keyboard_iconIncognitoKey);
        mToolbarArrowIcon = keyboardAttr.getDrawable(R.styleable.Keyboard_iconToolbarKey);
        mBinIcon = keyboardAttr.getDrawable(R.styleable.Keyboard_iconBin);

        final LinearLayout.LayoutParams toolbarKeyLayoutParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        for (final ToolbarKey key : ToolbarUtilsKt.getEnabledToolbarKeys(prefs)) {
            final ImageButton button = createToolbarKey(context, keyboardAttr, key);
            button.setLayoutParams(toolbarKeyLayoutParams);
            setupKey(button, colors);
            mToolbar.addView(button);
        }

        final int toolbarHeight = Math.min(mToolbarExpandKey.getLayoutParams().height, (int) getResources().getDimension(R.dimen.config_suggestions_strip_height));
        mToolbarExpandKey.getLayoutParams().height = toolbarHeight;
        mToolbarExpandKey.getLayoutParams().width = toolbarHeight; // we want it square
        colors.setBackground(mToolbarExpandKey, ColorType.STRIP_BACKGROUND);
        mDefaultBackground = mToolbarExpandKey.getBackground();
        mEnabledToolKeyBackground.setColors(new int[] {colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) | 0xFF000000, Color.TRANSPARENT}); // ignore alpha on accent color
        mEnabledToolKeyBackground.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        mEnabledToolKeyBackground.setGradientRadius(mToolbarExpandKey.getLayoutParams().height / 2f); // nothing else has a usable height at this state

        mToolbarExpandKey.setOnClickListener(this);
        mToolbarExpandKey.setImageDrawable(Settings.getInstance().getCurrent().mIncognitoModeEnabled ? mIncognitoIcon : mToolbarArrowIcon);
        colors.setColor(mToolbarExpandKey, ColorType.TOOL_BAR_EXPAND_KEY);
        mToolbarExpandKey.setBackground(new ShapeDrawable(new OvalShape())); // ShapeDrawable color is black, need src_atop filter
        mToolbarExpandKey.getBackground().setColorFilter(colors.get(ColorType.TOOL_BAR_EXPAND_KEY_BACKGROUND), PorterDuff.Mode.SRC_ATOP);
        mToolbarExpandKey.getLayoutParams().height *= 0.82; // shrink the whole key a little (drawable not affected)
        mToolbarExpandKey.getLayoutParams().width *= 0.82;

        for (final ToolbarKey pinnedKey : ToolbarUtilsKt.getPinnedToolbarKeys(prefs)) {
            final ImageButton button = createToolbarKey(context, keyboardAttr, pinnedKey);
            button.setLayoutParams(toolbarKeyLayoutParams);
            setupKey(button, colors);
            mPinnedKeys.addView(button);
            final View pinnedKeyInToolbar = mToolbar.findViewWithTag(pinnedKey);
            if (pinnedKeyInToolbar != null && Settings.getInstance().getCurrent().mQuickPinToolbarKeys)
                pinnedKeyInToolbar.setBackground(mEnabledToolKeyBackground);
        }

        colors.setBackground(this, ColorType.STRIP_BACKGROUND);
        keyboardAttr.recycle();

        mIvOscar.setImageDrawable(getResources().getDrawable(R.drawable.ic_oscar));
        mIvOscar.setOnClickListener(this);
        mSurajVoiceInputIcon.setOnClickListener(this);
    }

    /**
     * A connection back to the input method.
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = inputView.findViewById(R.id.keyboard_view);
    }

    private void updateKeys() {
        final SettingsValues currentSettingsValues = Settings.getInstance().getCurrent();
        final View toolbarVoiceKey = mToolbar.findViewWithTag(ToolbarKey.VOICE);
        if (toolbarVoiceKey != null)
            toolbarVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        final View pinnedVoiceKey = mPinnedKeys.findViewWithTag(ToolbarKey.VOICE);
        if (pinnedVoiceKey != null)
            pinnedVoiceKey.setVisibility(currentSettingsValues.mShowsVoiceInputKey ? VISIBLE : GONE);
        mToolbarExpandKey.setImageDrawable(currentSettingsValues.mIncognitoModeEnabled ? mIncognitoIcon : mToolbarArrowIcon);
        mToolbarExpandKey.setScaleX((mToolbarContainer.getVisibility() != VISIBLE ? 1f : -1f) * mRtl);

        // hide pinned keys if device is locked, and avoid expanding toolbar
        final KeyguardManager km = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
        final boolean hideToolbarKeys = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? km.isDeviceLocked()
                : km.isKeyguardLocked();
        mToolbarExpandKey.setOnClickListener(hideToolbarKeys ? null : this);
        mPinnedKeys.setVisibility(hideToolbarKeys ? GONE : mSuggestionsStrip.getVisibility());
        isExternalSuggestionVisible = false;
    }

    public void setRtl(final boolean isRtlLanguage) {
        final int layoutDirection;
        if (!Settings.getInstance().getCurrent().mVarToolbarDirection)
            layoutDirection = View.LAYOUT_DIRECTION_LOCALE;
        else{
            layoutDirection = isRtlLanguage ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR;
            mRtl = isRtlLanguage ? -1 : 1;
        }
        mStripVisibilityGroup.setLayoutDirection(layoutDirection);
    }

    public void setSuggestions(final SuggestedWords suggestedWords, final boolean isRtlLanguage) {
        clear();
        setRtl(isRtlLanguage);
        updateKeys();
        mSuggestedWords = suggestedWords;
        mStartIndexOfMoreSuggestions = mLayoutHelper.layoutAndReturnStartIndexOfMoreSuggestions(
                getContext(), mSuggestedWords, mSuggestionsStrip, this);
    }

    public void setExternalSuggestionView(final View view) {
        clear();
        isExternalSuggestionVisible = true;
        mSuggestionsStrip.addView(view);
        if (Settings.getInstance().getCurrent().mAutoHideToolbar)
            setToolbarVisibility(false);
    }

    @Override
    public void onVisibilityChanged(@NonNull final View view, final int visibility) {
        super.onVisibilityChanged(view, visibility);
        if (view == this)
            // workaround for a bug with inline suggestions views that just keep showing up otherwise, https://github.com/Helium314/HeliBoard/pull/386
            mSuggestionsStrip.setVisibility(visibility);
    }

    public void setMoreSuggestionsHeight(final int remainingHeight) {
        mLayoutHelper.setMoreSuggestionsHeight(remainingHeight);
    }

    @SuppressLint("ClickableViewAccessibility") // why would "null" need to call View#performClick?
    private void clear() {
        mSuggestionsStrip.removeAllViews();
        if (DEBUG_SUGGESTIONS)
            removeAllDebugInfoViews();
        if (mToolbarContainer.getVisibility() != VISIBLE)
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

    private final PopupKeysPanel.Controller mMoreSuggestionsController =
            new PopupKeysPanel.Controller() {
        @Override
        public void onDismissPopupKeysPanel() {
            mMainKeyboardView.onDismissPopupKeysPanel();
        }

        @Override
        public void onShowPopupKeysPanel(final PopupKeysPanel panel) {
            mMainKeyboardView.onShowPopupKeysPanel(panel);
        }

        @Override
        public void onCancelPopupKeysPanel() {
            dismissMoreSuggestionsPanel();
        }
    };

    public boolean isShowingMoreSuggestionPanel() {
        return mMoreSuggestionsView.isShowingInParent();
    }

    public void dismissMoreSuggestionsPanel() {
        mMoreSuggestionsView.dismissPopupKeysPanel();
    }

    @Override
    public boolean onLongClick(final View view) {
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(Constants.NOT_A_CODE, this);
        if (view.getTag() instanceof ToolbarKey) {
            onLongClickToolKey(view);
            return true;
        }
        if (view instanceof TextView && mWordViews.contains(view)) {
            return onLongClickSuggestion((TextView) view);
        } else return showMoreSuggestions();
    }

    private void onLongClickToolKey(final View view) {
        if (!(view.getTag() instanceof ToolbarKey tag)) return;
        if (view.getParent() == mPinnedKeys || !Settings.getInstance().getCurrent().mQuickPinToolbarKeys) {
            final int longClickCode = getCodeForToolbarKeyLongClick(tag);
            if (longClickCode != KeyCode.UNSPECIFIED) {
                mListener.onCodeInput(longClickCode, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
            }
        } else if (view.getParent() == mToolbar) {
            final View pinnedKeyView = mPinnedKeys.findViewWithTag(tag);
            if (pinnedKeyView == null) {
                addKeyToPinnedKeys(tag);
                mToolbar.findViewWithTag(tag).setBackground(mEnabledToolKeyBackground);
                ToolbarUtilsKt.addPinnedKey(DeviceProtectedUtils.getSharedPreferences(getContext()), tag);
            } else {
                ToolbarUtilsKt.removePinnedKey(DeviceProtectedUtils.getSharedPreferences(getContext()), tag);
                mToolbar.findViewWithTag(tag).setBackground(mDefaultBackground.getConstantState().newDrawable(getResources()));
                mPinnedKeys.removeView(pinnedKeyView);
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility") // no need for View#performClick, we return false mostly anyway
    private boolean onLongClickSuggestion(final TextView wordView) {
        boolean showIcon = true;
        if (wordView.getTag() instanceof Integer) {
            final int index = (int) wordView.getTag();
            if (index < mSuggestedWords.size() && mSuggestedWords.getInfo(index).mSourceDict == Dictionary.DICTIONARY_USER_TYPED)
                showIcon = false;
        }
        if (showIcon) {
            final Drawable icon = mBinIcon;
            Settings.getInstance().getCurrent().mColors.setColor(icon, ColorType.REMOVE_SUGGESTION_ICON);
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
        }
        if (DebugFlags.DEBUG_ENABLED && (isShowingMoreSuggestionPanel() || !showMoreSuggestions())) {
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
        if (isShowingMoreSuggestionPanel()) {
            mMoreSuggestionsView.dismissPopupKeysPanel();
        }
        KeyboardSwitcher.getInstance().showToast(text, true);
    }

    private void removeSuggestion(TextView wordView) {
        final String word = wordView.getText().toString();
        mListener.removeSuggestion(word);
        mMoreSuggestionsView.dismissPopupKeysPanel();
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
        // Show the toolbar if no suggestions are left and the "Auto show toolbar" setting is enabled
        if (mSuggestedWords.isEmpty() && Settings.getInstance().getCurrent().mAutoShowToolbar){
            setToolbarVisibility(true);
        }
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
        mMoreSuggestionsView.showPopupKeysPanel(this, mMoreSuggestionsController, pointX, pointY,
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
        public boolean onScroll(@Nullable MotionEvent down, @NonNull MotionEvent me, float deltaX, float deltaY) {
            if (down == null) return false;
            final float dy = me.getY() - down.getY();
            if (mToolbarContainer.getVisibility() != VISIBLE && deltaY > 0 && dy < 0) {
                return showMoreSuggestions();
            }
            return false;
        }
    };

    @Override
    public boolean onInterceptTouchEvent(final MotionEvent me) {

        // Disable More Suggestions if inline autofill suggestions is visible
        if(isExternalSuggestionVisible) {
            return false;
        }

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
        AudioAndHapticFeedbackManager.getInstance().performHapticAndAudioFeedback(KeyCode.NOT_SPECIFIED, this);
        final Object tag = view.getTag();

        if(view == mIvOscar) {
            Log.d(TAG, "Generating summary...");
            Toast.makeText(getContext(), "Generating summary...", Toast.LENGTH_SHORT).show();
            GeminiClient geminiClient = new GeminiClient();
            GenerativeModel generativeModel = geminiClient.getGeminiFlashModel();
            SummarizeViewModelFactory factory = new SummarizeViewModelFactory(generativeModel);
            SummarizeViewModel viewModel = factory.create(SummarizeViewModel.class);


            //get text from voice input and pass it to summarizeStreaming

            viewModel.summarizeStreaming("Pass actual text from RichInputConnection");


            //mCurrentKey.getOutputText() = "Pass actual text from RichInputConnection";

            //sListener.onTextInput(Constants.CODE_OUTPUT_TEXT, mCurrentKey.getOutputText());

            //Key mCurrentKeyboard = key.getCode();

            //mCurrenteKey.getOutputText() = "Pass actual text from RichInputConnection";
            //mCurrentKey.getOutputText() = "Pass actual text from RichInputConnection";

            //viewModel.summarizeStreaming(mCurrenteKey.getOutputText());

            //assert mCurrentKey.getOutputText() != null;

            //viewModel.summarizeStreaming(mCurrentKey.getOutputText());
            //viewModel.summarizeStreaming("Pass actual text from RichInputConnection");
            Log.d(TAG, "Need to pass text from RichInputConnection");


            // TODO: 06/08/24
            //get the input connection first
            //InputConnection iconn = mMainKeyboardView.onCreateInputConnection(new EditorInfo());
//get the total text first.
            //String full_text=iconn.getExtractedText(new ExtractedTextRequest(),0).text.toString();
//get whole text before the cursor
            //String before_text=iconn.getTextBeforeCursor(full_text.length(),0).toString();

            //Log.d(TAG, "Text before cursor: "+before_text);
            //viewModel.summarizeStreaming(before_text);

            return;
        }
        if(view == mSurajVoiceInputIcon) {
            //speakNow();
            //startRecord();

            if (recordStatus) {
                manualStopRecord = true;
                stopRecord();
            } else {
                manualStopRecord = false;
                startRecord();
            }
        }
        if (tag instanceof ToolbarKey) {
            final int code = getCodeForToolbarKey((ToolbarKey) tag);
            if (code != KeyCode.UNSPECIFIED) {
                Log.d(TAG, "click toolbar key "+tag);
                mListener.onCodeInput(code, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                if (tag == ToolbarKey.INCOGNITO || tag == ToolbarKey.AUTOCORRECT || tag == ToolbarKey.ONE_HANDED) {
                    if (tag == ToolbarKey.INCOGNITO)
                        updateKeys(); // update icon
                    view.setActivated(!view.isActivated());
                }
                return;
            }
        }
        if (view == mToolbarExpandKey) {
            setToolbarVisibility(mToolbarContainer.getVisibility() != VISIBLE);
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
    private static final int REQUEST_CODE_SPEECH_INPUT = 1000;

    private void speakNow() {

        Log.d(TAG, "Voice input clicked");
        Toast.makeText(getContext(), "Testing this is voice input", Toast.LENGTH_SHORT).show();

            Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
            intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
            intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Hi i'm Oscar ai, \\n I'm Listening..\"");

            // Use startActivityForResult or startActivity depending on your needs
            //startActivityForResult(this,intent, REQUEST_CODE_SPEECH_INPUT, null); // Replace REQUEST_CODE with your desired code

    }

    private SpeechRecognizer speechRecognizer;

    private boolean recordStatus = false;
    private boolean manualStopRecord = false;


    private void stopRecord() {
        Toast.makeText(getContext(), "Recording stopped", Toast.LENGTH_SHORT).show();
        //comment.setText(getString(R.string.touch_to_start));
        //micImage.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_mic_none_24));
        recordStatus = false;
        speechRecognizer.stopListening();
    }



    private void startRecord() {
        Toast.makeText(getContext(), "Recording started", Toast.LENGTH_SHORT).show();
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(getContext());
        speechRecognizer.setRecognitionListener(this);
        Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);

        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.US);
        intent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 30000);
        speechRecognizer.startListening(intent);

//        comment.setText(getString(R.string.recording_in_progress));
//        micImage.setImageDrawable(AppCompatResources.getDrawable(this, R.drawable.ic_mic_24));
//        recordStatus = true;

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

    public void setToolbarVisibility(final boolean visible) {
        final KeyguardManager km = (KeyguardManager) getContext().getSystemService(Context.KEYGUARD_SERVICE);
        final boolean locked = Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1
                ? km.isDeviceLocked()
                : km.isKeyguardLocked();
        if (locked) {
            mPinnedKeys.setVisibility(GONE);
            mSuggestionsStrip.setVisibility(VISIBLE);
            mToolbarContainer.setVisibility(GONE);
        } else if (visible) {
            mPinnedKeys.setVisibility(GONE);
            mSuggestionsStrip.setVisibility(GONE);
            mToolbarContainer.setVisibility(VISIBLE);
        } else {
            mToolbarContainer.setVisibility(GONE);
            mSuggestionsStrip.setVisibility(VISIBLE);
            mPinnedKeys.setVisibility(VISIBLE);
        }
        mToolbarExpandKey.setScaleX((visible && !locked ? -1f : 1f) * mRtl);
    }

    private void addKeyToPinnedKeys(final ToolbarKey pinnedKey) {
        final ImageButton original = mToolbar.findViewWithTag(pinnedKey);
        if (original == null) return;
        final ImageButton copy = new ImageButton(getContext(), null, R.attr.suggestionWordStyle);
        copy.setTag(pinnedKey);
        copy.setScaleType(original.getScaleType());
        copy.setScaleX(original.getScaleX());
        copy.setScaleY(original.getScaleY());
        copy.setContentDescription(original.getContentDescription());
        copy.setImageDrawable(original.getDrawable());
        copy.setLayoutParams(original.getLayoutParams());
        copy.setActivated(original.isActivated());
        setupKey(copy, Settings.getInstance().getCurrent().mColors);
        mPinnedKeys.addView(copy);
    }

    private void setupKey(final ImageButton view, final Colors colors) {
        view.setOnClickListener(this);
        view.setOnLongClickListener(this);
        colors.setColor(view, ColorType.TOOL_BAR_KEY);
        colors.setBackground(view, ColorType.STRIP_BACKGROUND);
    }


}