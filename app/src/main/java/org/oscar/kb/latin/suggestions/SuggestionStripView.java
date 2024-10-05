package org.oscar.kb.latin.suggestions;

import static androidx.core.content.ContextCompat.getSystemService;
import static org.oscar.kb.latin.utils.ToolbarUtilsKt.createToolbarKey;
import static org.oscar.kb.latin.utils.ToolbarUtilsKt.getCodeForToolbarKey;
import static org.oscar.kb.latin.utils.ToolbarUtilsKt.getCodeForToolbarKeyLongClick;

import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;

import android.annotation.SuppressLint;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
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
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.FirebaseApp;
import com.google.firebase.crashlytics.FirebaseCrashlytics;

import org.oscar.kb.AIEngine.AIOutputEvent;
import org.oscar.kb.AIEngine.OnTextUpdatedListener;
import org.oscar.kb.AIEngine.SummarizeErrorEvent;
import org.oscar.kb.AIEngine.SummarizeViewModel;
import org.oscar.kb.AIEngine.SummarizeViewModelFactory;
import org.oscar.kb.AIEngine.TextUpdatedEvent;
import org.oscar.kb.keyboard.internal.KeyboardIconsSet;
import org.oscar.kb.latin.AudioAndHapticFeedbackManager;
import org.oscar.kb.R;
import org.oscar.kb.accessibility.AccessibilityUtils;
import org.oscar.kb.gemini.GeminiClient;
import org.oscar.kb.keyboard.Key;
import org.oscar.kb.keyboard.Keyboard;
import org.oscar.kb.keyboard.KeyboardActionListener;
import org.oscar.kb.keyboard.KeyboardSwitcher;
import org.oscar.kb.keyboard.MainKeyboardView;
import org.oscar.kb.keyboard.PopupKeysPanel;
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.KeyCode;
import org.oscar.kb.latin.Dictionary;
import org.oscar.kb.latin.LatinIME;
import org.oscar.kb.latin.RichInputConnection;
import org.oscar.kb.latin.SuggestedWords;
import org.oscar.kb.latin.SuggestedWords.SuggestedWordInfo;
import org.oscar.kb.latin.common.ColorType;
import org.oscar.kb.latin.common.Colors;
import org.oscar.kb.latin.common.Constants;
import org.oscar.kb.latin.define.DebugFlags;
import org.oscar.kb.latin.settings.DebugSettings;
import org.oscar.kb.latin.settings.Settings;
import org.oscar.kb.latin.settings.SettingsValues;
import org.oscar.kb.latin.setup.AppDatabase;
import org.oscar.kb.latin.setup.Prompt;
import org.oscar.kb.latin.setup.PromptHistoryViewModel;
import org.oscar.kb.latin.suggestions.PopupSuggestionsView.MoreSuggestionsListener;
import org.oscar.kb.latin.utils.DeviceProtectedUtils;
import org.oscar.kb.latin.utils.Log;
import org.oscar.kb.latin.utils.ToolbarKey;
import org.oscar.kb.latin.utils.ToolbarUtilsKt;


import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;

import com.airbnb.lottie.LottieAnimationView;
import com.google.ai.client.generativeai.GenerativeModel;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.oscar.kb.service.SpeechRecognitionService;


