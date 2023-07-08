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

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.util.Log;
import android.view.inputmethod.EditorInfo;

import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;

import org.dslul.openboard.inputmethod.compat.AppWorkaroundsUtils;
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme;
import org.dslul.openboard.inputmethod.latin.InputAttributes;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.spellcheck.AndroidSpellCheckerService;
import org.dslul.openboard.inputmethod.latin.utils.AsyncResultHolder;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.TargetPackageInfoGetterTask;

import java.util.Arrays;
import java.util.Locale;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * When you call the constructor of this class, you may want to change the current system locale by
 * using {@link org.dslul.openboard.inputmethod.latin.utils.RunInLocale}.
 */
// Non-final for testing via mock library.
public class SettingsValues {
    private static final String TAG = SettingsValues.class.getSimpleName();
    // "floatMaxValue" and "floatNegativeInfinity" are special marker strings for
    // Float.NEGATIVE_INFINITE and Float.MAX_VALUE. Currently used for auto-correction settings.
    private static final String FLOAT_MAX_VALUE_MARKER_STRING = "floatMaxValue";
    private static final String FLOAT_NEGATIVE_INFINITY_MARKER_STRING = "floatNegativeInfinity";
    private static final int TIMEOUT_TO_GET_TARGET_PACKAGE = 5; // seconds
    public static final float DEFAULT_SIZE_SCALE = 1.0f; // 100%
    public static final float AUTO_CORRECTION_DISABLED_THRESHOLD = Float.MAX_VALUE;

    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
    public final int mDelayInMillisecondsToUpdateOldSuggestions;
    public final long mDoubleSpacePeriodTimeout;
    // From configuration:
    public final Locale mLocale;
    public final boolean mHasHardwareKeyboard;
    public final int mDisplayOrientation;
    // From preferences, in the same order as xml/prefs.xml:
    public final boolean mAutoCap;
    public final boolean mVibrateOn;
    public final boolean mSoundOn;
    public final boolean mKeyPreviewPopupOn;
    public final boolean mShowsVoiceInputKey;
    public final boolean mIncludesOtherImesInLanguageSwitchList;
    public final boolean mShowsNumberRow;
    public final boolean mShowsHints;
    public final boolean mSpaceForLangChange;
    public final boolean mShowsLanguageSwitchKey;
    public final boolean mShowsEmojiKey;
    public final boolean mShowsClipboardKey;
    public final boolean mUsePersonalizedDicts;
    public final boolean mUseDoubleSpacePeriod;
    public final boolean mBlockPotentiallyOffensive;
    public final boolean mSpaceTrackpadEnabled;
    public final boolean mDeleteSwipeEnabled;
    public final boolean mAutospaceAfterPunctuationEnabled;
    public final boolean mClipboardHistoryEnabled;
    public final long mClipboardHistoryRetentionTime;
    public final boolean mOneHandedModeEnabled;
    public final int mOneHandedModeGravity;
    public final Locale mSecondaryLocale;
    // Use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mGestureInputEnabled;
    public final boolean mGestureTrailEnabled;
    public final boolean mGestureFloatingPreviewTextEnabled;
    public final boolean mSlidingKeyInputPreviewEnabled;
    public final int mKeyLongpressTimeout;
    public final boolean mEnableEmojiAltPhysicalKey;
    public final boolean mShowAppIcon;
    public final boolean mIsShowAppIconSettingInPreferences;
    public final boolean mCloudSyncEnabled;
    public final boolean mEnableMetricsLogging;
    public final boolean mShouldShowLxxSuggestionUi;
    // Use split layout for keyboard.
    public final boolean mIsSplitKeyboardEnabled;
    public final int mScreenMetrics;
    public final boolean mAddToPersonalDictionary;
    public final boolean mUseContactsDictionary;
    public final boolean mNavBarColor;

