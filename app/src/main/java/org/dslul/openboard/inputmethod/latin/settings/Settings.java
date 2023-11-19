/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.latin.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.os.Build;
import android.util.Log;

import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme;
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.LocaleKeyTextsKt;
import org.dslul.openboard.inputmethod.latin.AudioAndHapticFeedbackManager;
import org.dslul.openboard.inputmethod.latin.InputAttributes;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.common.Colors;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.AdditionalSubtypeUtils;
import org.dslul.openboard.inputmethod.latin.utils.ColorUtilKt;
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils;
import org.dslul.openboard.inputmethod.latin.utils.JniUtils;
import org.dslul.openboard.inputmethod.latin.utils.ResourceUtils;
import org.dslul.openboard.inputmethod.latin.utils.RunInLocale;
import org.dslul.openboard.inputmethod.latin.utils.StatsUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public final class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();
    // Settings screens
    public static final String SCREEN_DEBUG = "screen_debug";
    public static final String SCREEN_GESTURE = "screen_gesture";
    // In the same order as xml/prefs.xml
    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
    public static final String PREF_THEME_STYLE = "theme_style";
    public static final String PREF_THEME_COLORS = "theme_variant";
    public static final String PREF_THEME_COLORS_NIGHT = "theme_variant_night";
    public static final String PREF_THEME_KEY_BORDERS = "theme_key_borders";
    public static final String PREF_THEME_DAY_NIGHT = "theme_auto_day_night";
    public static final String PREF_THEME_USER_COLOR_PREFIX = "theme_color_";
    public static final String PREF_THEME_USER_COLOR_NIGHT_PREFIX = "theme_dark_color_";
    public static final String PREF_COLOR_KEYS_SUFFIX = "keys";
    public static final String PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX = "functional_keys";
    public static final String PREF_COLOR_SPACEBAR_SUFFIX = "spacebar";
    public static final String PREF_COLOR_SPACEBAR_TEXT_SUFFIX = "spacebar_text";
    public static final String PREF_COLOR_ACCENT_SUFFIX = "accent";
    public static final String PREF_COLOR_TEXT_SUFFIX = "text";
    public static final String PREF_COLOR_HINT_TEXT_SUFFIX = "hint_text";
    public static final String PREF_COLOR_BACKGROUND_SUFFIX = "background";
    public static final String PREF_AUTO_USER_COLOR_SUFFIX = "_auto";
    public static final String PREF_EDIT_PERSONAL_DICTIONARY = "edit_personal_dictionary";
    public static final String PREF_AUTO_CORRECTION = "pref_key_auto_correction";
    public static final String PREF_AUTO_CORRECTION_CONFIDENCE = "pref_key_auto_correction_confidence";
    public static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    public static final String PREF_KEY_USE_PERSONALIZED_DICTS = "pref_key_use_personalized_dicts";
    public static final String PREF_KEY_USE_DOUBLE_SPACE_PERIOD = "pref_key_use_double_space_period";
    public static final String PREF_BLOCK_POTENTIALLY_OFFENSIVE = "pref_key_block_potentially_offensive";
    public static final boolean SHOULD_SHOW_LXX_SUGGESTION_UI =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    public static final String PREF_LANGUAGE_SWITCH_KEY = "pref_language_switch_key";
    public static final String PREF_SHOW_EMOJI_KEY = "pref_show_emoji_key";
    public static final String PREF_CUSTOM_INPUT_STYLES = "custom_input_styles";
    public static final String PREF_ENABLE_SPLIT_KEYBOARD = "pref_split_keyboard";
    public static final String PREF_SPLIT_SPACER_SCALE = "pref_split_spacer_scale";
    public static final String PREF_KEYBOARD_HEIGHT_SCALE = "pref_keyboard_height_scale";
    public static final String PREF_BOTTOM_PADDING_SCALE = "pref_bottom_padding_scale";
    public static final String PREF_SPACE_TRACKPAD = "pref_space_trackpad";
    public static final String PREF_DELETE_SWIPE = "pref_delete_swipe";
    public static final String PREF_AUTOSPACE_AFTER_PUNCTUATION = "pref_autospace_after_punctuation";
    public static final String PREF_ALWAYS_INCOGNITO_MODE = "pref_always_incognito_mode";
    public static final String PREF_BIGRAM_PREDICTIONS = "next_word_prediction";
    public static final String PREF_GESTURE_INPUT = "gesture_input";
    public static final String PREF_VIBRATION_DURATION_SETTINGS = "pref_vibration_duration_settings";
    public static final String PREF_KEYPRESS_SOUND_VOLUME = "pref_keypress_sound_volume";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "pref_key_longpress_timeout";
    public static final String PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = "pref_enable_emoji_alt_physical_key";
    public static final String PREF_GESTURE_PREVIEW_TRAIL = "pref_gesture_preview_trail";
    public static final String PREF_GESTURE_FLOATING_PREVIEW_TEXT = "pref_gesture_floating_preview_text";
    public static final String PREF_GESTURE_SPACE_AWARE = "pref_gesture_space_aware";
    public static final String PREF_SHOW_SETUP_WIZARD_ICON = "pref_show_setup_wizard_icon";
    public static final String PREF_USE_NEW_KEYBOARD_PARSING = "pref_use_new_keyboard_parsing";

    public static final String PREF_ONE_HANDED_MODE = "pref_one_handed_mode_enabled";
    public static final String PREF_ONE_HANDED_GRAVITY = "pref_one_handed_mode_gravity";

    public static final String PREF_SHOW_NUMBER_ROW = "pref_show_number_row";

    public static final String PREF_SHOW_HINTS = "pref_show_hints";
    public static final String PREF_SHOW_POPUP_HINTS = "pref_show_popup_hints";

    public static final String PREF_SPACE_TO_CHANGE_LANG = "prefs_long_press_keyboard_to_change_lang";
    public static final String PREF_SPACE_LANGUAGE_SLIDE = "pref_space_language_slide";

    public static final String PREF_ENABLE_CLIPBOARD_HISTORY = "pref_enable_clipboard_history";
    public static final String PREF_CLIPBOARD_HISTORY_RETENTION_TIME = "pref_clipboard_history_retention_time";

    public static final String PREF_SECONDARY_LOCALES_PREFIX = "pref_secondary_locales_";
    public static final String PREF_ADD_TO_PERSONAL_DICTIONARY = "pref_add_to_personal_dictionary";
    public static final String PREF_NAVBAR_COLOR = "pref_navbar_color";
    public static final String PREF_NARROW_KEY_GAPS = "pref_narrow_key_gaps";
    public static final String PREF_ENABLED_INPUT_STYLES = "pref_enabled_input_styles";
    public static final String PREF_SELECTED_INPUT_STYLE = "pref_selected_input_style";
    public static final String PREF_USE_SYSTEM_LOCALES = "pref_use_system_locales";
    public static final String PREF_MORE_MORE_KEYS = "pref_more_more_keys";
    public static final String PREF_URL_DETECTION = "pref_url_detection";

    public static final String PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG = "pref_dont_show_missing_dict_dialog";
    public static final String PREF_PINNED_KEYS = "pref_pinned_keys";

    // Emoji
    public static final String PREF_EMOJI_RECENT_KEYS = "emoji_recent_keys";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = "last_shown_emoji_category_page_id";

    public static final String PREF_PINNED_CLIPS = "pinned_clips";
    // used as a workaround against keyboard not showing edited theme in ColorsSettingsFragment
    public static final String PREF_FORCE_OPPOSITE_THEME = "force_opposite_theme";

    private static final float UNDEFINED_PREFERENCE_VALUE_FLOAT = -1.0f;
    private static final int UNDEFINED_PREFERENCE_VALUE_INT = -1;

    private Context mContext;
    private Resources mRes;
    private SharedPreferences mPrefs;
    private SettingsValues mSettingsValues;
    private final ReentrantLock mSettingsValuesLock = new ReentrantLock();

    private static final Settings sInstance = new Settings();

    // preferences that are not used in SettingsValues
    private static final HashSet<String> dontReloadOnChanged = new HashSet<>() {{
        add(PREF_FORCE_OPPOSITE_THEME);
        add(PREF_PINNED_CLIPS);
        add(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID);
        add(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID);
        add(PREF_EMOJI_RECENT_KEYS);
        add(PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG);
    }};

    public static Settings getInstance() {
        return sInstance;
    }

    public static void init(final Context context) {
        sInstance.onCreate(context);
    }

    private Settings() {
        // Intentional empty constructor for singleton.
    }

    private void onCreate(final Context context) {
        mContext = context;
        mRes = context.getResources();
        mPrefs = DeviceProtectedUtils.getSharedPreferences(context);
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    public void onDestroy() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    @Override
    public void onSharedPreferenceChanged(final SharedPreferences prefs, final String key) {
        if (dontReloadOnChanged.contains(key))
            return;
        mSettingsValuesLock.lock();
        try {
            if (mSettingsValues == null) {
                // TODO: Introduce a static function to register this class and ensure that
                // loadSettings must be called before "onSharedPreferenceChanged" is called.
                Log.w(TAG, "onSharedPreferenceChanged called before loadSettings.");
                return;
            }
            loadSettings(mContext, mSettingsValues.mLocale, mSettingsValues.mInputAttributes);
            StatsUtils.onLoadSettings(mSettingsValues);
        } finally {
            mSettingsValuesLock.unlock();
        }
        if (PREF_CUSTOM_INPUT_STYLES.equals(key)) {
            final String additionalSubtypes = readPrefAdditionalSubtypes(prefs, mContext.getResources());
            SubtypeSettingsKt.updateAdditionalSubtypes(AdditionalSubtypeUtils.createAdditionalSubtypesArray(additionalSubtypes));
        }
    }

    public void loadSettings(final Context context, final Locale locale,
                             @NonNull final InputAttributes inputAttributes) {
        mSettingsValuesLock.lock();
        mContext = context;
        try {
            final SharedPreferences prefs = mPrefs;
            final RunInLocale<SettingsValues> job = new RunInLocale<SettingsValues>() {
                @Override
                protected SettingsValues job(final Resources res) {
                    return new SettingsValues(context, prefs, res, inputAttributes);
                }
            };
            mSettingsValues = job.runInLocale(mRes, locale);
        } finally {
            mSettingsValuesLock.unlock();
        }
    }

    // TODO: Remove this method and add proxy method to SettingsValues.
    public SettingsValues getCurrent() {
        return mSettingsValues;
    }

    public static int readScreenMetrics(final Resources res) {
        return res.getInteger(R.integer.config_screen_metrics);
    }

    // Accessed from the settings interface, hence public
    public static boolean readKeypressSoundEnabled(final SharedPreferences prefs,
                                                   final Resources res) {
        return prefs.getBoolean(PREF_SOUND_ON, res.getBoolean(R.bool.config_default_sound_enabled));
    }

    public static boolean readVibrationEnabled(final SharedPreferences prefs,
                                               final Resources res) {
        final boolean hasVibrator = AudioAndHapticFeedbackManager.getInstance().hasVibrator();
        return hasVibrator && prefs.getBoolean(PREF_VIBRATE_ON,
                res.getBoolean(R.bool.config_default_vibration_enabled));
    }

    public static boolean readAutoCorrectEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_AUTO_CORRECTION, true);
    }

    public static String readAutoCorrectConfidence(final SharedPreferences prefs,
                                                   final Resources res) {
        return prefs.getString(PREF_AUTO_CORRECTION_CONFIDENCE,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
    }

    public static boolean readBlockPotentiallyOffensive(final SharedPreferences prefs,
                                                        final Resources res) {
        return prefs.getBoolean(PREF_BLOCK_POTENTIALLY_OFFENSIVE,
                res.getBoolean(R.bool.config_block_potentially_offensive));
    }

    public static boolean readGestureInputEnabled(final SharedPreferences prefs) {
        return JniUtils.sHaveGestureLib && prefs.getBoolean(PREF_GESTURE_INPUT, true);
    }

    public static boolean readFromBuildConfigIfToShowKeyPreviewPopupOption(final Resources res) {
        return res.getBoolean(R.bool.config_enable_show_key_preview_popup_option);
    }

    public static boolean readKeyPreviewPopupEnabled(final SharedPreferences prefs,
                                                     final Resources res) {
        final boolean defaultKeyPreviewPopup = res.getBoolean(
                R.bool.config_default_key_preview_popup);
        if (!readFromBuildConfigIfToShowKeyPreviewPopupOption(res)) {
            return defaultKeyPreviewPopup;
        }
        return prefs.getBoolean(PREF_POPUP_ON, defaultKeyPreviewPopup);
    }

    public static boolean readAlwaysIncognitoMode(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_ALWAYS_INCOGNITO_MODE, false);
    }

    public static String readPrefAdditionalSubtypes(final SharedPreferences prefs,
                                                    final Resources res) {
        final String predefinedPrefSubtypes = AdditionalSubtypeUtils.createPrefSubtypes(
                res.getStringArray(R.array.predefined_subtypes));
        return prefs.getString(PREF_CUSTOM_INPUT_STYLES, predefinedPrefSubtypes);
    }

    public static void writePrefAdditionalSubtypes(final SharedPreferences prefs,
                                                   final String prefSubtypes) {
        prefs.edit().putString(PREF_CUSTOM_INPUT_STYLES, prefSubtypes).apply();
    }

    public static float readKeypressSoundVolume(final SharedPreferences prefs,
                                                final Resources res) {
        final float volume = prefs.getFloat(
                PREF_KEYPRESS_SOUND_VOLUME, UNDEFINED_PREFERENCE_VALUE_FLOAT);
        return (volume != UNDEFINED_PREFERENCE_VALUE_FLOAT) ? volume
                : readDefaultKeypressSoundVolume(res);
    }

    // Default keypress sound volume for unknown devices.
    // The negative value means system default.
    private static final String DEFAULT_KEYPRESS_SOUND_VOLUME = Float.toString(-1.0f);

    public static float readDefaultKeypressSoundVolume(final Resources res) {
        return Float.parseFloat(ResourceUtils.getDeviceOverrideValue(res,
                R.array.keypress_volumes, DEFAULT_KEYPRESS_SOUND_VOLUME));
    }

    public static int readKeyLongpressTimeout(final SharedPreferences prefs,
                                              final Resources res) {
        final int milliseconds = prefs.getInt(
                PREF_KEY_LONGPRESS_TIMEOUT, UNDEFINED_PREFERENCE_VALUE_INT);
        return (milliseconds != UNDEFINED_PREFERENCE_VALUE_INT) ? milliseconds
                : readDefaultKeyLongpressTimeout(res);
    }

    public static int readDefaultKeyLongpressTimeout(final Resources res) {
        return res.getInteger(R.integer.config_default_longpress_key_timeout);
    }

    public static int readKeypressVibrationDuration(final SharedPreferences prefs,
                                                    final Resources res) {
        final int milliseconds = prefs.getInt(
                PREF_VIBRATION_DURATION_SETTINGS, UNDEFINED_PREFERENCE_VALUE_INT);
        return (milliseconds != UNDEFINED_PREFERENCE_VALUE_INT) ? milliseconds
                : readDefaultKeypressVibrationDuration(res);
    }

    // Default keypress vibration duration for unknown devices.
    // The negative value means system default.
    private static final String DEFAULT_KEYPRESS_VIBRATION_DURATION = Integer.toString(-1);

    public static int readDefaultKeypressVibrationDuration(final Resources res) {
        return Integer.parseInt(ResourceUtils.getDeviceOverrideValue(res,
                R.array.keypress_vibration_durations, DEFAULT_KEYPRESS_VIBRATION_DURATION));
    }

    public static boolean readClipboardHistoryEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_ENABLE_CLIPBOARD_HISTORY, true);
    }

    public static int readClipboardHistoryRetentionTime(final SharedPreferences prefs,
                                              final Resources res) {
        final int minutes = prefs.getInt(
                PREF_CLIPBOARD_HISTORY_RETENTION_TIME, UNDEFINED_PREFERENCE_VALUE_INT);
        return (minutes != UNDEFINED_PREFERENCE_VALUE_INT) ? minutes
                : readDefaultClipboardHistoryRetentionTime(res);
    }

    public static int readDefaultClipboardHistoryRetentionTime(final Resources res) {
        return res.getInteger(R.integer.config_clipboard_history_retention_time);
    }

    public static boolean readSpaceTrackpadEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_SPACE_TRACKPAD, true);
    }

    public static boolean readDeleteSwipeEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_DELETE_SWIPE, true);
    }

    public static boolean readAutospaceAfterPunctuationEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_AUTOSPACE_AFTER_PUNCTUATION, false);
    }

    public static boolean readUseFullscreenMode(final Resources res) {
        return res.getBoolean(R.bool.config_use_fullscreen_mode);
    }

    public static boolean readShowSetupWizardIcon(final SharedPreferences prefs,
                                                  final Context context) {
        if (!prefs.contains(PREF_SHOW_SETUP_WIZARD_ICON)) {
            final ApplicationInfo appInfo = context.getApplicationInfo();
            final boolean isApplicationInSystemImage =
                    (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            // Default value
            return !isApplicationInSystemImage;
        }
        return prefs.getBoolean(PREF_SHOW_SETUP_WIZARD_ICON, false);
    }

    public static boolean readOneHandedModeEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_ONE_HANDED_MODE, false);
    }

    public void writeOneHandedModeEnabled(final boolean enabled) {
        mPrefs.edit().putBoolean(PREF_ONE_HANDED_MODE, enabled).apply();
    }

    @SuppressLint("RtlHardcoded")
    public static int readOneHandedModeGravity(final SharedPreferences prefs) {
        return prefs.getInt(PREF_ONE_HANDED_GRAVITY, Gravity.LEFT);
    }

    public void writeOneHandedModeGravity(final int gravity) {
        mPrefs.edit().putInt(PREF_ONE_HANDED_GRAVITY, gravity).apply();
    }

    public static boolean readHasHardwareKeyboard(final Configuration conf) {
        // The standard way of finding out whether we have a hardware keyboard. This code is taken
        // from InputMethodService#onEvaluateInputShown, which canonically determines this.
        // In a nutshell, we have a keyboard if the configuration says the type of hardware keyboard
        // is NOKEYS and if it's not hidden (e.g. folded inside the device).
        return conf.keyboard != Configuration.KEYBOARD_NOKEYS
                && conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    public static void writeEmojiRecentKeys(final SharedPreferences prefs, String str) {
        prefs.edit().putString(PREF_EMOJI_RECENT_KEYS, str).apply();
    }

    public static String readEmojiRecentKeys(final SharedPreferences prefs) {
        return prefs.getString(PREF_EMOJI_RECENT_KEYS, "");
    }

    public static void writeLastShownEmojiCategoryId(
            final SharedPreferences prefs, final int categoryId) {
        prefs.edit().putInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, categoryId).apply();
    }

    public static int readLastShownEmojiCategoryId(
            final SharedPreferences prefs, final int defValue) {
        return prefs.getInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID, defValue);
    }

    public static void writeLastShownEmojiCategoryPageId(
            final SharedPreferences prefs, final int categoryId) {
        prefs.edit().putInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, categoryId).apply();
    }

    public static int readLastShownEmojiCategoryPageId(
            final SharedPreferences prefs, final int defValue) {
        return prefs.getInt(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID, defValue);
    }

    public static String readPinnedClipString(final SharedPreferences prefs) {
        return prefs.getString(PREF_PINNED_CLIPS, "");
    }

    public static void writePinnedClipString(final SharedPreferences prefs, final String clips) {
        prefs.edit().putString(PREF_PINNED_CLIPS, clips).apply();
    }

    public static List<String> readPinnedKeys(final SharedPreferences prefs) {
        final String pinnedKeysString = prefs.getString(Settings.PREF_PINNED_KEYS, "");
        if (pinnedKeysString.isEmpty())
            return new ArrayList<>();
        return Arrays.asList(pinnedKeysString.split(";"));
    }

    public static void addPinnedKey(final SharedPreferences prefs, final String key) {
        final LinkedHashSet<String> keys = new LinkedHashSet<>(readPinnedKeys(prefs));
        keys.add(key);
        prefs.edit().putString(Settings.PREF_PINNED_KEYS, String.join(";", keys)).apply();
    }

    public static void removePinnedKey(final SharedPreferences prefs, final String key) {
        final LinkedHashSet<String> keys = new LinkedHashSet<>(readPinnedKeys(prefs));
        keys.remove(key);
        prefs.edit().putString(Settings.PREF_PINNED_KEYS, String.join(";", keys)).apply();
    }

    public static int readMoreMoreKeysPref(final SharedPreferences prefs) {
        return switch (prefs.getString(Settings.PREF_MORE_MORE_KEYS, "normal")) {
            case "all" -> LocaleKeyTextsKt.MORE_KEYS_ALL;
            case "more" -> LocaleKeyTextsKt.MORE_KEYS_MORE;
            default -> LocaleKeyTextsKt.MORE_KEYS_NORMAL;
        };
    }

    public static List<Locale> getSecondaryLocales(final SharedPreferences prefs, final String mainLocaleString) {
        final String localesString = prefs.getString(PREF_SECONDARY_LOCALES_PREFIX + mainLocaleString.toLowerCase(Locale.ROOT), "");

        final ArrayList<Locale> locales = new ArrayList<>();
        for (String locale : localesString.split(";")) {
            if (locale.isEmpty()) continue;
            locales.add(LocaleUtils.constructLocaleFromString(locale));
        }
        return locales;
    }

    public static void setSecondaryLocales(final SharedPreferences prefs, final String mainLocaleString, final List<String> locales) {
        if (locales.isEmpty()) {
            prefs.edit().putString(PREF_SECONDARY_LOCALES_PREFIX + mainLocaleString.toLowerCase(Locale.ROOT), "").apply();
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (String locale : locales) {
            sb.append(";").append(locale);
        }
        prefs.edit().putString(PREF_SECONDARY_LOCALES_PREFIX + mainLocaleString.toLowerCase(Locale.ROOT), sb.toString()).apply();
    }

    public static Colors getColorsForCurrentTheme(final Context context, final SharedPreferences prefs) {
        boolean isNight = ResourceUtils.isNight(context.getResources());
        if (prefs.getBoolean(PREF_FORCE_OPPOSITE_THEME, false)) isNight = !isNight;
        final String themeColors = (isNight && prefs.getBoolean(PREF_THEME_DAY_NIGHT, context.getResources().getBoolean(R.bool.day_night_default)))
                ? prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARKER)
                : prefs.getString(Settings.PREF_THEME_COLORS, KeyboardTheme.THEME_LIGHT);
        final String themeStyle = prefs.getString(Settings.PREF_THEME_STYLE, KeyboardTheme.STYLE_MATERIAL);

        return KeyboardTheme.getThemeColors(themeColors, themeStyle, context, prefs);
    }

    public static int readUserColor(final SharedPreferences prefs, final Context context, final String colorName, final boolean isNight) {
        final String pref = getColorPref(colorName, isNight);
        if (prefs.getBoolean(pref + PREF_AUTO_USER_COLOR_SUFFIX, true)) {
            return determineAutoColor(prefs, context, colorName, isNight);
        }
        if (prefs.contains(pref))
            return prefs.getInt(pref, Color.GRAY);
        else return determineAutoColor(prefs, context, colorName, isNight);
    }

    public static String getColorPref(final String color, final boolean isNight) {
        return (isNight ? PREF_THEME_USER_COLOR_NIGHT_PREFIX : PREF_THEME_USER_COLOR_PREFIX) + color;
    }

    private static int determineAutoColor(final SharedPreferences prefs, final Context context, final String color, final boolean isNight) {
        switch (color) {
            case PREF_COLOR_ACCENT_SUFFIX:
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                    // try determining accent color on Android 10 & 11, accent is not available in resources
                    final Context wrapper = new ContextThemeWrapper(context, android.R.style.Theme_DeviceDefault);
                    final TypedValue value = new TypedValue();
                    if (wrapper.getTheme().resolveAttribute(android.R.attr.colorAccent, value, true))
                        return value.data;
                }
                return ContextCompat.getColor(getDayNightContext(context, isNight), R.color.accent);
            case PREF_COLOR_TEXT_SUFFIX:
                // base it on background color, and not key, because it's also used for suggestions
                final int background = readUserColor(prefs, context, PREF_COLOR_BACKGROUND_SUFFIX, isNight);
                if (ColorUtilKt.isBrightColor(background)) {
                    // but if key borders are enabled, we still want reasonable contrast
                    if (!prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false)
                            || ColorUtilKt.isGoodContrast(Color.BLACK, readUserColor(prefs, context, PREF_COLOR_KEYS_SUFFIX, isNight)))
                        return Color.BLACK;
                    else
                        return Color.GRAY;
                }
                else return Color.WHITE;
            case PREF_COLOR_HINT_TEXT_SUFFIX:
                if (ColorUtilKt.isBrightColor(readUserColor(prefs, context, PREF_COLOR_KEYS_SUFFIX, isNight))) return Color.DKGRAY;
                else return readUserColor(prefs, context, PREF_COLOR_TEXT_SUFFIX, isNight);
            case PREF_COLOR_KEYS_SUFFIX:
                return ColorUtilKt.brightenOrDarken(readUserColor(prefs, context, PREF_COLOR_BACKGROUND_SUFFIX, isNight), isNight);
            case PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX:
                return ColorUtilKt.brightenOrDarken(readUserColor(prefs, context, PREF_COLOR_KEYS_SUFFIX, isNight), true);
            case PREF_COLOR_SPACEBAR_SUFFIX:
                return readUserColor(prefs, context, PREF_COLOR_KEYS_SUFFIX, isNight);
            case PREF_COLOR_SPACEBAR_TEXT_SUFFIX:
                final int spacebar = readUserColor(prefs, context, PREF_COLOR_SPACEBAR_SUFFIX, isNight);
                final int hintText = readUserColor(prefs, context, PREF_COLOR_HINT_TEXT_SUFFIX, isNight);
                if (ColorUtilKt.isGoodContrast(hintText, spacebar)) return hintText & 0x80FFFFFF; // add some transparency
                final int text = readUserColor(prefs, context, PREF_COLOR_TEXT_SUFFIX, isNight);
                if (ColorUtilKt.isGoodContrast(text, spacebar)) return text & 0x80FFFFFF;
                if (ColorUtilKt.isBrightColor(spacebar)) return Color.BLACK & 0x80FFFFFF;
                else return Color.WHITE & 0x80FFFFFF;
            case PREF_COLOR_BACKGROUND_SUFFIX:
            default:
                return ContextCompat.getColor(getDayNightContext(context, isNight), R.color.keyboard_background);
        }
    }

    public static Context getDayNightContext(final Context context, final boolean wantNight) {
        final boolean isNight = (context.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        if (isNight == wantNight)
            return context;
        final Configuration config = new Configuration(context.getResources().getConfiguration());
        final int night = config.uiMode & Configuration.UI_MODE_NIGHT_MASK;
        final int uiModeWithNightBitsZero = config.uiMode - night;
        config.uiMode = uiModeWithNightBitsZero + (wantNight ? Configuration.UI_MODE_NIGHT_YES : Configuration.UI_MODE_NIGHT_NO);
        final ContextThemeWrapper wrapper = new ContextThemeWrapper(context, R.style.platformActivityTheme);
        wrapper.applyOverrideConfiguration(config);
        return wrapper;
    }

}