public final class SuggestionStripView extends RelativeLayout implements OnClickListener,
        OnLongClickListener, SummarizeTextProvider, OnTextUpdatedListener
        //,RecognitionListener
{

    LatinIME mLatinIME;
    private FirebaseCrashlytics crashlytics;
    private PromptHistoryViewModel promptViewModel; // Declare the ViewModel

    private Key mCurrenteKey;
    private LinearLayout linearLayout;
    private ImageView mic_suggestion_strip;
    private TextView timerTextView;
    private Handler handler = new Handler(Looper.getMainLooper());
    private int seconds = 0;
    private boolean isRecording = false;
    private Button cancel;
    private Button done;
    private SummarizeViewModel mViewModel;

    //private KeyboardView.OnKeyboardActionListener mOnKeyboardActionListener;

    private KeyboardActionListener mKeyboardActionListener = KeyboardActionListener.EMPTY_LISTENER;


    GeminiClient geminiClient = new GeminiClient(); // Assuming you have a way to create a GeminiClient instance
    GenerativeModel generativeModel = geminiClient.getGeminiFlashModel();


    public TextView getAiOutputTextView() {
        return aiOutput;
    }
    private boolean isCancelled = false;

//    private void saveAITextToDatabase(String aiText) {
//        AppDatabase db = AppDatabase.getDatabase(getContext());
////        long timestamp = System.currentTimeMillis();
//        Prompt aiTextEntity = new Prompt(aiText);
//        new Thread(() -> db.promptDao().insert(aiTextEntity)).start();
//    }

    public void updateText(final String recognizedText) {
        if (isCancelled) {
            return; // Ignore updates if cancelled
        }
        new Handler(Looper.getMainLooper()).post(() -> {
            aiOutput.setText(recognizedText);  // Update UI with AI-corrected text

            // Removing any calls to the Gemini API here

            // Toast aiOutput text
            //saveAITextToDatabase(recognizedText);
            //generateAIText("input"); // Generate AI output and save to DB todo: check the use of this function before uncommenting
            // Your existing code for AI processing

//            GeminiClient geminiClient = new GeminiClient();
//            GenerativeModel generativeModel = geminiClient.getGeminiFlashModel();
//            SummarizeViewModelFactory factory = new SummarizeViewModelFactory(generativeModel);
//            SummarizeViewModel viewModel = factory.create(SummarizeViewModel.class);
//
//            // Optionally post an event if you're using EventBus or similar
//            AIOutputEvent event = new AIOutputEvent(recognizedText);
//            EventBus.getDefault().post(event);
//
//            viewModel.summarizeStreaming(recognizedText);
        });
    }
    private void sendToGeminiAPI(String text) {
        // Your logic to send the recognized text to the Gemini API
        GeminiClient geminiClient = new GeminiClient();
        GenerativeModel generativeModel = geminiClient.getGeminiFlashModel();
        SummarizeViewModelFactory factory = new SummarizeViewModelFactory(generativeModel);
        SummarizeViewModel viewModel = factory.create(SummarizeViewModel.class);

        viewModel.summarizeStreaming(text);
    }

//    public void generateAIText(String inputText) {
//        GeminiClient geminiClient = new GeminiClient();
//        GenerativeModel generativeModel = geminiClient.getGeminiFlashModel();
//        SummarizeViewModelFactory factory = new SummarizeViewModelFactory(generativeModel);
//        SummarizeViewModel viewModel = factory.create(SummarizeViewModel.class);
//
//        viewModel.summarizeStreaming(inputText);
//        viewModel.setOnTextUpdatedListener(outputText -> {
//            // Save the AI-generated text to the database
//            saveAITextToDatabase(outputText);
//        });
//    }

    private String tempRecognizedText = null; // Store recognized text temporarily

    private final BroadcastReceiver speechResultReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String recognizedText = intent != null ? intent.getStringExtra("recognizedText") : null;
            if (recognizedText != null) {
                Log.d("SuggestionStripView", recognizedText);
                // Save the recognized text (user input) to the Room database
                //saveUserTextToDatabase(recognizedText);  // Call the function to save original user input

                // Process AI text
                //updateText(recognizedText);
                tempRecognizedText = recognizedText;

                // Optionally update the UI (if needed)
                aiOutput.setText(recognizedText);
            }
        }
    };


    @Subscribe
    public void onTextUpdated(TextUpdatedEvent event) {

        //lvTextProgress.setVisibility(View.VISIBLE);
        if (event.getText() != null && !event.getText().isEmpty()) {
            // if aiOutput text is not null clear history
            aiOutput.setVisibility(View.GONE);
            aiOutput.setText(event.getText());
            aiOutput.setVisibility(View.GONE);
            //log received text
            Log.d("SuggestionStripView", "onTextUpdated: " + event.getText());
            // Copy the text to clipboard

            // Save the AI-generated text to the database
            //saveUserTextToDatabase(aiOutput.getText().toString());
            Log.d("SuggestionStripViewDB", "AI Output: " + aiOutput.getText().toString());

            ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
            ClipData clip = ClipData.newPlainText("aiOutput", aiOutput.getText().toString());
            clipboard.setPrimaryClip(clip);

            // log the clipboard text
            Log.d("SuggestionStripViewClip", "Clipboard text: " + aiOutput.getText().toString());
            mListener.onCodeInput(KeyCode.CLIPBOARD_PASTE, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);

            // log listener
            Log.d("SuggestionStripViewListener", "onCodeInput: " + KeyCode.CLIPBOARD_PASTE + " " + Constants.SUGGESTION_STRIP_COORDINATE + " " + Constants.SUGGESTION_STRIP_COORDINATE + " " + false);
            // log clipboard paste text
            Log.d("SuggestionStripViewClipPaste", "Clipboard paste text: " + aiOutput.getText().toString());
        }
    }

    private void saveUserTextToDatabase(String aiText) {
        AppDatabase db = AppDatabase.getDatabase(getContext());
        long timestamp = System.currentTimeMillis(); // Get current timestamp
        Prompt userTextEntity = new Prompt(aiText,  timestamp); // Set the type to AI_OUTPUT
        new Thread(() -> db.promptDao().insert(userTextEntity)).start();
    }

    @Subscribe
    public void onSummarizeError(SummarizeErrorEvent event) {
        // Update the UI to show the error message
        aiOutput.setText(event.getErrorMessage());
        aiOutput.setVisibility(View.VISIBLE);
        Log.d("UI", "Error message received: " + event.getErrorMessage());
    }

    public void setAiOutputText(String text) {
        aiOutput.setText(text);
    }

    @NonNull
    @Override
    public String getSummarizeText() {
        return "";
    }

    @Override
    public void setSummarizeText(@NonNull String text) {

    }

    StringBuilder outputBuilder = new StringBuilder();

    @Override
    public void onTextUpdated(@NonNull String text) {
        Log.d("SuggestionStripViewOnTextUpdated", "onTextUpdated: " + text);
        aiOutput.setText(text);

        // Assuming you have access to the recognized text variable
        String recognizedText = tempRecognizedText; // Store the recognized text temporarily

        if (recognizedText != null) {
            //promptViewModel.insert(new Prompt(recognizedText, Prompt.PromptType.USER_INPUT)); // Save both inputs
        }
    }

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

    public final TextView aiOutput;

    //private final ImageView ivOscarVoiceInput;

    private final ImageView ivDelete;

    private final ImageView ivCopy;

    private final LottieAnimationView lvTextProgress;

    //private final LottieAnimationView tvAudioProgress;
    private final ImageView tvAudioProgress;

    private SpeechRecognizer speechRecognizer;
    private AudioManager audioManager;
    private int previousVolume;


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

        EventBus.getDefault().register(this);


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
        aiOutput = findViewById(R.id.ai_output);
        //ivOscarVoiceInput = findViewById(R.id.ivOscarVoiceInput);
        ivDelete = findViewById(R.id.ic_delete);
        ivCopy = findViewById(R.id.ic_copy);
        lvTextProgress = findViewById(R.id.lvTextProgress);
        tvAudioProgress = findViewById(R.id.tvAudioProgress);
        crashlytics = FirebaseCrashlytics.getInstance();
        FirebaseApp.initializeApp(context);
        mic_suggestion_strip = findViewById(R.id.mic_suggest_strip);
        linearLayout = findViewById(R.id.linear_layouted);
        timerTextView = findViewById(R.id.timerTextView);
        cancel = findViewById(R.id.et_cancel);
        done = findViewById(R.id.et_done);
        audioManager = (AudioManager) getContext().getSystemService(Context.AUDIO_SERVICE);

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

        mic_suggestion_strip.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "mic_suggestion_strip clicked");
                ivCopy.setVisibility(View.GONE);
                ivDelete.setVisibility(View.GONE);
                mic_suggestion_strip.setVisibility(View.GONE);
                aiOutput.setVisibility(View.GONE);
                linearLayout.setVisibility(View.VISIBLE);
                startTimer();  // Starts the timer
                startRecord();
                vibrate();
                previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); // Save current volume
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0); // Mute the soun
            }
        });
        cancel.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "mic_suggestion_strip clicked");
                stopTimer();
                linearLayout.setVisibility(View.GONE);
                aiOutput.setVisibility(View.GONE);
                mic_suggestion_strip.setVisibility(View.VISIBLE);
                ivCopy.setVisibility(View.VISIBLE);
                ivDelete.setVisibility(View.VISIBLE);
                stopRecord();
                isCancelled = true;
                tempRecognizedText = null; // Optionally clear the temporary text
                aiOutput.setText(""); // Optionally clear the TextView
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0); // Restore previous volume
            }
        });
        done.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Log.d(TAG, "Done button clicked");
                stopTimer();
                linearLayout.setVisibility(View.GONE);
                mic_suggestion_strip.setVisibility(View.VISIBLE);
                aiOutput.setVisibility(View.VISIBLE);
                ivCopy.setVisibility(View.VISIBLE);
                ivDelete.setVisibility(View.VISIBLE);
                stopRecord();
                isCancelled = false;
                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0); // Restore previous volume

                // Save user input
                if (tempRecognizedText != null) {
                    Log.d("SuggestionStripViewFullText", "Saving recognized text to database: " + tempRecognizedText);
                    saveUserTextToDatabase(tempRecognizedText);  // Call the function to save original user input
                    Log.d("SuggestionStripViewFullText", "Recognized text: " + tempRecognizedText);
                    //updateText(tempRecognizedText); // Now send the text to be updated
                    sendToGeminiAPI(tempRecognizedText); // Send the recognized text to the Gemini API
                } else {
                    Log.d(TAG, "No text to send");
                }
            }
        });

        mLayoutHelper = new SuggestionStripLayoutHelper(context, attrs, defStyle, mWordViews, mDividerViews, mDebugInfoViews);

        mMoreSuggestionsContainer = inflater.inflate(R.layout.more_suggestions, null);
        mMoreSuggestionsView = mMoreSuggestionsContainer.findViewById(R.id.more_suggestions_view);
        mMoreSuggestionsBuilder = new MoreSuggestions.Builder(context, mMoreSuggestionsView);

        final Resources res = context.getResources();
        mMoreSuggestionsModalTolerance = res.getDimensionPixelOffset(
                R.dimen.config_more_suggestions_modal_tolerance);
        mMoreSuggestionsSlidingDetector = new GestureDetector(context, mMoreSuggestionsSlidingListener);

        final KeyboardIconsSet iconsSet = KeyboardIconsSet.Companion.getInstance();
        mIncognitoIcon = iconsSet.getNewDrawable(KeyboardIconsSet.NAME_INCOGNITO_KEY, context);
        mToolbarArrowIcon = iconsSet.getNewDrawable(KeyboardIconsSet.NAME_TOOLBAR_KEY, context);
        mBinIcon = iconsSet.getNewDrawable(KeyboardIconsSet.NAME_BIN, context);

        final LinearLayout.LayoutParams toolbarKeyLayoutParams = new LinearLayout.LayoutParams(
                getResources().getDimensionPixelSize(R.dimen.config_suggestions_strip_edge_key_width),
                LinearLayout.LayoutParams.MATCH_PARENT
        );
        for (final ToolbarKey key : ToolbarUtilsKt.getEnabledToolbarKeys(prefs)) {
            final ImageButton button = createToolbarKey(context, iconsSet, key);
            button.setLayoutParams(toolbarKeyLayoutParams);
            setupKey(button, colors);
            mToolbar.addView(button);
        }

        final int toolbarHeight = Math.min(mToolbarExpandKey.getLayoutParams().height, (int) getResources().getDimension(R.dimen.config_suggestions_strip_height));
        mToolbarExpandKey.getLayoutParams().height = toolbarHeight;
        mToolbarExpandKey.getLayoutParams().width = toolbarHeight; // we want it square
        colors.setBackground(mToolbarExpandKey, ColorType.STRIP_BACKGROUND);
        mDefaultBackground = mToolbarExpandKey.getBackground();
        mEnabledToolKeyBackground.setColors(new int[]{colors.get(ColorType.TOOL_BAR_KEY_ENABLED_BACKGROUND) | 0xFF000000, Color.TRANSPARENT}); // ignore alpha on accent color
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
            final ImageButton button = createToolbarKey(context, iconsSet, pinnedKey);
            button.setLayoutParams(toolbarKeyLayoutParams);
            setupKey(button, colors);
            mPinnedKeys.addView(button);
            final View pinnedKeyInToolbar = mToolbar.findViewWithTag(pinnedKey);
            if (pinnedKeyInToolbar != null && Settings.getInstance().getCurrent().mQuickPinToolbarKeys)
                pinnedKeyInToolbar.setBackground(mEnabledToolKeyBackground);
        }

        colors.setBackground(this, ColorType.STRIP_BACKGROUND);

        mIvOscar.setOnClickListener(this);
        //ivOscarVoiceInput.setOnClickListener(this);
        tvAudioProgress.setOnClickListener(this);
        ivDelete.setOnClickListener(this);
        ivCopy.setOnClickListener(this);

    }

    /**
     * A connection back to the input method.
     */
    public void setListener(final Listener listener, final View inputView) {
        mListener = listener;
        mMainKeyboardView = inputView.findViewById(R.id.keyboard_view);
    }

    private void startTimer() {
        isRecording = true;
        handler.post(new Runnable() {
            @Override
            public void run() {
                if (isRecording) {
                    int minutes = seconds / 60;
                    int secs = seconds % 60;
                    String time = String.format("%02d:%02d", minutes, secs);
                    timerTextView.setText(time);
                    seconds++;
                    handler.postDelayed(this, 1000);  // Update every second
                }
            }
        });
    }

    private void stopTimer() {
        isRecording = false;
        handler.removeCallbacksAndMessages(null);
        seconds = 0;
        timerTextView.setText("00:00");
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
        else {
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
                ((ViewGroup) parent).removeView(debugInfoView);
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

    @SuppressLint("ClickableViewAccessibility")
    // no need for View#performClick, we return false mostly anyway
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
                if (motionEvent.getAction() == MotionEvent.ACTION_UP && downOk.get()) {
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
        for (int i = 0; i < mSuggestedWords.size(); i++) {
            final SuggestedWordInfo info = mSuggestedWords.getInfo(i);
            if (!info.getWord().equals(word))
                sw.add(info);
        }
        ArrayList<SuggestedWordInfo> rs = null;
        if (mSuggestedWords.mRawSuggestions != null) {
            rs = mSuggestedWords.mRawSuggestions;
            for (int i = 0; i < rs.size(); i++) {
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
        if (mSuggestedWords.isEmpty() && Settings.getInstance().getCurrent().mAutoShowToolbar) {
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
                (int) (maxWidth * layoutHelper.mMinMoreSuggestionsWidth),
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
        if (isExternalSuggestionVisible) {
            return false;
        }

        // Detecting sliding up finger to show {@link MoreSuggestionsView}.
        if (!mMoreSuggestionsView.isShowingInParent()) {
            mLastX = (int) me.getX();
            mLastY = (int) me.getY();
            return mMoreSuggestionsSlidingDetector.onTouchEvent(me);
        }
        if (mMoreSuggestionsView.isInModalMode()) {
            return false;
        }

        final int action = me.getAction();
        final int index = me.getActionIndex();
        final int x = (int) me.getX(index);
        final int y = (int) me.getY(index);
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
        final int x = mMoreSuggestionsView.translateX((int) me.getX(index));
        final int y = mMoreSuggestionsView.translateY((int) me.getY(index));
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
        try {
            if (view == mIvOscar) {
                Log.d(TAG, "Generating summary...");
                Toast.makeText(getContext(), "Generating summary...", Toast.LENGTH_SHORT).show();
                GeminiClient geminiClient = new GeminiClient();
                GenerativeModel generativeModel = geminiClient.getGeminiFlashModel();
                SummarizeViewModelFactory factory = new SummarizeViewModelFactory(generativeModel);
                SummarizeViewModel viewModel = factory.create(SummarizeViewModel.class);

                //viewModel.summarizeStreaming("Pass actual text from RichInputConnection");

                //CharSequence text = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0)


                mLatinIME = new LatinIME();

                RichInputConnection inputConnection = new RichInputConnection(mLatinIME);
                CharSequence text = inputConnection.getTextBeforeCursor(Integer.MAX_VALUE, 0);

                if (text != null) {
                    Log.d(TAG, "Text before cursor: " + text);
                    viewModel.summarizeStreaming(text.toString());
                } else {
                    Log.d(TAG, "Text before cursor is null");
                }


                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error in generating summary: " + e.getMessage());
            crashlytics.recordException(e);
        }
        if (view == tvAudioProgress) {
            if (recordStatus) {
                manualStopRecord = true;
                stopRecord(); // Stop and process transcription
            } else if (isInternetAvailable()) {
                manualStopRecord = false;
                startRecord(); // Start listening again
                aiOutput.setText("");
            } else {
                Toast.makeText(this.getContext(), "Oops! Internet connection lost.", Toast.LENGTH_SHORT).show();
            }
            return;
        }
        try {
            if (view == aiOutput) {
                // On Click sent to Keyboard
                mListener.onCodeInput(KeyCode.CLIPBOARD_PASTE, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                return;
            }
        } catch (Exception e) {
            Log.d(TAG, "Error in generating summary: " + e.getMessage());
            crashlytics.recordException(e);
        }

        if (view == ivDelete) {
            mListener.onCodeInput(KeyCode.DELETE, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
            //clear the text in aiOutput
            aiOutput.setText("");
            //hide visibility of aiOutput
            aiOutput.setVisibility(GONE);
            //show auto suggestion text
            return;
        }
        if (view == ivCopy) {
            if (aiOutput.getText().toString().isEmpty()) {
                Toast.makeText(getContext(), "You do not have anything to copy", Toast.LENGTH_SHORT).show();
                return;
            } else {
                Toast.makeText(getContext(), "Text copied to clipboard", Toast.LENGTH_SHORT).show();
                ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
                ClipData clip = ClipData.newPlainText("aiOutput", aiOutput.getText().toString());
                clipboard.setPrimaryClip(clip);
                mListener.onCodeInput(KeyCode.CLIPBOARD_COPY, Constants.SUGGESTION_STRIP_COORDINATE, Constants.SUGGESTION_STRIP_COORDINATE, false);
                //if clipoard is empty show toast you do not have anything to copy or show clipboard has been copy once there been data
                //aiOutput.setVisibility(VISIBLE);
            }
        }
        if (tag instanceof ToolbarKey) {
            final int code = getCodeForToolbarKey((ToolbarKey) tag);
            if (code != KeyCode.UNSPECIFIED) {
                Log.d(TAG, "click toolbar key " + tag);
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


    private boolean recordStatus = false;
    private boolean manualStopRecord = false;
    private StringBuilder transcriptionBuffer = new StringBuilder(); // To accumulate text

    private void stopRecord() {
        try {
            //lvTextProgress.setVisibility(View.GONE);
            //aiOutput.setVisibility(View.VISIBLE);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    // Set aiOutputText to "Processing..."
                    //aiOutput.setText("Processing...");
                    stopSpeechRecognitionService();
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, previousVolume, 0); // Restore previous volume

                    tvAudioProgress.setImageDrawable(getResources().getDrawable(R.drawable.baseline_mic_24));
                    recordStatus = false;
                }
            }, 5000);

        } catch (Exception e) {
            Log.d(TAG, "Error in starting record: " + e.getMessage());
            crashlytics.recordException(e);
        }
    }


    private void startRecord() {
        try {
            aiOutput.setVisibility(View.GONE);
            //lvTextProgress.setVisibility(View.VISIBLE);
            previousVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC); // Save current volume
            audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0); // Mute the sound
            startForegroundService();
            //tvAudioProgress.playAnimation();
            tvAudioProgress.setImageDrawable(getResources().getDrawable(R.drawable.ic_pause));
            transcriptionBuffer.setLength(0);
            Log.d(TAG, "Recording started");
            recordStatus = true;
        } catch (Exception e) {
            Log.d(TAG, "Error in starting record: " + e.getMessage());
            crashlytics.recordException(e);
            aiOutput.setText("Error in starting record: " + e.getMessage());
        }
    }

    private void vibrate() {
        Vibrator vibrator = (Vibrator) getContext().getSystemService(Context.VIBRATOR_SERVICE);
        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            }
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // Register your BroadcastReceiver here
        LocalBroadcastManager.getInstance(getContext()).registerReceiver(
                speechResultReceiver, new IntentFilter("SpeechRecognitionResults")
        );
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        dismissMoreSuggestionsPanel();
        tvAudioProgress.setImageDrawable(getResources().getDrawable(R.drawable.baseline_mic_24));
        LocalBroadcastManager.getInstance(getContext()).unregisterReceiver(speechResultReceiver);
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

    private boolean isInternetAvailable() {
        Context context = getContext();
        if (context == null) {
            return false;
        }
        ConnectivityManager connectivityManager =
                (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (connectivityManager != null) {
            Network network = null;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                network = connectivityManager.getActiveNetwork();
            }
            NetworkCapabilities networkCapabilities = connectivityManager.getNetworkCapabilities(network);
            return networkCapabilities != null &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
        }
        return false;
    }

    private void startForegroundService() {
        Intent intent = new Intent(getContext(), SpeechRecognitionService.class);
        ContextCompat.startForegroundService(getContext(), intent);
    }

    private void stopSpeechRecognitionService() {
        // Create an intent for the service you want to stop
        Intent intent = new Intent(getContext(), SpeechRecognitionService.class);

        // Create a ContextWrapper instance and use stopService from it
        ContextWrapper contextWrapper = new ContextWrapper(getContext());
        contextWrapper.stopService(intent);
    }
}