    // From the input box
    @Nonnull
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mKeypressSoundVolume;
    private final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    public final float mPlausibilityThreshold;
    public final boolean mAutoCorrectionEnabledPerUserSettings;
    private final boolean mSuggestionsEnabledPerUserSettings;
    public final boolean mIncognitoModeEnabled;
    private final AsyncResultHolder<AppWorkaroundsUtils> mAppWorkarounds;

    // User-defined colors
    public final boolean mUserTheme;
    public final ColorFilter mKeyBackgroundColorFilter;
    public final int mBackgroundColor;
    public final ColorFilter mBackgroundColorFilter;
    public final ColorFilter mKeyTextColorFilter;
    public final ColorFilter mHintTextColorFilter;
    public final int mUserThemeColorAccent;
    public final int mKeyTextColor;

    // Debug settings
    public final boolean mIsInternal;
    public final boolean mHasCustomKeyPreviewAnimationParams;
    public final boolean mHasKeyboardResize;
    public final float mKeyboardHeightScale;
    public final int mKeyPreviewShowUpDuration;
    public final int mKeyPreviewDismissDuration;
    public final float mKeyPreviewShowUpStartXScale;
    public final float mKeyPreviewShowUpStartYScale;
    public final float mKeyPreviewDismissEndXScale;
    public final float mKeyPreviewDismissEndYScale;

    @Nullable
    public final String mAccount;

