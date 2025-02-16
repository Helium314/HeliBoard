/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.TypedValueCompat;

import helium314.keyboard.compat.ConfigurationCompatKt;
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfosKt;
import helium314.keyboard.latin.InputAttributes;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.RichInputMethodManager;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.permissions.PermissionsUtil;
import helium314.keyboard.latin.utils.InputTypeUtils;
import helium314.keyboard.latin.utils.JniUtils;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.PopupKeysUtilsKt;
import helium314.keyboard.latin.utils.ScriptUtils;
import helium314.keyboard.latin.utils.SubtypeSettings;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link helium314.keyboard.latin.utils.RunInLocaleKt}.
 */
// Non-final for testing via mock library.
public class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();
    // "floatMaxValue" and "floatNegativeInfinity" are special marker strings for
    // Float.NEGATIVE_INFINITE and Float.MAX_VALUE. Currently used for auto-correction settings.
    private static final String FLOAT_MAX_VALUE_MARKER_STRING = "floatMaxValue";
    private static final String FLOAT_NEGATIVE_INFINITY_MARKER_STRING = "floatNegativeInfinity";
    public static final float DEFAULT_SIZE_SCALE = 1.0f; // 100%
    public static final float AUTO_CORRECTION_DISABLED_THRESHOLD = Float.MAX_VALUE;

    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
    public final long mDoubleSpacePeriodTimeout;
    // From configuration:
    public final Locale mLocale;
    public final boolean mHasHardwareKeyboard;
    public final int mDisplayOrientation;
    // From preferences
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mVibrateInDndMode;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    public final boolean mShowsVoiceInputKey;
    public final boolean mLanguageSwitchKeyToOtherImes;
    public final boolean mLanguageSwitchKeyToOtherSubtypes;
    private final boolean mShowsLanguageSwitchKey;
    public final boolean mShowsNumberRow;
    public final boolean mLocalizedNumberRow;
    public final boolean mShowNumberRowHints;
    public final boolean mShowsHints;
    public final boolean mShowsPopupHints;
    public final boolean mSpaceForLangChange;
    public final boolean mShowsEmojiKey;
    public final boolean mVarToolbarDirection;
    public final boolean mUsePersonalizedDicts;
    public final boolean mUseDoubleSpacePeriod;
    public final boolean mBlockPotentiallyOffensive;
    public final int mSpaceSwipeHorizontal;
    public final int mSpaceSwipeVertical;
    public final int mLanguageSwipeDistance;
    public final boolean mDeleteSwipeEnabled;
    public final boolean mAutospaceAfterPunctuationEnabled;
    public final boolean mClipboardHistoryEnabled;
    public final long mClipboardHistoryRetentionTime;
    public final boolean mOneHandedModeEnabled;
    public final int mOneHandedModeGravity;
    public final float mOneHandedModeScale;
    public final boolean mNarrowKeyGaps;
    public final int mShowMorePopupKeys;
    public final List<String> mPopupKeyTypes;
    public final List<String> mPopupKeyLabelSources;
    public final List<Locale> mSecondaryLocales;
    public final boolean mBigramPredictionEnabled;// Use bigrams to predict the next word when there is no input for it yet
    public final boolean mCenterSuggestionTextToEnter;
    public final boolean mGestureInputEnabled;
    public final boolean mGestureTrailEnabled;
    public final boolean mGestureFloatingPreviewTextEnabled;
    public final boolean mGestureFloatingPreviewDynamicEnabled;
    public final int mGestureFastTypingCooldown;
    public final int mGestureTrailFadeoutDuration;
    public final boolean mSlidingKeyInputPreviewEnabled;
    public final int mKeyLongpressTimeout;
    public final boolean mEnableEmojiAltPhysicalKey;
    public final boolean mIsSplitKeyboardEnabled;
    public final float mSplitKeyboardSpacerRelativeWidth;
    public final boolean mQuickPinToolbarKeys;
    public final int mScreenMetrics;
    public final boolean mAddToPersonalDictionary;
    public final boolean mUseContactsDictionary;
    public final boolean mCustomNavBarColor;
    public final float mKeyboardHeightScale;
    public final boolean mUrlDetectionEnabled;
    public final float mBottomPaddingScale;
    public final float mSidePaddingScale;
    public final boolean mAutoShowToolbar;
    public final boolean mAutoHideToolbar;
    public final boolean mAlphaAfterEmojiInEmojiView;
    public final boolean mAlphaAfterClipHistoryEntry;
    public final boolean mAlphaAfterSymbolAndSpace;
    public final boolean mRemoveRedundantPopups;
    public final String mSpaceBarText;
    public final float mFontSizeMultiplier;
    public final float mFontSizeMultiplierEmoji;

    // From the input box
    @NonNull
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mKeypressSoundVolume;
    public final boolean mAutoCorrectionEnabledPerUserSettings;
    public final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    public final int mScoreLimitForAutocorrect;
    public final boolean mAutoCorrectShortcuts;
    private final boolean mSuggestionsEnabledPerUserSettings;
    private final boolean mOverrideShowingSuggestions;
    public final boolean mSuggestClipboardContent;
    public final SettingsValuesForSuggestion mSettingsValuesForSuggestion;
    public final boolean mIncognitoModeEnabled;
    public final boolean mLongPressSymbolsForNumpad;
    public final int mEmojiMaxSdk;

    // User-defined colors
    public final Colors mColors;

    @Nullable
    public final String mAccount; // todo: always null, remove?

    // creation of Colors and SpacingAndPunctuations are the slowest parts in here, but still ok
    public SettingsValues(final Context context, final SharedPreferences prefs, final Resources res,
                          @NonNull final InputAttributes inputAttributes) {
        mLocale = ConfigurationCompatKt.locale(res.getConfiguration());
        mDisplayOrientation = res.getConfiguration().orientation;

        // Store the input attributes
        mInputAttributes = inputAttributes;

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, Defaults.PREF_AUTO_CAP) && ScriptUtils.scriptSupportsUppercase(mLocale);
        mVibrateOn = Settings.readVibrationEnabled(prefs);
        mVibrateInDndMode = prefs.getBoolean(Settings.PREF_VIBRATE_IN_DND_MODE, Defaults.PREF_VIBRATE_IN_DND_MODE);
        mSoundOn = prefs.getBoolean(Settings.PREF_SOUND_ON, Defaults.PREF_SOUND_ON);
        mKeyPreviewPopupOn = prefs.getBoolean(Settings.PREF_POPUP_ON, Defaults.PREF_POPUP_ON);
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(
                DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, Defaults.PREF_SLIDING_KEY_INPUT_PREVIEW);
        mShowsVoiceInputKey = mInputAttributes.mShouldShowVoiceInputKey;
        final String languagePref = prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, Defaults.PREF_LANGUAGE_SWITCH_KEY);
        mLanguageSwitchKeyToOtherImes = languagePref.equals("input_method") || languagePref.equals("both");
        mLanguageSwitchKeyToOtherSubtypes = languagePref.equals("internal") || languagePref.equals("both");
        mShowsLanguageSwitchKey = prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, Defaults.PREF_SHOW_LANGUAGE_SWITCH_KEY);
        mShowsNumberRow = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, Defaults.PREF_SHOW_NUMBER_ROW);
        mLocalizedNumberRow = prefs.getBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, Defaults.PREF_LOCALIZED_NUMBER_ROW);
        mShowNumberRowHints = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW_HINTS, Defaults.PREF_SHOW_NUMBER_ROW_HINTS);
        mShowsHints = prefs.getBoolean(Settings.PREF_SHOW_HINTS, Defaults.PREF_SHOW_HINTS);
        mShowsPopupHints = prefs.getBoolean(Settings.PREF_SHOW_POPUP_HINTS, Defaults.PREF_SHOW_POPUP_HINTS);
        mSpaceForLangChange = prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, Defaults.PREF_SPACE_TO_CHANGE_LANG);
        mShowsEmojiKey = prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, Defaults.PREF_SHOW_EMOJI_KEY);
        mVarToolbarDirection = prefs.getBoolean(Settings.PREF_VARIABLE_TOOLBAR_DIRECTION, Defaults.PREF_VARIABLE_TOOLBAR_DIRECTION);
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, Defaults.PREF_KEY_USE_PERSONALIZED_DICTS);
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, Defaults.PREF_KEY_USE_DOUBLE_SPACE_PERIOD)
                && inputAttributes.mIsGeneralTextInput;
        mBlockPotentiallyOffensive = prefs.getBoolean(Settings.PREF_BLOCK_POTENTIALLY_OFFENSIVE, Defaults.PREF_BLOCK_POTENTIALLY_OFFENSIVE);
        mUrlDetectionEnabled = prefs.getBoolean(Settings.PREF_URL_DETECTION, Defaults.PREF_URL_DETECTION);
        mAutoCorrectionEnabledPerUserSettings = prefs.getBoolean(Settings.PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION);
        mAutoCorrectEnabled = mAutoCorrectionEnabledPerUserSettings
                && (mInputAttributes.mInputTypeShouldAutoCorrect || prefs.getBoolean(Settings.PREF_MORE_AUTO_CORRECTION, Defaults.PREF_MORE_AUTO_CORRECTION))
                && (mUrlDetectionEnabled || !InputTypeUtils.isUriOrEmailType(mInputAttributes.mInputType));
        mCenterSuggestionTextToEnter = prefs.getBoolean(Settings.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, Defaults.PREF_CENTER_SUGGESTION_TEXT_TO_ENTER);
        mAutoCorrectionThreshold = mAutoCorrectEnabled
                ? readAutoCorrectionThreshold(res, prefs)
                : AUTO_CORRECTION_DISABLED_THRESHOLD;
        mScoreLimitForAutocorrect = (mAutoCorrectionThreshold < 0) ? 600000 // very aggressive
                : (mAutoCorrectionThreshold < 0.07 ? 800000 : 950000); // aggressive or modest
        mAutoCorrectShortcuts = prefs.getBoolean(Settings.PREF_AUTOCORRECT_SHORTCUTS, Defaults.PREF_AUTOCORRECT_SHORTCUTS);
        mBigramPredictionEnabled = prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, Defaults.PREF_BIGRAM_PREDICTIONS);
        mSuggestClipboardContent = prefs.getBoolean(Settings.PREF_SUGGEST_CLIPBOARD_CONTENT, Defaults.PREF_SUGGEST_CLIPBOARD_CONTENT);
        mDoubleSpacePeriodTimeout = 1100; // ms
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.getConfiguration());
        final boolean isLandscape = mDisplayOrientation == Configuration.ORIENTATION_LANDSCAPE;
        final float displayWidthDp = TypedValueCompat.pxToDp(res.getDisplayMetrics().widthPixels, res.getDisplayMetrics());
        mIsSplitKeyboardEnabled = Settings.readSplitKeyboardEnabled(prefs, isLandscape);
        // determine spacerWidth from display width and scale setting
        mSplitKeyboardSpacerRelativeWidth = mIsSplitKeyboardEnabled
                ? Math.min(Math.max((displayWidthDp - 600) / 600f + 0.15f, 0.15f), 0.35f) * Settings.readSplitSpacerScale(prefs, isLandscape)
                : 0f;
        mQuickPinToolbarKeys = prefs.getBoolean(Settings.PREF_QUICK_PIN_TOOLBAR_KEYS, Defaults.PREF_QUICK_PIN_TOOLBAR_KEYS);
        mScreenMetrics = Settings.readScreenMetrics(res);

        // Compute other readable settings
        mKeyLongpressTimeout = prefs.getInt(Settings.PREF_KEY_LONGPRESS_TIMEOUT, Defaults.PREF_KEY_LONGPRESS_TIMEOUT);
        mKeypressVibrationDuration = prefs.getInt(Settings.PREF_VIBRATION_DURATION_SETTINGS, Defaults.PREF_VIBRATION_DURATION_SETTINGS);
        mKeypressSoundVolume = prefs.getFloat(Settings.PREF_KEYPRESS_SOUND_VOLUME, Defaults.PREF_KEYPRESS_SOUND_VOLUME);
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, Defaults.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY);
        mGestureInputEnabled = JniUtils.sHaveGestureLib && prefs.getBoolean(Settings.PREF_GESTURE_INPUT, Defaults.PREF_GESTURE_INPUT);
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, Defaults.PREF_GESTURE_PREVIEW_TRAIL);
        mGestureFloatingPreviewTextEnabled = !mInputAttributes.mDisableGestureFloatingPreviewText
                && prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, Defaults.PREF_GESTURE_FLOATING_PREVIEW_TEXT);
        mGestureFloatingPreviewDynamicEnabled = Settings.readGestureDynamicPreviewEnabled(prefs);
        mGestureFastTypingCooldown = prefs.getInt(Settings.PREF_GESTURE_FAST_TYPING_COOLDOWN, Defaults.PREF_GESTURE_FAST_TYPING_COOLDOWN);
        mGestureTrailFadeoutDuration = prefs.getInt(Settings.PREF_GESTURE_TRAIL_FADEOUT_DURATION, Defaults.PREF_GESTURE_TRAIL_FADEOUT_DURATION);
        mAccount = null; // remove? or can it be useful somewhere?
        mOverrideShowingSuggestions = mInputAttributes.mMayOverrideShowingSuggestions && prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, Defaults.PREF_ALWAYS_SHOW_SUGGESTIONS);
        final boolean suggestionsEnabled = prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, Defaults.PREF_SHOW_SUGGESTIONS);
        mSuggestionsEnabledPerUserSettings = (mInputAttributes.mShouldShowSuggestions && suggestionsEnabled)
                || mOverrideShowingSuggestions;
        mIncognitoModeEnabled = prefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE) || mInputAttributes.mNoLearning
                || mInputAttributes.mIsPasswordField;
        mKeyboardHeightScale = prefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT_SCALE, Defaults.PREF_KEYBOARD_HEIGHT_SCALE);
        mSpaceSwipeHorizontal = Settings.readHorizontalSpaceSwipe(prefs);
        mSpaceSwipeVertical = Settings.readVerticalSpaceSwipe(prefs);
        mLanguageSwipeDistance = prefs.getInt(Settings.PREF_LANGUAGE_SWIPE_DISTANCE, Defaults.PREF_LANGUAGE_SWIPE_DISTANCE);
        mDeleteSwipeEnabled = prefs.getBoolean(Settings.PREF_DELETE_SWIPE, Defaults.PREF_DELETE_SWIPE);
        mAutospaceAfterPunctuationEnabled = prefs.getBoolean(Settings.PREF_AUTOSPACE_AFTER_PUNCTUATION, Defaults.PREF_AUTOSPACE_AFTER_PUNCTUATION);
        mClipboardHistoryEnabled = prefs.getBoolean(Settings.PREF_ENABLE_CLIPBOARD_HISTORY, Defaults.PREF_ENABLE_CLIPBOARD_HISTORY);
        mClipboardHistoryRetentionTime = prefs.getInt(Settings.PREF_CLIPBOARD_HISTORY_RETENTION_TIME, Defaults.PREF_CLIPBOARD_HISTORY_RETENTION_TIME);

        mOneHandedModeEnabled = Settings.readOneHandedModeEnabled(prefs, isLandscape);
        mOneHandedModeGravity = Settings.readOneHandedModeGravity(prefs, isLandscape);
        if (mOneHandedModeEnabled) {
            final float baseScale = res.getFraction(R.fraction.config_one_handed_mode_width, 1, 1);
            final float extraScale = Settings.readOneHandedModeScale(prefs, isLandscape);
            mOneHandedModeScale = 1 - (1 - baseScale) * extraScale;
        } else
            mOneHandedModeScale = 1f;
        final InputMethodSubtype selectedSubtype = SubtypeSettings.INSTANCE.getSelectedSubtype(prefs);
        mSecondaryLocales = Settings.getSecondaryLocales(prefs, mLocale);
        mShowMorePopupKeys = selectedSubtype.isAsciiCapable()
                ? Settings.readMorePopupKeysPref(prefs)
                : LocaleKeyboardInfosKt.POPUP_KEYS_NORMAL;
        mColors = Settings.getColorsForCurrentTheme(context, prefs);

        // read locale-specific popup key settings, fall back to global settings
        final String popupKeyTypesDefault = prefs.getString(Settings.PREF_POPUP_KEYS_ORDER, Defaults.PREF_POPUP_KEYS_ORDER);
        mPopupKeyTypes = PopupKeysUtilsKt.getEnabledPopupKeys(prefs, Settings.PREF_POPUP_KEYS_ORDER + "_" + mLocale.toLanguageTag(), popupKeyTypesDefault);
        final String popupKeyLabelDefault = prefs.getString(Settings.PREF_POPUP_KEYS_LABELS_ORDER, Defaults.PREF_POPUP_KEYS_LABELS_ORDER);
        mPopupKeyLabelSources = PopupKeysUtilsKt.getEnabledPopupKeys(prefs, Settings.PREF_POPUP_KEYS_LABELS_ORDER + "_" + mLocale.toLanguageTag(), popupKeyLabelDefault);

        mAddToPersonalDictionary = prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, Defaults.PREF_ADD_TO_PERSONAL_DICTIONARY);
        mUseContactsDictionary = SettingsValues.readUseContactsEnabled(prefs, context);
        mCustomNavBarColor = prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, Defaults.PREF_NAVBAR_COLOR);
        mNarrowKeyGaps = prefs.getBoolean(Settings.PREF_NARROW_KEY_GAPS, Defaults.PREF_NARROW_KEY_GAPS);
        mSettingsValuesForSuggestion = new SettingsValuesForSuggestion(
                mBlockPotentiallyOffensive,
                prefs.getBoolean(Settings.PREF_GESTURE_SPACE_AWARE, Defaults.PREF_GESTURE_SPACE_AWARE)
        );
        mSpacingAndPunctuations = new SpacingAndPunctuations(res, mUrlDetectionEnabled);
        mBottomPaddingScale = Settings.readBottomPaddingScale(prefs, isLandscape);
        mSidePaddingScale = Settings.readSidePaddingScale(prefs, isLandscape);
        mLongPressSymbolsForNumpad = prefs.getBoolean(Settings.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD, Defaults.PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD);
        mAutoShowToolbar = prefs.getBoolean(Settings.PREF_AUTO_SHOW_TOOLBAR, Defaults.PREF_AUTO_SHOW_TOOLBAR);
        mAutoHideToolbar = suggestionsEnabled && prefs.getBoolean(Settings.PREF_AUTO_HIDE_TOOLBAR, Defaults.PREF_AUTO_HIDE_TOOLBAR);
        mAlphaAfterEmojiInEmojiView = prefs.getBoolean(Settings.PREF_ABC_AFTER_EMOJI, Defaults.PREF_ABC_AFTER_EMOJI);
        mAlphaAfterClipHistoryEntry = prefs.getBoolean(Settings.PREF_ABC_AFTER_CLIP, Defaults.PREF_ABC_AFTER_CLIP);
        mAlphaAfterSymbolAndSpace = prefs.getBoolean(Settings.PREF_ABC_AFTER_SYMBOL_SPACE, Defaults.PREF_ABC_AFTER_SYMBOL_SPACE);
        mRemoveRedundantPopups = prefs.getBoolean(Settings.PREF_REMOVE_REDUNDANT_POPUPS, Defaults.PREF_REMOVE_REDUNDANT_POPUPS);
        mSpaceBarText = prefs.getString(Settings.PREF_SPACE_BAR_TEXT, Defaults.PREF_SPACE_BAR_TEXT);
        mEmojiMaxSdk = prefs.getInt(Settings.PREF_EMOJI_MAX_SDK, Defaults.PREF_EMOJI_MAX_SDK);
        mFontSizeMultiplier = prefs.getFloat(Settings.PREF_FONT_SCALE, Defaults.PREF_FONT_SCALE);
        mFontSizeMultiplierEmoji = prefs.getFloat(Settings.PREF_EMOJI_FONT_SCALE, Defaults.PREF_EMOJI_FONT_SCALE);
    }

    public boolean isApplicationSpecifiedCompletionsOn() {
        return mInputAttributes.mApplicationSpecifiedCompletionOn;
    }

    public boolean needsToLookupSuggestions() {
        return (mInputAttributes.mShouldShowSuggestions || mOverrideShowingSuggestions)
                && (mAutoCorrectEnabled || isSuggestionsEnabledPerUserSettings());
    }

    public boolean isSuggestionsEnabledPerUserSettings() {
        return mSuggestionsEnabledPerUserSettings;
    }

    public boolean isWordSeparator(final int code) {
        return mSpacingAndPunctuations.isWordSeparator(code);
    }

    public boolean isWordConnector(final int code) {
        return mSpacingAndPunctuations.isWordConnector(code);
    }

    public boolean isWordCodePoint(final int code) {
        return Character.isLetter(code) || isWordConnector(code)
                || Character.COMBINING_SPACING_MARK == Character.getType(code);
    }

    public boolean isUsuallyPrecededBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyPrecededBySpace(code);
    }

    public boolean isUsuallyFollowedBySpace(final int code) {
        return mSpacingAndPunctuations.isUsuallyFollowedBySpace(code);
    }

    public boolean shouldInsertSpacesAutomatically() {
        return mInputAttributes.mShouldInsertSpacesAutomatically;
    }

    public boolean isLanguageSwitchKeyEnabled() {
        if (!mShowsLanguageSwitchKey) {
            return false;
        }
        final RichInputMethodManager imm = RichInputMethodManager.getInstance();
        if (!mLanguageSwitchKeyToOtherSubtypes) {
            return imm.hasMultipleEnabledIMEsOrSubtypes(false /* include aux subtypes */);
        }
        if (!mLanguageSwitchKeyToOtherImes) {
            return imm.hasMultipleEnabledSubtypesInThisIme(false /* include aux subtypes */);
        }
        return imm.hasMultipleEnabledSubtypesInThisIme(false /* include aux subtypes */)
            || imm.hasMultipleEnabledIMEsOrSubtypes(false /* include aux subtypes */);
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return mInputAttributes.isSameInputType(editorInfo);
    }

    public boolean hasSameOrientation(final Configuration configuration) {
        return mDisplayOrientation == configuration.orientation;
    }

    // todo: way too complicated
    private static float readAutoCorrectionThreshold(final Resources res,
                                                     final SharedPreferences prefs) {
        final String currentAutoCorrectionSetting = Settings.readAutoCorrectConfidence(prefs, res);
        final String[] autoCorrectionThresholdValues = res.getStringArray(
                R.array.auto_correction_threshold_values);
        // When autoCorrectionThreshold is greater than 1.0, it's like auto correction is off.
        final float autoCorrectionThreshold;
        try {
            final int arrayIndex = Integer.parseInt(currentAutoCorrectionSetting);
            if (arrayIndex >= 0 && arrayIndex < autoCorrectionThresholdValues.length) {
                final String val = autoCorrectionThresholdValues[arrayIndex];
                if (FLOAT_MAX_VALUE_MARKER_STRING.equals(val)) {
                    autoCorrectionThreshold = Float.MAX_VALUE;
                } else if (FLOAT_NEGATIVE_INFINITY_MARKER_STRING.equals(val)) {
                    autoCorrectionThreshold = Float.NEGATIVE_INFINITY;
                } else {
                    autoCorrectionThreshold = Float.parseFloat(val);
                }
            } else {
                autoCorrectionThreshold = Float.MAX_VALUE;
            }
        } catch (final NumberFormatException e) {
            // Whenever the threshold settings are correct, never come here.
            Log.w(TAG, "Cannot load auto correction threshold setting."
                    + " currentAutoCorrectionSetting: " + currentAutoCorrectionSetting
                    + ", autoCorrectionThresholdValues: "
                    + Arrays.toString(autoCorrectionThresholdValues), e);
            return Float.MAX_VALUE;
        }
        return autoCorrectionThreshold;
    }

    private static boolean readUseContactsEnabled(final SharedPreferences prefs, final Context context) {
        final boolean setting = prefs.getBoolean(Settings.PREF_USE_CONTACTS, Defaults.PREF_USE_CONTACTS);
        if (!setting) return false;
        if (PermissionsUtil.checkAllPermissionsGranted(context, Manifest.permission.READ_CONTACTS))
            return true;
        // disable if permission not granted
        prefs.edit().putBoolean(Settings.PREF_USE_CONTACTS, false).apply();
        return false;
    }

    public String dump() {
        final StringBuilder sb = new StringBuilder("Current settings :");
        sb.append("\n   mSpacingAndPunctuations = ");
        sb.append("" + mSpacingAndPunctuations.dump());
        sb.append("\n   mAutoCap = ");
        sb.append("" + mAutoCap);
        sb.append("\n   mVibrateOn = ");
        sb.append("" + mVibrateOn);
        sb.append("\n   mSoundOn = ");
        sb.append("" + mSoundOn);
        sb.append("\n   mKeyPreviewPopupOn = ");
        sb.append("" + mKeyPreviewPopupOn);
        sb.append("\n   mShowsVoiceInputKey = ");
        sb.append("" + mShowsVoiceInputKey);
        sb.append("\n   mLanguageSwitchKeyToOtherImes = ");
        sb.append("" + mLanguageSwitchKeyToOtherImes);
        sb.append("\n   mLanguageSwitchKeyToOtherSubtypes = ");
        sb.append("" + mLanguageSwitchKeyToOtherSubtypes);
        sb.append("\n   mUsePersonalizedDicts = ");
        sb.append("" + mUsePersonalizedDicts);
        sb.append("\n   mUseDoubleSpacePeriod = ");
        sb.append("" + mUseDoubleSpacePeriod);
        sb.append("\n   mBlockPotentiallyOffensive = ");
        sb.append("" + mBlockPotentiallyOffensive);
        sb.append("\n   mBigramPredictionEnabled = ");
        sb.append("" + mBigramPredictionEnabled);
        sb.append("\n   mGestureInputEnabled = ");
        sb.append("" + mGestureInputEnabled);
        sb.append("\n   mGestureTrailEnabled = ");
        sb.append("" + mGestureTrailEnabled);
        sb.append("\n   mGestureFloatingPreviewTextEnabled = ");
        sb.append("" + mGestureFloatingPreviewTextEnabled);
        sb.append("\n   mSlidingKeyInputPreviewEnabled = ");
        sb.append("" + mSlidingKeyInputPreviewEnabled);
        sb.append("\n   mKeyLongpressTimeout = ");
        sb.append("" + mKeyLongpressTimeout);
        sb.append("\n   mLocale = ");
        sb.append("" + mLocale);
        sb.append("\n   mInputAttributes = ");
        sb.append("" + mInputAttributes);
        sb.append("\n   mKeypressVibrationDuration = ");
        sb.append("" + mKeypressVibrationDuration);
        sb.append("\n   mKeypressSoundVolume = ");
        sb.append("" + mKeypressSoundVolume);
        sb.append("\n   mAutoCorrectEnabled = ");
        sb.append("" + mAutoCorrectEnabled);
        sb.append("\n   mAutoCorrectionThreshold = ");
        sb.append("" + mAutoCorrectionThreshold);
        sb.append("\n   mAutoCorrectionEnabledPerUserSettings = ");
        sb.append("" + mAutoCorrectionEnabledPerUserSettings);
        sb.append("\n   mSuggestionsEnabledPerUserSettings = ");
        sb.append("" + mSuggestionsEnabledPerUserSettings);
        sb.append("\n   mDisplayOrientation = ");
        sb.append("" + mDisplayOrientation);
        sb.append("\n   mAppWorkarounds = ");
        return sb.toString();
    }
}
