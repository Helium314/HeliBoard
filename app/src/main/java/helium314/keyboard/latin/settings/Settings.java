/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.ContextThemeWrapper;
import android.view.Gravity;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.content.ContextCompat;
import androidx.preference.PreferenceManager;

import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.keyboard.KeyboardTheme;
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfosKt;
import helium314.keyboard.latin.AudioAndHapticFeedbackManager;
import helium314.keyboard.latin.InputAttributes;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.common.LocaleUtils;
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils;
import helium314.keyboard.latin.utils.ColorUtilKt;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.utils.JniUtils;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.ResourceUtils;
import helium314.keyboard.latin.utils.RunInLocaleKt;
import helium314.keyboard.latin.utils.StatsUtils;
import helium314.keyboard.latin.utils.SubtypeSettingsKt;
import helium314.keyboard.latin.utils.ToolbarKey;
import helium314.keyboard.latin.utils.ToolbarUtilsKt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.locks.ReentrantLock;

public final class Settings implements SharedPreferences.OnSharedPreferenceChangeListener {
    private static final String TAG = Settings.class.getSimpleName();
    // Settings screens
    public static final String SCREEN_DEBUG = "screen_debug";
    public static final String SCREEN_GESTURE = "screen_gesture";

    // theme-related stuff
    public static final String PREF_THEME_STYLE = "theme_style";
    public static final String PREF_THEME_COLORS = "theme_colors";
    public static final String PREF_THEME_COLORS_NIGHT = "theme_colors_night";
    public static final String PREF_THEME_KEY_BORDERS = "theme_key_borders";
    public static final String PREF_THEME_DAY_NIGHT = "theme_auto_day_night";
    public static final String PREF_THEME_USER_COLOR_PREFIX = "theme_color_";
    public static final String PREF_THEME_USER_COLOR_NIGHT_PREFIX = "theme_dark_color_";
    public static final String PREF_COLOR_KEYS_SUFFIX = "keys";
    public static final String PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX = "functional_keys";
    public static final String PREF_COLOR_SPACEBAR_SUFFIX = "spacebar";
    public static final String PREF_COLOR_SPACEBAR_TEXT_SUFFIX = "spacebar_text";
    public static final String PREF_COLOR_ACCENT_SUFFIX = "accent";
    public static final String PREF_COLOR_GESTURE_SUFFIX = "gesture";
    public static final String PREF_COLOR_TEXT_SUFFIX = "text";
    public static final String PREF_COLOR_SUGGESTION_TEXT_SUFFIX = "suggestion_text";
    public static final String PREF_COLOR_HINT_TEXT_SUFFIX = "hint_text";
    public static final String PREF_COLOR_BACKGROUND_SUFFIX = "background";
    public static final String PREF_AUTO_USER_COLOR_SUFFIX = "_auto";

    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
    public static final String PREF_AUTO_CORRECTION = "auto_correction";
    public static final String PREF_MORE_AUTO_CORRECTION = "more_auto_correction";
    public static final String PREF_AUTO_CORRECTION_CONFIDENCE = "auto_correction_confidence";
    public static final String PREF_CENTER_SUGGESTION_TEXT_TO_ENTER = "center_suggestion_text_to_enter";
    public static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    public static final String PREF_ALWAYS_SHOW_SUGGESTIONS = "always_show_suggestions";
    public static final String PREF_KEY_USE_PERSONALIZED_DICTS = "use_personalized_dicts";
    public static final String PREF_KEY_USE_DOUBLE_SPACE_PERIOD = "use_double_space_period";
    public static final String PREF_BLOCK_POTENTIALLY_OFFENSIVE = "block_potentially_offensive";
    public static final String PREF_LANGUAGE_SWITCH_KEY = "language_switch_key";
    public static final String PREF_SHOW_EMOJI_KEY = "show_emoji_key";
    public static final String PREF_VARIABLE_TOOLBAR_DIRECTION = "var_toolbar_direction";
    public static final String PREF_ADDITIONAL_SUBTYPES = "additional_subtypes";
    public static final String PREF_ENABLE_SPLIT_KEYBOARD = "split_keyboard";
    public static final String PREF_SPLIT_SPACER_SCALE = "split_spacer_scale";
    public static final String PREF_KEYBOARD_HEIGHT_SCALE = "keyboard_height_scale";
    public static final String PREF_BOTTOM_PADDING_SCALE = "bottom_padding_scale";
    public static final String PREF_SPACE_HORIZONTAL_SWIPE = "horizontal_space_swipe";
    public static final String PREF_SPACE_VERTICAL_SWIPE = "vertical_space_swipe";
    public static final String PREF_DELETE_SWIPE = "delete_swipe";
    public static final String PREF_AUTOSPACE_AFTER_PUNCTUATION = "autospace_after_punctuation";
    public static final String PREF_ALWAYS_INCOGNITO_MODE = "always_incognito_mode";
    public static final String PREF_BIGRAM_PREDICTIONS = "next_word_prediction";
    public static final String PREF_GESTURE_INPUT = "gesture_input";
    public static final String PREF_VIBRATION_DURATION_SETTINGS = "vibration_duration_settings";
    public static final String PREF_KEYPRESS_SOUND_VOLUME = "keypress_sound_volume";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "key_longpress_timeout";
    public static final String PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = "enable_emoji_alt_physical_key";
    public static final String PREF_GESTURE_PREVIEW_TRAIL = "gesture_preview_trail";
    public static final String PREF_GESTURE_FLOATING_PREVIEW_TEXT = "gesture_floating_preview_text";
    public static final String PREF_GESTURE_SPACE_AWARE = "gesture_space_aware";
    public static final String PREF_SHOW_SETUP_WIZARD_ICON = "show_setup_wizard_icon";
    public static final String PREF_USE_CONTACTS = "use_contacts";
    public static final String PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD = "long_press_symbols_for_numpad";