    public SettingsValues(final Context context, final SharedPreferences prefs, final Resources res,
                          @Nonnull final InputAttributes inputAttributes) {
        mLocale = res.getConfiguration().locale;
        // Get the resources
        mDelayInMillisecondsToUpdateOldSuggestions =
                res.getInteger(R.integer.config_delay_in_milliseconds_to_update_old_suggestions);
        mSpacingAndPunctuations = new SpacingAndPunctuations(res);

        // Store the input attributes
        mInputAttributes = inputAttributes;

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true) && ScriptUtils.scriptSupportsUppercase(mLocale.getLanguage());
        mVibrateOn = Settings.readVibrationEnabled(prefs, res);
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res);
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res);
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(
                DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, true);
        mShowsVoiceInputKey = needsToShowVoiceInputKey(prefs, res) && mInputAttributes.mShouldShowVoiceInputKey;
        mIncludesOtherImesInLanguageSwitchList = !Settings.ENABLE_SHOW_LANGUAGE_SWITCH_KEY_SETTINGS || prefs.getBoolean(Settings.PREF_INCLUDE_OTHER_IMES_IN_LANGUAGE_SWITCH_LIST, false) /* forcibly */;
        mShowsNumberRow = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false);
        mShowsHints = prefs.getBoolean(Settings.PREF_SHOW_HINTS, true);
        mSpaceForLangChange = prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, true);
        mShowsLanguageSwitchKey = prefs.getBoolean(Settings.PREF_SHOW_LANGUAGE_SWITCH_KEY, false);
        mShowsEmojiKey = prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, false);
        mShowsClipboardKey = prefs.getBoolean(Settings.PREF_SHOW_CLIPBOARD_KEY, false);
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true);
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true)
                && inputAttributes.mIsGeneralTextInput;
        mBlockPotentiallyOffensive = Settings.readBlockPotentiallyOffensive(prefs, res);
        mAutoCorrectEnabled = Settings.readAutoCorrectEnabled(prefs, res);
        mAutoCorrectionThreshold = mAutoCorrectEnabled
                ? readAutoCorrectionThreshold(res, prefs)
                : AUTO_CORRECTION_DISABLED_THRESHOLD;
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res);
        mDoubleSpacePeriodTimeout = res.getInteger(R.integer.config_double_space_period_timeout);
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.getConfiguration());
        mEnableMetricsLogging = prefs.getBoolean(Settings.PREF_ENABLE_METRICS_LOGGING, true);
        mIsSplitKeyboardEnabled = prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false);
        mScreenMetrics = Settings.readScreenMetrics(res);

        mShouldShowLxxSuggestionUi = Settings.SHOULD_SHOW_LXX_SUGGESTION_UI
                && prefs.getBoolean(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI, true);
        // Compute other readable settings
        mKeyLongpressTimeout = Settings.readKeyLongpressTimeout(prefs, res);
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res);
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res);
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(
                Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true);
        mShowAppIcon = Settings.readShowSetupWizardIcon(prefs, context);
        mIsShowAppIconSettingInPreferences = prefs.contains(Settings.PREF_SHOW_SETUP_WIZARD_ICON);
        mPlausibilityThreshold = Settings.readPlausibilityThreshold(res);
        mGestureInputEnabled = Settings.readGestureInputEnabled(prefs, res);
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true);
        mCloudSyncEnabled = prefs.getBoolean(LocalSettingsConstants.PREF_ENABLE_CLOUD_SYNC, false);
        mAccount = prefs.getString(LocalSettingsConstants.PREF_ACCOUNT_NAME,
                null /* default */);
        mGestureFloatingPreviewTextEnabled = !mInputAttributes.mDisableGestureFloatingPreviewText
                && prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true);
        mAutoCorrectionEnabledPerUserSettings = mAutoCorrectEnabled;
                //&& !mInputAttributes.mInputTypeNoAutoCorrect;
        mSuggestionsEnabledPerUserSettings = !mInputAttributes.mIsPasswordField &&
                readSuggestionsEnabled(prefs);
        mIncognitoModeEnabled = Settings.readAlwaysIncognitoMode(prefs) || mInputAttributes.mNoLearning
                || mInputAttributes.mIsPasswordField;
        mIsInternal = Settings.isInternal(prefs);
        mHasCustomKeyPreviewAnimationParams = prefs.getBoolean(
                DebugSettings.PREF_HAS_CUSTOM_KEY_PREVIEW_ANIMATION_PARAMS, false);
        mHasKeyboardResize = prefs.getBoolean(DebugSettings.PREF_RESIZE_KEYBOARD, false);
        mKeyboardHeightScale = Settings.readKeyboardHeight(prefs, DEFAULT_SIZE_SCALE);
        mKeyPreviewShowUpDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_DURATION,
                res.getInteger(R.integer.config_key_preview_show_up_duration));
        mKeyPreviewDismissDuration = Settings.readKeyPreviewAnimationDuration(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_DURATION,
                res.getInteger(R.integer.config_key_preview_dismiss_duration));
        final float defaultKeyPreviewShowUpStartScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_show_up_start_scale);
        final float defaultKeyPreviewDismissEndScale = ResourceUtils.getFloatFromFraction(
                res, R.fraction.config_key_preview_dismiss_end_scale);
        mKeyPreviewShowUpStartXScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_X_SCALE,
                defaultKeyPreviewShowUpStartScale);
        mKeyPreviewShowUpStartYScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_SHOW_UP_START_Y_SCALE,
                defaultKeyPreviewShowUpStartScale);
        mKeyPreviewDismissEndXScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_X_SCALE,
                defaultKeyPreviewDismissEndScale);
        mKeyPreviewDismissEndYScale = Settings.readKeyPreviewAnimationScale(
                prefs, DebugSettings.PREF_KEY_PREVIEW_DISMISS_END_Y_SCALE,
                defaultKeyPreviewDismissEndScale);
        mDisplayOrientation = res.getConfiguration().orientation;
        mAppWorkarounds = new AsyncResultHolder<>("AppWorkarounds");
        final PackageInfo packageInfo = TargetPackageInfoGetterTask.getCachedPackageInfo(
                mInputAttributes.mTargetApplicationPackageName);
        if (null != packageInfo) {
            mAppWorkarounds.set(new AppWorkaroundsUtils(packageInfo));
        } else {
            new TargetPackageInfoGetterTask(context, mAppWorkarounds)
                    .execute(mInputAttributes.mTargetApplicationPackageName);
        }
        mSpaceTrackpadEnabled = Settings.readSpaceTrackpadEnabled(prefs);
        mDeleteSwipeEnabled = Settings.readDeleteSwipeEnabled(prefs);
        mAutospaceAfterPunctuationEnabled = Settings.readAutospaceAfterPunctuationEnabled(prefs);
        mClipboardHistoryEnabled = Settings.readClipboardHistoryEnabled(prefs);
        mClipboardHistoryRetentionTime = Settings.readClipboardHistoryRetentionTime(prefs, res);
        mOneHandedModeEnabled = Settings.readOneHandedModeEnabled(prefs);
        mOneHandedModeGravity = Settings.readOneHandedModeGravity(prefs);
        mSecondaryLocale = Settings.getSecondaryLocale(prefs, RichInputMethodManager.getInstance().getCurrentSubtypeLocale().toString());

        final int keyboardThemeId = KeyboardTheme.getThemeForParameters(
                prefs.getString(Settings.PREF_THEME_FAMILY, ""),
                prefs.getString(Settings.PREF_THEME_VARIANT, ""),
                prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false),
                prefs.getBoolean(Settings.PREF_THEME_DAY_NIGHT, false),
                prefs.getBoolean(Settings.PREF_THEME_AMOLED_MODE, false)
        );
        mUserTheme = KeyboardTheme.getIsUser(keyboardThemeId);
        mUserThemeColorAccent = prefs.getInt(Settings.PREF_THEME_USER_COLOR_ACCENT, Color.BLUE);
        final int keyBgColor;
        if (prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false))
            keyBgColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_KEYS, Color.LTGRAY);
        else
            keyBgColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_BACKGROUND, Color.DKGRAY);
        mKeyBackgroundColorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(keyBgColor, BlendModeCompat.MODULATE);
        mHintTextColorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(prefs.getInt(Settings.PREF_THEME_USER_COLOR_HINT_TEXT, Color.WHITE), BlendModeCompat.SRC_ATOP);
        mKeyTextColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_TEXT, Color.WHITE);
        mKeyTextColorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(mKeyTextColor, BlendModeCompat.SRC_ATOP);
        if (mUserTheme) {
            mBackgroundColor = prefs.getInt(Settings.PREF_THEME_USER_COLOR_BACKGROUND, Color.DKGRAY);
        } else if (KeyboardTheme.THEME_VARIANT_LIGHT.equals(KeyboardTheme.getThemeVariant(keyboardThemeId))) {
            mBackgroundColor = Color.rgb(236, 239, 241);
        } else if (keyboardThemeId == KeyboardTheme.THEME_ID_LXX_DARK) {
            mBackgroundColor = Color.rgb(38, 50, 56);
        } else {
            // dark border is 13/13/13, but that's ok
            mBackgroundColor = Color.BLACK;
        }
        mBackgroundColorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(mBackgroundColor, BlendModeCompat.MODULATE);

        mAddToPersonalDictionary = prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, false);
        mUseContactsDictionary = prefs.getBoolean(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY, false);
        mNavBarColor = prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, false);
    }

    public boolean isMetricsLoggingEnabled() {
        return mEnableMetricsLogging;
    }

    public boolean isApplicationSpecifiedCompletionsOn() {
        return mInputAttributes.mApplicationSpecifiedCompletionOn;
    }

    public boolean needsToLookupSuggestions() {
        return mInputAttributes.mShouldShowSuggestions
                && (mAutoCorrectionEnabledPerUserSettings || isSuggestionsEnabledPerUserSettings());
    }

    public boolean isSuggestionsEnabledPerUserSettings() {
        return mSuggestionsEnabledPerUserSettings;
    }

    public boolean isPersonalizationEnabled() {
        return mUsePersonalizedDicts;
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
        if (mIncludesOtherImesInLanguageSwitchList) {
            return imm.hasMultipleEnabledIMEsOrSubtypes(false /* include aux subtypes */);
        }
        return imm.hasMultipleEnabledSubtypesInThisIme(false /* include aux subtypes */);
    }

    public boolean isSameInputType(final EditorInfo editorInfo) {
        return mInputAttributes.isSameInputType(editorInfo);
    }

    public boolean hasSameOrientation(final Configuration configuration) {
        return mDisplayOrientation == configuration.orientation;
    }

    private static final String SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE = "2";

    private static boolean readSuggestionsEnabled(final SharedPreferences prefs) {
        if (prefs.contains(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)) {
            final boolean alwaysHide = SUGGESTIONS_VISIBILITY_HIDE_VALUE_OBSOLETE.equals(
                    prefs.getString(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE, null));
            prefs.edit()
                    .remove(Settings.PREF_SHOW_SUGGESTIONS_SETTING_OBSOLETE)
                    .putBoolean(Settings.PREF_SHOW_SUGGESTIONS, !alwaysHide)
                    .apply();
        }
        return prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true);
    }

    private static boolean readBigramPredictionEnabled(final SharedPreferences prefs,
                                                       final Resources res) {
        return prefs.getBoolean(Settings.PREF_BIGRAM_PREDICTIONS, res.getBoolean(
                R.bool.config_default_next_word_prediction));
    }

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

    private static boolean needsToShowVoiceInputKey(final SharedPreferences prefs,
                                                    final Resources res) {
        // Migrate preference from {@link Settings#PREF_VOICE_MODE_OBSOLETE} to
        // {@link Settings#PREF_VOICE_INPUT_KEY}.
        if (prefs.contains(Settings.PREF_VOICE_MODE_OBSOLETE)) {
            final String voiceModeMain = res.getString(R.string.voice_mode_main);
            final String voiceMode = prefs.getString(
                    Settings.PREF_VOICE_MODE_OBSOLETE, voiceModeMain);
            final boolean shouldShowVoiceInputKey = voiceModeMain.equals(voiceMode);
            prefs.edit()
                    .putBoolean(Settings.PREF_VOICE_INPUT_KEY, shouldShowVoiceInputKey)
                    // Remove the obsolete preference if exists.
                    .remove(Settings.PREF_VOICE_MODE_OBSOLETE)
                    .apply();
        }
        return prefs.getBoolean(Settings.PREF_VOICE_INPUT_KEY, true);
    }

    public String dump() {
        final StringBuilder sb = new StringBuilder("Current settings :");
        sb.append("\n   mSpacingAndPunctuations = ");
        sb.append("" + mSpacingAndPunctuations.dump());
        sb.append("\n   mDelayInMillisecondsToUpdateOldSuggestions = ");
        sb.append("" + mDelayInMillisecondsToUpdateOldSuggestions);
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
        sb.append("\n   mIncludesOtherImesInLanguageSwitchList = ");
        sb.append("" + mIncludesOtherImesInLanguageSwitchList);
        sb.append("\n   mShowsLanguageSwitchKey = ");
        sb.append("" + mShowsLanguageSwitchKey);
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
        final AppWorkaroundsUtils awu = mAppWorkarounds.get(null, 0);
        sb.append("" + (null == awu ? "null" : awu.toString()));
        sb.append("\n   mIsInternal = ");
        sb.append("" + mIsInternal);
        sb.append("\n   mKeyPreviewShowUpDuration = ");
        sb.append("" + mKeyPreviewShowUpDuration);
        sb.append("\n   mKeyPreviewDismissDuration = ");
        sb.append("" + mKeyPreviewDismissDuration);
        sb.append("\n   mKeyPreviewShowUpStartScaleX = ");
        sb.append("" + mKeyPreviewShowUpStartXScale);
        sb.append("\n   mKeyPreviewShowUpStartScaleY = ");
        sb.append("" + mKeyPreviewShowUpStartYScale);
        sb.append("\n   mKeyPreviewDismissEndScaleX = ");
        sb.append("" + mKeyPreviewDismissEndXScale);
        sb.append("\n   mKeyPreviewDismissEndScaleY = ");
        sb.append("" + mKeyPreviewDismissEndYScale);
        return sb.toString();
    }
}
