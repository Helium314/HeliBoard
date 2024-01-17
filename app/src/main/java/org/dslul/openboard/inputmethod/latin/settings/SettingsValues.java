/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import org.dslul.openboard.inputmethod.latin.utils.Log;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.dslul.openboard.inputmethod.compat.AppWorkaroundsUtils;
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.LocaleKeyTextsKt;
import org.dslul.openboard.inputmethod.latin.InputAttributes;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodManager;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.spellcheck.AndroidSpellCheckerService;
import org.dslul.openboard.inputmethod.latin.utils.AsyncResultHolder;
import org.dslul.openboard.inputmethod.latin.utils.MoreKeysUtilsKt;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeSettingsKt;
import org.dslul.openboard.inputmethod.latin.utils.TargetPackageInfoGetterTask;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
    public static final float DEFAULT_SIZE_SCALE = 1.0f; // 100%
    public static final float AUTO_CORRECTION_DISABLED_THRESHOLD = Float.MAX_VALUE;

    // From resources:
    public final SpacingAndPunctuations mSpacingAndPunctuations;
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
    public final boolean mLanguageSwitchKeyToOtherImes;
    public final boolean mLanguageSwitchKeyToOtherSubtypes;
    public final boolean mShowsNumberRow;
    public final boolean mLocalizedNumberRow;
    public final boolean mShowsHints;
    public final boolean mShowsPopupHints;
    public final boolean mSpaceForLangChange;
    public final boolean mSpaceLanguageSlide;
    public final boolean mShowsEmojiKey;
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
    public final float mOneHandedModeScale;
    public final boolean mNarrowKeyGaps;
    public final int mShowMoreMoreKeys;
    public final List<String> mMoreKeyTypes;
    public final List<String> mMoreKeyLabelSources;
    public final List<Locale> mSecondaryLocales;
    // Use bigrams to predict the next word when there is no input for it yet
    public final boolean mBigramPredictionEnabled;
    public final boolean mGestureInputEnabled;
    public final boolean mGestureTrailEnabled;
    public final boolean mGestureFloatingPreviewTextEnabled;
    public final boolean mSlidingKeyInputPreviewEnabled;
    public final int mKeyLongpressTimeout;
    public final boolean mEnableEmojiAltPhysicalKey;
    public final boolean mIsShowAppIconSettingInPreferences;
    public final boolean mShouldShowLxxSuggestionUi;
    // Use split layout for keyboard.
    public final boolean mIsSplitKeyboardEnabled;
    public final float mSplitKeyboardSpacerRelativeWidth;
    public final int mScreenMetrics;
    public final boolean mAddToPersonalDictionary;
    public final boolean mUseContactsDictionary;
    public final boolean mCustomNavBarColor;
    public final float mKeyboardHeightScale;
    public final boolean mUrlDetectionEnabled;
    public final float mBottomPaddingScale;

    // From the input box
    @NonNull
    public final InputAttributes mInputAttributes;

    // Deduced settings
    public final int mKeypressVibrationDuration;
    public final float mKeypressSoundVolume;
    private final boolean mAutoCorrectEnabled;
    public final float mAutoCorrectionThreshold;
    public final int mScoreLimitForAutocorrect;
    public final boolean mAutoCorrectionEnabledPerUserSettings;
    private final boolean mSuggestionsEnabledPerUserSettings;
    public final SettingsValuesForSuggestion mSettingsValuesForSuggestion;
    public final boolean mIncognitoModeEnabled;
    private final AsyncResultHolder<AppWorkaroundsUtils> mAppWorkarounds;

    // User-defined colors
    public final Colors mColors;

    @Nullable
    public final String mAccount;

    // creation of Colors and SpacingAndPunctuations are the slowest parts in here, but still ok
    public SettingsValues(final Context context, final SharedPreferences prefs, final Resources res,
                          @NonNull final InputAttributes inputAttributes) {
        mLocale = res.getConfiguration().locale;

        // Store the input attributes
        mInputAttributes = inputAttributes;

        // Get the settings preferences
        mAutoCap = prefs.getBoolean(Settings.PREF_AUTO_CAP, true) && ScriptUtils.scriptSupportsUppercase(mLocale);
        mVibrateOn = Settings.readVibrationEnabled(prefs, res);
        mSoundOn = Settings.readKeypressSoundEnabled(prefs, res);
        mKeyPreviewPopupOn = Settings.readKeyPreviewPopupEnabled(prefs, res);
        mSlidingKeyInputPreviewEnabled = prefs.getBoolean(
                DebugSettings.PREF_SLIDING_KEY_INPUT_PREVIEW, true);
        mShowsVoiceInputKey = mInputAttributes.mShouldShowVoiceInputKey;
        final String languagePref = prefs.getString(Settings.PREF_LANGUAGE_SWITCH_KEY, "off");
        mLanguageSwitchKeyToOtherImes = languagePref.equals("input_method") || languagePref.equals("both");
        mLanguageSwitchKeyToOtherSubtypes = languagePref.equals("internal") || languagePref.equals("both");
        mShowsNumberRow = prefs.getBoolean(Settings.PREF_SHOW_NUMBER_ROW, false);
        mLocalizedNumberRow = prefs.getBoolean(Settings.PREF_LOCALIZED_NUMBER_ROW, true);
        mShowsHints = prefs.getBoolean(Settings.PREF_SHOW_HINTS, true);
        mShowsPopupHints = prefs.getBoolean(Settings.PREF_SHOW_POPUP_HINTS, false);
        mSpaceForLangChange = prefs.getBoolean(Settings.PREF_SPACE_TO_CHANGE_LANG, true);
        mSpaceLanguageSlide = prefs.getBoolean(Settings.PREF_SPACE_LANGUAGE_SLIDE, false);
        mShowsEmojiKey = prefs.getBoolean(Settings.PREF_SHOW_EMOJI_KEY, false);
        mUsePersonalizedDicts = prefs.getBoolean(Settings.PREF_KEY_USE_PERSONALIZED_DICTS, true);
        mUseDoubleSpacePeriod = prefs.getBoolean(Settings.PREF_KEY_USE_DOUBLE_SPACE_PERIOD, true)
                && inputAttributes.mIsGeneralTextInput;
        mBlockPotentiallyOffensive = Settings.readBlockPotentiallyOffensive(prefs, res);
        mAutoCorrectEnabled = Settings.readAutoCorrectEnabled(prefs);
        mAutoCorrectionThreshold = mAutoCorrectEnabled
                ? readAutoCorrectionThreshold(res, prefs)
                : AUTO_CORRECTION_DISABLED_THRESHOLD;
        mScoreLimitForAutocorrect = (mAutoCorrectionThreshold < 0) ? 600000 // very aggressive
                : (mAutoCorrectionThreshold < 0.07 ? 800000 : 950000); // aggressive or modest
        mBigramPredictionEnabled = readBigramPredictionEnabled(prefs, res);
        mDoubleSpacePeriodTimeout = res.getInteger(R.integer.config_double_space_period_timeout);
        mHasHardwareKeyboard = Settings.readHasHardwareKeyboard(res.getConfiguration());
        final float displayWidthDp = res.getDisplayMetrics().widthPixels / res.getDisplayMetrics().density;
        mIsSplitKeyboardEnabled = prefs.getBoolean(Settings.PREF_ENABLE_SPLIT_KEYBOARD, false) && displayWidthDp > 600; // require display width of 600 dp for split
        // determine spacerWidth from display width and scale setting
        mSplitKeyboardSpacerRelativeWidth = mIsSplitKeyboardEnabled
                ? Math.min(Math.max((displayWidthDp - 600) / 6000f + 0.15f, 0.15f), 0.25f) * prefs.getFloat(Settings.PREF_SPLIT_SPACER_SCALE, DEFAULT_SIZE_SCALE)
                : 0f;
        mScreenMetrics = Settings.readScreenMetrics(res);

        mShouldShowLxxSuggestionUi = Settings.SHOULD_SHOW_LXX_SUGGESTION_UI
                && prefs.getBoolean(DebugSettings.PREF_SHOULD_SHOW_LXX_SUGGESTION_UI, true);
        // Compute other readable settings
        mKeyLongpressTimeout = Settings.readKeyLongpressTimeout(prefs, res);
        mKeypressVibrationDuration = Settings.readKeypressVibrationDuration(prefs, res);
        mKeypressSoundVolume = Settings.readKeypressSoundVolume(prefs, res);
        mEnableEmojiAltPhysicalKey = prefs.getBoolean(Settings.PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY, true);
        mIsShowAppIconSettingInPreferences = prefs.contains(Settings.PREF_SHOW_SETUP_WIZARD_ICON);
        mGestureInputEnabled = Settings.readGestureInputEnabled(prefs);
        mGestureTrailEnabled = prefs.getBoolean(Settings.PREF_GESTURE_PREVIEW_TRAIL, true);
        mAccount = null; // remove? or can it be useful somewhere?
        mGestureFloatingPreviewTextEnabled = !mInputAttributes.mDisableGestureFloatingPreviewText
                && prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_TEXT, true);
        mAutoCorrectionEnabledPerUserSettings = mAutoCorrectEnabled;
                //&& !mInputAttributes.mInputTypeNoAutoCorrect; // follow that request or not?
        mSuggestionsEnabledPerUserSettings = (mInputAttributes.mShouldShowSuggestions && readSuggestionsEnabled(prefs))
                || (!mInputAttributes.mIsPasswordField && readSuggestionsOverrideEnabled(prefs));
        mIncognitoModeEnabled = Settings.readAlwaysIncognitoMode(prefs) || mInputAttributes.mNoLearning
                || mInputAttributes.mIsPasswordField;
        mKeyboardHeightScale = prefs.getFloat(Settings.PREF_KEYBOARD_HEIGHT_SCALE, DEFAULT_SIZE_SCALE);
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

        mOneHandedModeEnabled = Settings.readOneHandedModeEnabled(prefs, mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT);
        mOneHandedModeGravity = Settings.readOneHandedModeGravity(prefs, mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT);
        if (mOneHandedModeEnabled) {
            final float baseScale = res.getFraction(R.fraction.config_one_handed_mode_width, 1, 1);
            final float extraScale = Settings.readOneHandedModeScale(prefs, mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT);
            mOneHandedModeScale = 1 - (1 - baseScale) * extraScale;
        } else
            mOneHandedModeScale = 1f;
        final InputMethodSubtype selectedSubtype = SubtypeSettingsKt.getSelectedSubtype(prefs);
        mSecondaryLocales = Settings.getSecondaryLocales(prefs, selectedSubtype.getLocale());
        mShowMoreMoreKeys = selectedSubtype.isAsciiCapable()
                ? Settings.readMoreMoreKeysPref(prefs)
                : LocaleKeyTextsKt.MORE_KEYS_NORMAL;
        mColors = Settings.getColorsForCurrentTheme(context, prefs);

        // read locale-specific popup key settings, fall back to global settings
        final String moreKeyTypesDefault = prefs.getString(Settings.PREF_MORE_KEYS_ORDER, MoreKeysUtilsKt.MORE_KEYS_ORDER_DEFAULT);
        mMoreKeyTypes = MoreKeysUtilsKt.getEnabledMoreKeys(prefs, Settings.PREF_MORE_KEYS_ORDER + "_" + selectedSubtype.getLocale(), moreKeyTypesDefault);
        final String moreKeyLabelDefault = prefs.getString(Settings.PREF_MORE_KEYS_LABELS_ORDER, MoreKeysUtilsKt.MORE_KEYS_LABEL_DEFAULT);
        mMoreKeyLabelSources = MoreKeysUtilsKt.getEnabledMoreKeys(prefs, Settings.PREF_MORE_KEYS_LABELS_ORDER + "_" + selectedSubtype.getLocale(), moreKeyLabelDefault);

        mAddToPersonalDictionary = prefs.getBoolean(Settings.PREF_ADD_TO_PERSONAL_DICTIONARY, false);
        mUseContactsDictionary = prefs.getBoolean(AndroidSpellCheckerService.PREF_USE_CONTACTS_KEY, false);
        mCustomNavBarColor = prefs.getBoolean(Settings.PREF_NAVBAR_COLOR, false);
        mNarrowKeyGaps = prefs.getBoolean(Settings.PREF_NARROW_KEY_GAPS, true);
        mSettingsValuesForSuggestion = new SettingsValuesForSuggestion(
                mBlockPotentiallyOffensive,
                prefs.getBoolean(Settings.PREF_GESTURE_SPACE_AWARE, false)
        );
        mUrlDetectionEnabled = prefs.getBoolean(Settings.PREF_URL_DETECTION, false);
        mSpacingAndPunctuations = new SpacingAndPunctuations(res, mUrlDetectionEnabled);
        mBottomPaddingScale = prefs.getFloat(Settings.PREF_BOTTOM_PADDING_SCALE, DEFAULT_SIZE_SCALE);
    }

    public boolean isApplicationSpecifiedCompletionsOn() {
        return mInputAttributes.mApplicationSpecifiedCompletionOn;
    }

    public boolean needsToLookupSuggestions() {
        return mAutoCorrectionEnabledPerUserSettings || isSuggestionsEnabledPerUserSettings();
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
        if (!mLanguageSwitchKeyToOtherImes && !mLanguageSwitchKeyToOtherSubtypes) {
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

    private static boolean readSuggestionsEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(Settings.PREF_SHOW_SUGGESTIONS, true);
    }

    private static boolean readSuggestionsOverrideEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(Settings.PREF_ALWAYS_SHOW_SUGGESTIONS, false);
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
        final AppWorkaroundsUtils awu = mAppWorkarounds.get(null, 0);
        sb.append("" + (null == awu ? "null" : awu.toString()));
        return sb.toString();
    }
}