    // one-handed mode gravity, enablement and scale, stored separately per orientation
    public static final String PREF_ONE_HANDED_MODE_PREFIX = "one_handed_mode_enabled_p_";
    public static final String PREF_ONE_HANDED_GRAVITY_PREFIX = "one_handed_mode_gravity_p_";
    public static final String PREF_ONE_HANDED_SCALE_PREFIX = "one_handed_mode_scale_p_";

    public static final String PREF_SHOW_NUMBER_ROW = "show_number_row";
    public static final String PREF_LOCALIZED_NUMBER_ROW = "localized_number_row";

    public static final String PREF_SHOW_HINTS = "show_hints";
    public static final String PREF_POPUP_KEYS_ORDER = "popup_keys_order";
    public static final String PREF_POPUP_KEYS_LABELS_ORDER = "popup_keys_labels_order";
    public static final String PREF_SHOW_POPUP_HINTS = "show_popup_hints";
    public static final String PREF_MORE_POPUP_KEYS = "more_popup_keys";

    public static final String PREF_SPACE_TO_CHANGE_LANG = "prefs_long_press_keyboard_to_change_lang";

    public static final String PREF_ENABLE_CLIPBOARD_HISTORY = "enable_clipboard_history";
    public static final String PREF_CLIPBOARD_HISTORY_RETENTION_TIME = "clipboard_history_retention_time";

    public static final String PREF_SECONDARY_LOCALES_PREFIX = "secondary_locales_";
    public static final String PREF_ADD_TO_PERSONAL_DICTIONARY = "add_to_personal_dictionary";
    public static final String PREF_NAVBAR_COLOR = "navbar_color";
    public static final String PREF_NARROW_KEY_GAPS = "narrow_key_gaps";
    public static final String PREF_ENABLED_SUBTYPES = "enabled_subtypes";
    public static final String PREF_SELECTED_SUBTYPE = "selected_subtype";
    public static final String PREF_USE_SYSTEM_LOCALES = "use_system_locales";
    public static final String PREF_URL_DETECTION = "url_detection";
    public static final String PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG = "dont_show_missing_dict_dialog";
    public static final String PREF_PINNED_TOOLBAR_KEYS = "pinned_toolbar_keys";
    public static final String PREF_TOOLBAR_KEYS = "toolbar_keys";
    public static final String PREF_CLIPBOARD_TOOLBAR_KEYS = "clipboard_toolbar_keys";

    // Emoji
    public static final String PREF_EMOJI_RECENT_KEYS = "emoji_recent_keys";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = "last_shown_emoji_category_page_id";

    public static final String PREF_PINNED_CLIPS = "pinned_clips";
    public static final String PREF_VERSION_CODE = "version_code";
    public static final String PREF_SHOW_ALL_COLORS = "show_all_colors";
    public static final String PREF_LIBRARY_CHECKSUM = "lib_checksum";

    private static final float UNDEFINED_PREFERENCE_VALUE_FLOAT = -1.0f;
    private static final int UNDEFINED_PREFERENCE_VALUE_INT = -1;

    private Context mContext;
    private SharedPreferences mPrefs;
    private SettingsValues mSettingsValues;
    private final ReentrantLock mSettingsValuesLock = new ReentrantLock();

    // static cache for background images to avoid potentially slow reload on every settings reload
    private static Drawable sCachedBackgroundDay;
    private static Drawable sCachedBackgroundNight;

