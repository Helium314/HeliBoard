/*
 * Copyright (C) 2013 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.settings;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.view.ContextThemeWrapper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import helium314.keyboard.keyboard.KeyboardActionListener;
import helium314.keyboard.keyboard.KeyboardTheme;
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyboardInfosKt;
import helium314.keyboard.latin.AudioAndHapticFeedbackManager;
import helium314.keyboard.latin.InputAttributes;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.Colors;
import helium314.keyboard.latin.common.LocaleUtils;
import helium314.keyboard.latin.utils.AdditionalSubtypeUtils;
import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.utils.KtxKt;
import helium314.keyboard.latin.utils.LayoutType;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.ResourceUtils;
import helium314.keyboard.latin.utils.RunInLocaleKt;
import helium314.keyboard.latin.utils.StatsUtils;
import helium314.keyboard.latin.utils.SubtypeSettingsKt;
import helium314.keyboard.latin.utils.ToolbarKey;
import helium314.keyboard.latin.utils.ToolbarUtilsKt;
import helium314.keyboard.settings.SettingsActivity;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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
    public static final String PREF_ICON_STYLE = "icon_style";
    public static final String PREF_THEME_COLORS = "theme_colors";
    public static final String PREF_THEME_COLORS_NIGHT = "theme_colors_night";
    public static final String PREF_THEME_KEY_BORDERS = "theme_key_borders";
    public static final String PREF_THEME_DAY_NIGHT = "theme_auto_day_night";
    public static final String PREF_USER_COLORS_PREFIX = "user_colors_";
    public static final String PREF_USER_ALL_COLORS_PREFIX = "user_all_colors_";
    public static final String PREF_USER_MORE_COLORS_PREFIX = "user_more_colors_";

    public static final String PREF_CUSTOM_ICON_NAMES = "custom_icon_names";
    public static final String PREF_TOOLBAR_CUSTOM_KEY_CODES = "toolbar_custom_key_codes";
    public static final String PREF_LAYOUT_PREFIX = "layout_";

    public static final String PREF_AUTO_CAP = "auto_cap";
    public static final String PREF_VIBRATE_ON = "vibrate_on";
    public static final String PREF_VIBRATE_IN_DND_MODE = "vibrate_in_dnd_mode";
    public static final String PREF_SOUND_ON = "sound_on";
    public static final String PREF_POPUP_ON = "popup_on";
    public static final String PREF_AUTO_CORRECTION = "auto_correction";
    public static final String PREF_MORE_AUTO_CORRECTION = "more_auto_correction";
    public static final String PREF_AUTO_CORRECTION_CONFIDENCE = "auto_correction_confidence";
    public static final String PREF_AUTOCORRECT_SHORTCUTS = "autocorrect_shortcuts";
    public static final String PREF_CENTER_SUGGESTION_TEXT_TO_ENTER = "center_suggestion_text_to_enter";
    public static final String PREF_SHOW_SUGGESTIONS = "show_suggestions";
    public static final String PREF_ALWAYS_SHOW_SUGGESTIONS = "always_show_suggestions";
    public static final String PREF_KEY_USE_PERSONALIZED_DICTS = "use_personalized_dicts";
    public static final String PREF_KEY_USE_DOUBLE_SPACE_PERIOD = "use_double_space_period";
    public static final String PREF_BLOCK_POTENTIALLY_OFFENSIVE = "block_potentially_offensive";
    public static final String PREF_SHOW_LANGUAGE_SWITCH_KEY = "show_language_switch_key";
    public static final String PREF_LANGUAGE_SWITCH_KEY = "language_switch_key";
    public static final String PREF_SHOW_EMOJI_KEY = "show_emoji_key";
    public static final String PREF_VARIABLE_TOOLBAR_DIRECTION = "var_toolbar_direction";
    public static final String PREF_ADDITIONAL_SUBTYPES = "additional_subtypes";
    public static final String PREF_ENABLE_SPLIT_KEYBOARD = "split_keyboard";
    public static final String PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE = "split_keyboard_landscape";
    public static final String PREF_SPLIT_SPACER_SCALE = "split_spacer_scale";
    public static final String PREF_SPLIT_SPACER_SCALE_LANDSCAPE = "split_spacer_scale_landscape";
    public static final String PREF_KEYBOARD_HEIGHT_SCALE = "keyboard_height_scale";
    public static final String PREF_BOTTOM_PADDING_SCALE = "bottom_padding_scale";
    public static final String PREF_BOTTOM_PADDING_SCALE_LANDSCAPE = "bottom_padding_scale_landscape";
    public static final String PREF_SIDE_PADDING_SCALE = "side_padding_scale";
    public static final String PREF_SIDE_PADDING_SCALE_LANDSCAPE = "side_padding_scale_landscape";
    public static final String PREF_FONT_SCALE = "font_scale";
    public static final String PREF_EMOJI_FONT_SCALE = "emoji_font_scale";
    public static final String PREF_SPACE_HORIZONTAL_SWIPE = "horizontal_space_swipe";
    public static final String PREF_SPACE_VERTICAL_SWIPE = "vertical_space_swipe";
    public static final String PREF_DELETE_SWIPE = "delete_swipe";
    public static final String PREF_AUTOSPACE_AFTER_PUNCTUATION = "autospace_after_punctuation";
    public static final String PREF_ALWAYS_INCOGNITO_MODE = "always_incognito_mode";
    public static final String PREF_BIGRAM_PREDICTIONS = "next_word_prediction";
    public static final String PREF_SUGGEST_CLIPBOARD_CONTENT = "suggest_clipboard_content";
    public static final String PREF_GESTURE_INPUT = "gesture_input";
    public static final String PREF_VIBRATION_DURATION_SETTINGS = "vibration_duration_settings";
    public static final String PREF_KEYPRESS_SOUND_VOLUME = "keypress_sound_volume";
    public static final String PREF_KEY_LONGPRESS_TIMEOUT = "key_longpress_timeout";
    public static final String PREF_ENABLE_EMOJI_ALT_PHYSICAL_KEY = "enable_emoji_alt_physical_key";
    public static final String PREF_GESTURE_PREVIEW_TRAIL = "gesture_preview_trail";
    public static final String PREF_GESTURE_FLOATING_PREVIEW_TEXT = "gesture_floating_preview_text";
    public static final String PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC = "gesture_floating_preview_dynamic";
    public static final String PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM = "gesture_dynamic_preview_follow_system";
    public static final String PREF_GESTURE_SPACE_AWARE = "gesture_space_aware";
    public static final String PREF_GESTURE_FAST_TYPING_COOLDOWN = "gesture_fast_typing_cooldown";
    public static final String PREF_GESTURE_TRAIL_FADEOUT_DURATION = "gesture_trail_fadeout_duration";
    public static final String PREF_SHOW_SETUP_WIZARD_ICON = "show_setup_wizard_icon";
    public static final String PREF_USE_CONTACTS = "use_contacts";
    public static final String PREFS_LONG_PRESS_SYMBOLS_FOR_NUMPAD = "long_press_symbols_for_numpad";

    // one-handed mode gravity, enablement and scale, stored separately per orientation
    public static final String PREF_ONE_HANDED_MODE_PREFIX = "one_handed_mode_enabled_p_";
    public static final String PREF_ONE_HANDED_GRAVITY_PREFIX = "one_handed_mode_gravity_p_";
    public static final String PREF_ONE_HANDED_SCALE_PREFIX = "one_handed_mode_scale_p_";

    public static final String PREF_SHOW_NUMBER_ROW = "show_number_row";
    public static final String PREF_LOCALIZED_NUMBER_ROW = "localized_number_row";
    public static final String PREF_SHOW_NUMBER_ROW_HINTS = "show_number_row_hints";
    public static final String PREF_CUSTOM_CURRENCY_KEY = "custom_currency_key";

    public static final String PREF_SHOW_HINTS = "show_hints";
    public static final String PREF_POPUP_KEYS_ORDER = "popup_keys_order";
    public static final String PREF_POPUP_KEYS_LABELS_ORDER = "popup_keys_labels_order";
    public static final String PREF_SHOW_POPUP_HINTS = "show_popup_hints";
    public static final String PREF_MORE_POPUP_KEYS = "more_popup_keys";

    public static final String PREF_SPACE_TO_CHANGE_LANG = "prefs_long_press_keyboard_to_change_lang";
    public static final String PREF_LANGUAGE_SWIPE_DISTANCE = "language_swipe_distance";

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
    public static final String PREF_QUICK_PIN_TOOLBAR_KEYS = "quick_pin_toolbar_keys";
    public static final String PREF_PINNED_TOOLBAR_KEYS = "pinned_toolbar_keys";
    public static final String PREF_TOOLBAR_KEYS = "toolbar_keys";
    public static final String PREF_AUTO_SHOW_TOOLBAR = "auto_show_toolbar";
    public static final String PREF_AUTO_HIDE_TOOLBAR = "auto_hide_toolbar";
    public static final String PREF_CLIPBOARD_TOOLBAR_KEYS = "clipboard_toolbar_keys";
    public static final String PREF_ABC_AFTER_EMOJI = "abc_after_emoji";
    public static final String PREF_ABC_AFTER_CLIP = "abc_after_clip";
    public static final String PREF_ABC_AFTER_SYMBOL_SPACE = "abc_after_symbol_space";
    public static final String PREF_REMOVE_REDUNDANT_POPUPS = "remove_redundant_popups";
    public static final String PREF_SPACE_BAR_TEXT = "space_bar_text";

    // Emoji
    public static final String PREF_EMOJI_MAX_SDK = "emoji_max_sdk";
    public static final String PREF_EMOJI_RECENT_KEYS = "emoji_recent_keys";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_ID = "last_shown_emoji_category_id";
    public static final String PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID = "last_shown_emoji_category_page_id";

    public static final String PREF_PINNED_CLIPS = "pinned_clips";
    public static final String PREF_VERSION_CODE = "version_code";
    public static final String PREF_LIBRARY_CHECKSUM = "lib_checksum";

    private Context mContext;
    private SharedPreferences mPrefs;
    private SettingsValues mSettingsValues;
    private final ReentrantLock mSettingsValuesLock = new ReentrantLock();

    // static cache for background images to avoid potentially slow reload on every settings reload
    private final static Drawable[] sCachedBackgroundImages = new Drawable[4];
    private static Typeface sCachedTypeface;
    private static boolean sCustomTypefaceLoaded; // to avoid repeatedly checking custom typeface file when there is no custom typeface

    private static final Settings sInstance = new Settings();

    // preferences that are not used in SettingsValues and thus should not trigger reload when changed
    private static final HashSet<String> dontReloadOnChanged = new HashSet<>() {{
        add(PREF_PINNED_CLIPS);
        add(PREF_LAST_SHOWN_EMOJI_CATEGORY_PAGE_ID);
        add(PREF_LAST_SHOWN_EMOJI_CATEGORY_ID);
        add(PREF_EMOJI_RECENT_KEYS);
        add(PREF_DONT_SHOW_MISSING_DICTIONARY_DIALOG);
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
        mPrefs = KtxKt.prefs(context);
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
            ToolbarUtilsKt.clearCustomToolbarKeyCodes();
            loadSettings(mContext, mSettingsValues.mLocale, mSettingsValues.mInputAttributes);
            StatsUtils.onLoadSettings(mSettingsValues);
        } finally {
            mSettingsValuesLock.unlock();
        }
        if (PREF_ADDITIONAL_SUBTYPES.equals(key)) {
            final String additionalSubtypes = prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES);
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

    public static boolean readVibrationEnabled(final SharedPreferences prefs) {
        return prefs.getBoolean(PREF_VIBRATE_ON, Defaults.PREF_VIBRATE_ON)
                && AudioAndHapticFeedbackManager.getInstance().hasVibrator();
    }

    public void toggleAutoCorrect() {
        final boolean oldValue = mPrefs.getBoolean(PREF_AUTO_CORRECTION, Defaults.PREF_AUTO_CORRECTION);
        mPrefs.edit().putBoolean(Settings.PREF_AUTO_CORRECTION, !oldValue).apply();
    }

    public static String readAutoCorrectConfidence(final SharedPreferences prefs, final Resources res) {
        return prefs.getString(PREF_AUTO_CORRECTION_CONFIDENCE,
                res.getString(R.string.auto_correction_threshold_mode_index_modest));
    }

    public static boolean readGestureDynamicPreviewEnabled(final SharedPreferences prefs) {
        final boolean followSystem = prefs.getBoolean(PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM, Defaults.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM);
        final boolean defValue = Defaults.PREF_GESTURE_DYNAMIC_PREVIEW_FOLLOW_SYSTEM;
        final boolean curValue = prefs.getBoolean(Settings.PREF_GESTURE_FLOATING_PREVIEW_DYNAMIC, defValue);
        return followSystem ? defValue : curValue;
    }

    public static boolean readGestureDynamicPreviewDefault(final Context context) {
        // if transitions are disabled for the system (reduced motion), moving preview should be disabled
        return android.provider.Settings.System.getFloat(
                context.getContentResolver(),
                android.provider.Settings.Global.TRANSITION_ANIMATION_SCALE,
                1.0f
        ) != 0.0f;
    }

    public static int readDefaultGestureFastTypingCooldown(final Resources res) {
        return res.getInteger(R.integer.config_gesture_static_time_threshold_after_fast_typing);
    }

    public void toggleAlwaysIncognitoMode() {
        final boolean oldValue = mPrefs.getBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, Defaults.PREF_ALWAYS_INCOGNITO_MODE);
        mPrefs.edit().putBoolean(Settings.PREF_ALWAYS_INCOGNITO_MODE, !oldValue).apply();
    }

    public static void writePrefAdditionalSubtypes(final SharedPreferences prefs, final String prefSubtypes) {
        prefs.edit().putString(PREF_ADDITIONAL_SUBTYPES, prefSubtypes).apply();
    }

    public static int readDefaultClipboardHistoryRetentionTime(final Resources res) {
        return res.getInteger(R.integer.config_clipboard_history_retention_time);
    }

    public static int readHorizontalSpaceSwipe(final SharedPreferences prefs) {
        return switch (prefs.getString(PREF_SPACE_HORIZONTAL_SWIPE, Defaults.PREF_SPACE_HORIZONTAL_SWIPE)) {
            case "move_cursor" -> KeyboardActionListener.SWIPE_MOVE_CURSOR;
            case "switch_language" -> KeyboardActionListener.SWIPE_SWITCH_LANGUAGE;
            case "toggle_numpad" -> KeyboardActionListener.SWIPE_TOGGLE_NUMPAD;
            default -> KeyboardActionListener.SWIPE_NO_ACTION;
        };
    }

    public static int readVerticalSpaceSwipe(final SharedPreferences prefs) {
        return switch (prefs.getString(PREF_SPACE_VERTICAL_SWIPE, Defaults.PREF_SPACE_VERTICAL_SWIPE)) {
            case "move_cursor" -> KeyboardActionListener.SWIPE_MOVE_CURSOR;
            case "switch_language" -> KeyboardActionListener.SWIPE_SWITCH_LANGUAGE;
            case "toggle_numpad" -> KeyboardActionListener.SWIPE_TOGGLE_NUMPAD;
            default -> KeyboardActionListener.SWIPE_NO_ACTION;
        };
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
        return prefs.getBoolean(PREF_SHOW_SETUP_WIZARD_ICON, Defaults.PREF_SHOW_SETUP_WIZARD_ICON);
    }

    public static boolean readOneHandedModeEnabled(final SharedPreferences prefs, final boolean isLandscape) {
        return prefs.getBoolean(PREF_ONE_HANDED_MODE_PREFIX + !isLandscape, Defaults.PREF_ONE_HANDED_MODE);
    }

    public void writeOneHandedModeEnabled(final boolean enabled) {
        mPrefs.edit().putBoolean(PREF_ONE_HANDED_MODE_PREFIX +
                (getCurrent().mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT), enabled).apply();
    }

    public static float readOneHandedModeScale(final SharedPreferences prefs, final boolean isLandscape) {
        return prefs.getFloat(PREF_ONE_HANDED_SCALE_PREFIX + !isLandscape, Defaults.PREF_ONE_HANDED_SCALE);
    }

    public void writeOneHandedModeScale(final Float scale) {
        mPrefs.edit().putFloat(PREF_ONE_HANDED_SCALE_PREFIX +
                (getCurrent().mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT), scale).apply();
    }

    public static int readOneHandedModeGravity(final SharedPreferences prefs, final boolean isLandscape) {
        return prefs.getInt(PREF_ONE_HANDED_GRAVITY_PREFIX + !isLandscape, Defaults.PREF_ONE_HANDED_GRAVITY);
    }

    public void writeOneHandedModeGravity(final int gravity) {
        mPrefs.edit().putInt(PREF_ONE_HANDED_GRAVITY_PREFIX +
                (getCurrent().mDisplayOrientation == Configuration.ORIENTATION_PORTRAIT), gravity).apply();
    }

    public void writeSplitKeyboardEnabled(final boolean enabled, final boolean isLandscape) {
        final String pref = isLandscape ? PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE : PREF_ENABLE_SPLIT_KEYBOARD;
        mPrefs.edit().putBoolean(pref, enabled).apply();
    }

    public static boolean readSplitKeyboardEnabled(final SharedPreferences prefs, final boolean isLandscape) {
        final String pref = isLandscape ? PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE : PREF_ENABLE_SPLIT_KEYBOARD;
        return prefs.getBoolean(pref, isLandscape ? Defaults.PREF_ENABLE_SPLIT_KEYBOARD_LANDSCAPE : Defaults.PREF_ENABLE_SPLIT_KEYBOARD);
    }

    public static float readSplitSpacerScale(final SharedPreferences prefs, final boolean isLandscape) {
        final String pref = isLandscape ? PREF_SPLIT_SPACER_SCALE_LANDSCAPE : PREF_SPLIT_SPACER_SCALE;
        return prefs.getFloat(pref, isLandscape ? Defaults.PREF_SPLIT_SPACER_SCALE_LANDSCAPE : Defaults.PREF_SPLIT_SPACER_SCALE);
    }

    public static float readBottomPaddingScale(final SharedPreferences prefs, final boolean landscape) {
        if (landscape)
            return prefs.getFloat(PREF_BOTTOM_PADDING_SCALE_LANDSCAPE, Defaults.PREF_BOTTOM_PADDING_SCALE_LANDSCAPE);
        return prefs.getFloat(PREF_BOTTOM_PADDING_SCALE, Defaults.PREF_BOTTOM_PADDING_SCALE);
    }

    public static float readSidePaddingScale(final SharedPreferences prefs, final boolean landscape) {
        if (landscape)
            return prefs.getFloat(PREF_SIDE_PADDING_SCALE_LANDSCAPE, Defaults.PREF_SIDE_PADDING_SCALE_LANDSCAPE);
        return prefs.getFloat(PREF_SIDE_PADDING_SCALE, Defaults.PREF_SIDE_PADDING_SCALE);
    }

    public static boolean readHasHardwareKeyboard(final Configuration conf) {
        // The standard way of finding out whether we have a hardware keyboard. This code is taken
        // from InputMethodService#onEvaluateInputShown, which canonically determines this.
        // In a nutshell, we have a keyboard if the configuration says the type of hardware keyboard
        // is NOKEYS and if it's not hidden (e.g. folded inside the device).
        return conf.keyboard != Configuration.KEYBOARD_NOKEYS
                && conf.hardKeyboardHidden != Configuration.HARDKEYBOARDHIDDEN_YES;
    }

    public static String readPinnedClipString(final Context context) {
        try {
            final SharedPreferences prefs = KtxKt.protectedPrefs(context);
            return prefs.getString(PREF_PINNED_CLIPS, Defaults.PREF_PINNED_CLIPS);
        } catch (final IllegalStateException e) {
            // SharedPreferences in credential encrypted storage are not available until after user is unlocked
            return "";
        }
    }

    public static void writePinnedClipString(final Context context, final String clips) {
        try {
            final SharedPreferences prefs = KtxKt.protectedPrefs(context);
            prefs.edit().putString(PREF_PINNED_CLIPS, clips).apply();
        } catch (final IllegalStateException e) {
            // SharedPreferences in credential encrypted storage are not available until after user is unlocked
        }
    }

    public static int readMorePopupKeysPref(final SharedPreferences prefs) {
        return switch (prefs.getString(Settings.PREF_MORE_POPUP_KEYS, Defaults.PREF_MORE_POPUP_KEYS)) {
            case "all" -> LocaleKeyboardInfosKt.POPUP_KEYS_ALL;
            case "more" -> LocaleKeyboardInfosKt.POPUP_KEYS_MORE;
            case "normal" -> LocaleKeyboardInfosKt.POPUP_KEYS_NORMAL;
            default -> LocaleKeyboardInfosKt.POPUP_KEYS_MAIN;
        };
    }

    @Nullable public static Drawable readUserBackgroundImage(final Context context, final boolean night) {
        final boolean landscape = context.getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        final int index = (night ? 1 : 0) + (landscape ? 2 : 0);
        if (sCachedBackgroundImages[index] != null) return sCachedBackgroundImages[index];

        File image = getCustomBackgroundFile(context, night, landscape);
        if (!image.isFile() && landscape)
            image = getCustomBackgroundFile(context, night, false); // fall back to portrait image for historic reasons
        if (!image.isFile()) return null;
        try {
            sCachedBackgroundImages[index] = new BitmapDrawable(context.getResources(), BitmapFactory.decodeFile(image.getAbsolutePath()));
            return sCachedBackgroundImages[index];
        } catch (Exception e) {
            return null;
        }
    }

    public static File getCustomBackgroundFile(final Context context, final boolean night, final boolean landscape) {
        return new File(DeviceProtectedUtils.getFilesDir(context), "custom_background_image" + (landscape ? "_landscape" : "") + (night ? "_night" : ""));
    }

    public static void clearCachedBackgroundImages() {
        Arrays.fill(sCachedBackgroundImages, null);
    }

    public static List<Locale> getSecondaryLocales(final SharedPreferences prefs, final Locale mainLocale) {
        final String localesString = prefs.getString(PREF_SECONDARY_LOCALES_PREFIX + mainLocale.toLanguageTag(), Defaults.PREF_SECONDARY_LOCALES);

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
        if (SettingsActivity.Companion.getForceOppositeTheme()) isNight = !isNight;
        else isNight = isNight && prefs.getBoolean(PREF_THEME_DAY_NIGHT, Defaults.PREF_THEME_DAY_NIGHT);
        final String themeName = (isNight)
                ? prefs.getString(Settings.PREF_THEME_COLORS_NIGHT, Defaults.PREF_THEME_COLORS_NIGHT)
                : prefs.getString(Settings.PREF_THEME_COLORS, Defaults.PREF_THEME_COLORS);
        final String themeStyle = prefs.getString(Settings.PREF_THEME_STYLE, Defaults.PREF_THEME_STYLE);

        return KeyboardTheme.getThemeColors(themeName, themeStyle, context, prefs, isNight);
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

    public String readCustomCurrencyKey() {
        return mPrefs.getString(PREF_CUSTOM_CURRENCY_KEY, Defaults.PREF_CUSTOM_CURRENCY_KEY);
    }

    public Integer getCustomToolbarKeyCode(ToolbarKey key) {
        return ToolbarUtilsKt.getCustomKeyCode(key, mPrefs);
    }

    public Integer getCustomToolbarLongpressCode(ToolbarKey key) {
        return ToolbarUtilsKt.getCustomLongpressKeyCode(key, mPrefs);
    }

    public static File getCustomFontFile(final Context context) {
        return new File(DeviceProtectedUtils.getFilesDir(context), "custom_font");
    }

    public static String getLayoutName(final LayoutType type, final SharedPreferences prefs) {
        return prefs.getString(PREF_LAYOUT_PREFIX + type.name(), Defaults.INSTANCE.getDefault(type));
    }

    @Nullable
    public Typeface getCustomTypeface() {
        if (!sCustomTypefaceLoaded) {
            try {
                sCachedTypeface = Typeface.createFromFile(getCustomFontFile(mContext));
            } catch (Exception e) { }
        }
        sCustomTypefaceLoaded = true;
        return sCachedTypeface;
    }

    public static void clearCachedTypeface() {
        sCachedTypeface = null;
        sCustomTypefaceLoaded = false;
    }
}