    private static final Settings sInstance = new Settings();

    // preferences that are not used in SettingsValues and thus should not trigger reload when changed
    private static final HashSet<String> dontReloadOnChanged = new HashSet<>() {{
        add(PREF_PINNED_CLIPS);
        add(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID);
        add(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID);
        add(PREF_EMOJI_RECENT_KEYS);
        add(PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG);
        add(PREF_SHOW_ALL_COLORS);
        add(PREF_SELECTED_SUBTYPE);
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
        if (PREF_ADDITIONAL_SUBTYPES.equals(key)) {
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
            Log.i(TAG, "loadSettings");
            mSettingsValues = RunInLocaleKt.runInLocale(context, locale,
                    ctx -> new SettingsValues(ctx, prefs, ctx.getResources(), inputAttributes));
        } finally {
            mSettingsValuesLock.unlock();
        }
    }

    public void stopListener() {
        mPrefs.unregisterOnSharedPreferenceChangeListener(this);
    }

    public void startListener() {
        mPrefs.registerOnSharedPreferenceChangeListener(this);
    }

    // TODO: Remove this method and add proxy method to SettingsValues.
    public SettingsValues getCurrent() {
        return mSettingsValues;
    }

    public static int readScreenMetrics(final Resources res) {
        return res.getInteger(R.integer.config_screen_metrics);
    }

    // Accessed from the settings interface, hence public
    public static boolean readKeypressSoundEnabled(final SharedPreferences prefs, final Resources res) {
        return prefs.getBoolean(PREF_SOUND_ON, res.getBoolean(R.bool.config_default_sound_enabled));
    }

    public static boolean readVibrationEnabled(final SharedPreferences prefs, final Resources res) {
        return prefs.getBoolean(PREF_VIBRATE_ON, res.getBoolean(R.bool.config_default_vibration_enabled))
                && AudioAndHapticFeedbackManager.getInstance().hasVibrator();
    }

    public static boolean readAutoCorrectEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_AUTO_CORRECTION, true);
    }

    public static boolean readMoreAutoCorrectEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_MORE_AUTO_CORRECTION, true);
    }

    public void toggleAutoCorrect() {
        mPrefs.edit().putBoolean(Settings.PREF_AUTO_CORRECTION, !readAutoCorrectEnabled(mPrefs)).apply();
    }

    public static String readAutoCorrectConfidence(final SharedPreferences prefs, final Resources res) {
        return prefs.getString(PREF_AUTO_CORRECTION_CONFIDENCE,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
    }

    public static boolean readCenterSuggestionTextToEnter(final SharedPreferences prefs, final Resources res) {
        return prefs.getBoolean(PREF_CENTER_SUGGESTION_TEXT_TO_ENTER, res.getBoolean(R.bool.config_center_suggestion_text_to_enter));
    }

    public static boolean readBlockPotentiallyOffensive(final SharedPreferences prefs, final Resources res) {
        return prefs.getBoolean(PREF_BLOCK_POTENTIALLY_OFFENSIVE,
                res.getBoolean(R.bool.config_block_potentially_offensive));
    }

    public static boolean readGestureInputEnabled(final SharedPreferences prefs) {
        return JniUtils.sHaveGestureLib && prefs.getBoolean(PREF_GESTURE_INPUT, true);
    }

    public static boolean readFromBuildConfigIfToShowKeyPreviewPopupOption(final Resources res) {
        return res.getBoolean(R.bool.config_enable_show_key_preview_popup_option);
    }

    public static boolean readKeyPreviewPopupEnabled(final SharedPreferences prefs, final Resources res) {
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

    public void toggleAlwaysIncognitoMode() {
        mPrefs.edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, !readAlwaysIncognitoMode(mPrefs)).apply();
    }


    public static String readPrefAdditionalSubtypes(final SharedPreferences prefs, final Resources res) {
        final String predefinedPrefSubtypes = AdditionalSubtypeUtils.createPrefSubtypes(
                res.getStringArray(R.array.predefined_subtypes));
        return prefs.getString(PREF_ADDITIONAL_SUBTYPES, predefinedPrefSubtypes);
    }

    public static void writePrefAdditionalSubtypes(final SharedPreferences prefs, final String prefSubtypes) {
        prefs.edit().putString(PREF_ADDITIONAL_SUBTYPES, prefSubtypes).apply();
    }

    public static float readKeypressSoundVolume(final SharedPreferences prefs, final Resources res) {
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

    public static int readKeyLongpressTimeout(final SharedPreferences prefs, final Resources res) {
        final int milliseconds = prefs.getInt(
                PREF_KEY_LONGPRESS_TIMEOUT, UNDEFINED_PREFERENCE_VALUE_INT);
        return (milliseconds != UNDEFINED_PREFERENCE_VALUE_INT) ? milliseconds
                : readDefaultKeyLongpressTimeout(res);
    }

    public static int readDefaultKeyLongpressTimeout(final Resources res) {
        return res.getInteger(R.integer.config_default_longpress_key_timeout);
    }

    public static int readKeypressVibrationDuration(final SharedPreferences prefs, final Resources res) {
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

    public static int readHorizontalSpaceSwipe(final SharedPreferences prefs) {
        return switch (prefs.getString(PREF_SPACE_HORIZONTAL_SWIPE, "none")) {
            case "move_cursor" -> KeyboardActionListener.SWIPE_MOVE_CURSOR;
            case "switch_language" -> KeyboardActionListener.SWIPE_SWITCH_LANGUAGE;
            default -> KeyboardActionListener.SWIPE_NO_ACTION;
        };
    }

    public static int readVerticalSpaceSwipe(final SharedPreferences prefs) {
        return switch (prefs.getString(PREF_SPACE_VERTICAL_SWIPE, "none")) {
            case "move_cursor" -> KeyboardActionListener.SWIPE_MOVE_CURSOR;
            case "switch_language" -> KeyboardActionListener.SWIPE_SWITCH_LANGUAGE;
            default -> KeyboardActionListener.SWIPE_NO_ACTION;
        };
    }

    public static boolean readDeleteSwipeEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_DELETE_SWIPE, true);
    }

    public static boolean readAutospaceAfterPunctuationEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_AUTOSPACE_AFTER_PUNCTUATION, false);
    }

    public static boolean readFullscreenModeAllowed(final Resources res) {
        return res.getBoolean(R.bool.config_fullscreen_mode_allowed);
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

    public static boolean readOneHandedModeEnabled(final SharedPreferences prefs, final boolean portrait) {
        return prefs.getBoolean(PREF_ONE_HANDED_MODE_PREFIX + portrait, false);
    }

    public void writeOneHandedModeEnabled(final boolean enabled) {
        mPrefs.edit().putBoolean(PREF_ONE_HANDED_MODE_PREFIX +
                (getCurrent().mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT), enabled).apply();
    }

    public static float readOneHandedModeScale(final SharedPreferences prefs, final boolean portrait) {
        return prefs.getFloat(PREF_ONE_HANDED_SCALE_PREFIX + portrait, 1f);
    }

    public void writeOneHandedModeScale(final Float scale) {
        mPrefs.edit().putFloat(PREF_ONE_HANDED_SCALE_PREFIX +
                (getCurrent().mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT), scale).apply();
    }

    @SuppressLint("RtlHardcoded")
    public static int readOneHandedModeGravity(final SharedPreferences prefs, final boolean portrait) {
        return prefs.getInt(PREF_ONE_HANDED_GRAVITY_PREFIX + portrait, Gravity.LEFT);
    }

    public void writeOneHandedModeGravity(final int gravity) {
        mPrefs.edit().putInt(PREF_ONE_HANDED_GRAVITY_PREFIX +
                (getCurrent().mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT), gravity).apply();
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

    public static String readPinnedClipString(final Context context) {
        try {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            return prefs.getString(PREF_PINNED_CLIPS, "");
        } catch (final IllegalStateException e) {
            // SharedPreferences in credential encrypted storage are not available until after user is unlocked
            return "";
        }
    }

    public static void writePinnedClipString(final Context context, final String clips) {
        try {
            final SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
            prefs.edit().putString(PREF_PINNED_CLIPS, clips).apply();
        } catch (final IllegalStateException e) {
            // SharedPreferences in credential encrypted storage are not available until after user is unlocked
        }
    }

    public static ArrayList<ToolbarKey> readPinnedKeys(final SharedPreferences prefs) {
        final ArrayList<ToolbarKey> list = new ArrayList<>();
        for (final String key : prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, "").split(";")) {
            try {
                list.add(ToolbarKey.valueOf(key));
            } catch (IllegalArgumentException ignored) { } // may happen if toolbar key is removed from app
        }
        return list;
    }

    public static void addPinnedKey(final SharedPreferences prefs, final ToolbarKey key) {
        final ArrayList<ToolbarKey> keys = readPinnedKeys(prefs);
        if (keys.contains(key)) return;
        keys.add(key);
        prefs.edit().putString(Settings.PREF_PINNED_TOOLBAR_KEYS, ToolbarUtilsKt.toToolbarKeyString(keys)).apply();
    }

    public static void removePinnedKey(final SharedPreferences prefs, final ToolbarKey key) {
        final ArrayList<ToolbarKey> keys = readPinnedKeys(prefs);
        keys.remove(key);
        prefs.edit().putString(Settings.PREF_PINNED_TOOLBAR_KEYS, ToolbarUtilsKt.toToolbarKeyString(keys)).apply();
    }

    public static int readMorePopupKeysPref(final SharedPreferences prefs) {
        return switch (prefs.getString(Settings.PREF_MORE_POPUP_KEYS, "normal")) {
            case "all" -> LocaleKeyboardInfosKt.POPUP_KEYS_ALL;
            case "more" -> LocaleKeyboardInfosKt.POPUP_KEYS_MORE;
            default -> LocaleKeyboardInfosKt.POPUP_KEYS_NORMAL;
        };
    }

    @Nullable public static Drawable readUserBackgroundImage(final Context context, final boolean night) {
        if (night && sCachedBackgroundNight != null) return sCachedBackgroundNight;
        if (!night && sCachedBackgroundDay != null) return sCachedBackgroundDay;
        final File image = getCustomBackgroundFile(context, night);
        if (!image.isFile()) return null;
        try {
            if (night) {
                sCachedBackgroundNight = new BitmapDrawable(context.getResources(), BitmapFactory.decodeFile(image.getAbsolutePath()));
                return sCachedBackgroundNight;
            } else {
                sCachedBackgroundDay = new BitmapDrawable(context.getResources(), BitmapFactory.decodeFile(image.getAbsolutePath()));
                return sCachedBackgroundDay;
            }
        } catch (Exception e) {
            return null;
        }
    }

    public static File getCustomBackgroundFile(final Context context, final boolean night) {
        return new File(DeviceProtectedUtils.getFilesDir(context), "custom_background_image" + (night ? "_night" : ""));
    }

    public static boolean readDayNightPref(final SharedPreferences prefs, final Resources res) {
        return prefs.getBoolean(PREF_THEME_DAY_NIGHT, res.getBoolean(R.bool.day_night_default));
    }

    public static void clearCachedBackgroundImages() {
        sCachedBackgroundDay = null;
        sCachedBackgroundNight = null;
    }

    public static List<Locale> getSecondaryLocales(final SharedPreferences prefs, final Locale mainLocale) {
        final String localesString = prefs.getString(PREF_SECONDARY_LOCALES_PREFIX + mainLocale.toLanguageTag(), "");

        final ArrayList<Locale> locales = new ArrayList<>();
        for (String languageTag : localesString.split(";")) {
            if (languageTag.isEmpty()) continue;
            locales.add(LocaleUtils.constructLocale(languageTag));
        }
        return locales;
    }

    public static void setSecondaryLocales(final SharedPreferences prefs, final Locale mainLocale, final List<Locale> locales) {
        if (locales.isEmpty()) {
            prefs.edit().putString(PREF_SECONDARY_LOCALES_PREFIX + mainLocale.toLanguageTag(), "").apply();
            return;
        }
        final StringBuilder sb = new StringBuilder();
        for (Locale locale : locales) {
            sb.append(";").append(locale.toLanguageTag());
        }
        prefs.edit().putString(PREF_SECONDARY_LOCALES_PREFIX + mainLocale.toLanguageTag(), sb.toString()).apply();
    }

    public static Colors getColorsForCurrentTheme(final Context context, final SharedPreferences prefs) {
        boolean isNight = ResourceUtils.isNight(context.getResources());
        if (ColorsSettingsFragment.Companion.getForceOppositeTheme()) isNight = !isNight;
        final String themeColors = (isNight && readDayNightPref(prefs, context.getResources()))
                ? prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, KeyboardTheme.THEME_DARK)
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
            case PREF_COLOR_GESTURE_SUFFIX:
                return readUserColor(prefs, context, PREF_COLOR_ACCENT_SUFFIX, isNight);
            case PREF_COLOR_SUGGESTION_TEXT_SUFFIX:
                return readUserColor(prefs, context, PREF_COLOR_TEXT_SUFFIX, isNight);
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
        final boolean isNight = ResourceUtils.isNight(context.getResources());
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

    public boolean isTablet() {
        return mContext.getResources().getInteger(R.integer.config_screen_metrics) >= 3;
    }

    public int getStringResIdByName(final String name) {
        return mContext.getResources().getIdentifier(name, "string", mContext.getPackageName());
    }

    public String getInLocale(@StringRes final int resId, final Locale locale) {
        return RunInLocaleKt.runInLocale(mContext, locale, (ctx) -> ctx.getString(resId));
    }
}
