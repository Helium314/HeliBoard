/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.utils;

import android.content.Context;
import android.content.res.Resources;
import android.view.inputmethod.InputMethodSubtype;

import helium314.keyboard.compat.ConfigurationCompatKt;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.common.LocaleUtils;
import helium314.keyboard.latin.common.StringUtils;

import java.util.HashMap;
import java.util.Locale;

import static helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.COMBINING_RULES;
import static helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET;
import static helium314.keyboard.latin.common.Constants.Subtype.ExtraValue.UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A helper class to deal with subtype locales.
  */
// TODO: consolidate this into RichInputMethodSubtype
// todo (later): see whether this complicated mess can be simplified
public final class SubtypeLocaleUtils {
    static final String TAG = SubtypeLocaleUtils.class.getSimpleName();

    // This reference class {@link R} must be located in the same package as LatinIME.java.
    // switched to context.getPackageName(), which works with changed debug package name
    // any reason to prefer original version?
//    private static final String RESOURCE_PACKAGE_NAME = R.class.getPackage().getName();

    // Special language code to represent "no language".
    public static final String NO_LANGUAGE = "zz";
    public static final String QWERTY = "qwerty";
    public static final String EMOJI = "emoji";
    public static final int UNKNOWN_KEYBOARD_LAYOUT = R.string.subtype_generic;

    private static volatile boolean sInitialized = false;
    private static final Object sInitializeLock = new Object();
    private static Resources sResources;
    // Keyboard layout to its display name map.
    private static final HashMap<String, String> sKeyboardLayoutToDisplayNameMap = new HashMap<>();
    // Keyboard layout to subtype name resource id map.
    private static final HashMap<String, Integer> sKeyboardLayoutToNameIdsMap = new HashMap<>();
    // Exceptional locale whose name should be displayed in Locale.ROOT.
    private static final HashMap<String, Integer> sExceptionalLocaleDisplayedInRootLocale = new HashMap<>();
    // Exceptional locale to subtype name resource id map.
    private static final HashMap<String, Integer> sExceptionalLocaleToNameIdsMap = new HashMap<>();
    // Exceptional locale to subtype name with layout resource id map.
    private static final HashMap<String, Integer> sExceptionalLocaleToWithLayoutNameIdsMap = new HashMap<>();
    private static final String SUBTYPE_NAME_RESOURCE_PREFIX = "string/subtype_";
    private static final String SUBTYPE_NAME_RESOURCE_GENERIC_PREFIX = "string/subtype_generic_";
    private static final String SUBTYPE_NAME_RESOURCE_WITH_LAYOUT_PREFIX = "string/subtype_with_layout_";
    private static final String SUBTYPE_NAME_RESOURCE_NO_LANGUAGE_PREFIX = "string/subtype_no_language_";
    private static final String SUBTYPE_NAME_RESOURCE_IN_ROOT_LOCALE_PREFIX = "string/subtype_in_root_locale_";

    private SubtypeLocaleUtils() {
        // Intentional empty constructor for utility class.
    }

    // Note that this initialization method can be called multiple times.
    public static void init(final Context context) {
        synchronized (sInitializeLock) {
            if (!sInitialized) {
                initLocked(context);
                sInitialized = true;
            }
        }
    }

    private static void initLocked(final Context context) {
        final String RESOURCE_PACKAGE_NAME = context.getPackageName();
        final Resources res = context.getResources();
        sResources = res;

        final String[] predefinedLayoutSet = res.getStringArray(R.array.predefined_layouts);
        final String[] layoutDisplayNames = res.getStringArray(R.array.predefined_layout_display_names);
        for (int i = 0; i < predefinedLayoutSet.length; i++) {
            final String layoutName = predefinedLayoutSet[i];
            sKeyboardLayoutToDisplayNameMap.put(layoutName, layoutDisplayNames[i]);
            final String resourceName = SUBTYPE_NAME_RESOURCE_GENERIC_PREFIX + layoutName;
            final int resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME);
            sKeyboardLayoutToNameIdsMap.put(layoutName, resId);
            // Register subtype name resource id of "No language" with key "zz_<layout>"
            final String noLanguageResName = SUBTYPE_NAME_RESOURCE_NO_LANGUAGE_PREFIX + layoutName;
            final int noLanguageResId = res.getIdentifier(noLanguageResName, null, RESOURCE_PACKAGE_NAME);
            final String key = getNoLanguageLayoutKey(layoutName);
            sKeyboardLayoutToNameIdsMap.put(key, noLanguageResId);
        }

        final String[] exceptionalLocaleInRootLocale = res.getStringArray(R.array.subtype_locale_displayed_in_root_locale);
        for (final String languageTag : exceptionalLocaleInRootLocale) {
            final String resourceName = SUBTYPE_NAME_RESOURCE_IN_ROOT_LOCALE_PREFIX + languageTag.replace('-', '_');
            final int resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME);
            sExceptionalLocaleDisplayedInRootLocale.put(languageTag, resId);
        }

        final String[] exceptionalLocales = res.getStringArray(R.array.subtype_locale_exception_keys);
        for (final String languageTag : exceptionalLocales) {
            final String resourceName = SUBTYPE_NAME_RESOURCE_PREFIX + languageTag.replace('-', '_');
            final int resId = res.getIdentifier(resourceName, null, RESOURCE_PACKAGE_NAME);
            sExceptionalLocaleToNameIdsMap.put(languageTag, resId);
            final String resourceNameWithLayout = SUBTYPE_NAME_RESOURCE_WITH_LAYOUT_PREFIX + languageTag.replace('-', '_');
            final int resIdWithLayout = res.getIdentifier(resourceNameWithLayout, null, RESOURCE_PACKAGE_NAME);
            sExceptionalLocaleToWithLayoutNameIdsMap.put(languageTag, resIdWithLayout);
        }
    }

    public static boolean isExceptionalLocale(final Locale locale) {
        return sExceptionalLocaleToNameIdsMap.containsKey(locale.toLanguageTag());
    }

    private static String getNoLanguageLayoutKey(final String keyboardLayoutName) {
        return NO_LANGUAGE + "_" + keyboardLayoutName;
    }

    public static int getSubtypeNameId(final Locale locale, final String keyboardLayoutName) {
        final String languageTag = locale.toLanguageTag();
        if (isExceptionalLocale(locale)) {
            return sExceptionalLocaleToWithLayoutNameIdsMap.get(languageTag);
        }
        final String key = NO_LANGUAGE.equals(languageTag)
                ? getNoLanguageLayoutKey(keyboardLayoutName)
                : keyboardLayoutName;
        final Integer nameId = sKeyboardLayoutToNameIdsMap.get(key);
        return nameId == null ? UNKNOWN_KEYBOARD_LAYOUT : nameId;
    }

    @NonNull
    public static Locale getDisplayLocaleOfSubtypeLocale(@NonNull final Locale locale) {
        final String languageTag = locale.toLanguageTag();
        if (NO_LANGUAGE.equals(languageTag)) {
            return ConfigurationCompatKt.locale(sResources.getConfiguration());
        }
        if (sExceptionalLocaleDisplayedInRootLocale.containsKey(languageTag)) {
            return Locale.ROOT;
        }
        return locale;
    }

    public static String getSubtypeLocaleDisplayNameInSystemLocale(@NonNull final Locale locale) {
        final Locale displayLocale = ConfigurationCompatKt.locale(sResources.getConfiguration());
        return getSubtypeLocaleDisplayNameInternal(locale, displayLocale);
    }

    @NonNull
    public static String getSubtypeLocaleDisplayName(@NonNull final Locale locale) {
        final Locale displayLocale = getDisplayLocaleOfSubtypeLocale(locale);
        return getSubtypeLocaleDisplayNameInternal(locale, displayLocale);
    }

    @NonNull
    public static String getSubtypeLanguageDisplayName(@NonNull final Locale locale) {
        final Locale displayLocale = getDisplayLocaleOfSubtypeLocale(locale);
        final Locale languageLocale;
        if (sExceptionalLocaleDisplayedInRootLocale.containsKey(locale.toLanguageTag())) {
            languageLocale = locale;
        } else {
            languageLocale = LocaleUtils.constructLocale(locale.getLanguage());
        }
        return getSubtypeLocaleDisplayNameInternal(languageLocale, displayLocale);
    }

    @NonNull
    private static String getSubtypeLocaleDisplayNameInternal(@NonNull final Locale locale,
            @NonNull final Locale displayLocale) {
        final String languageTag = locale.toLanguageTag();
        if (NO_LANGUAGE.equals(locale.toLanguageTag())) {
            // No language subtype should be displayed in system locale.
            return sResources.getString(R.string.subtype_no_language);
        }
        final Integer exceptionalNameResId;
        if (displayLocale.equals(Locale.ROOT)
                && sExceptionalLocaleDisplayedInRootLocale.containsKey(languageTag)) {
            exceptionalNameResId = sExceptionalLocaleDisplayedInRootLocale.get(languageTag);
        } else if (sExceptionalLocaleToNameIdsMap.containsKey(languageTag)) {
            exceptionalNameResId = sExceptionalLocaleToNameIdsMap.get(languageTag);
        } else {
            exceptionalNameResId = null;
        }

        final String displayName;
        if (exceptionalNameResId != null) {
            displayName = RunInLocaleKt.runInLocale(sResources, displayLocale, res -> res.getString(exceptionalNameResId));
        } else {
            displayName = LocaleUtils.getLocaleDisplayNameInLocale(locale, sResources, displayLocale);
        }
        return StringUtils.capitalizeFirstCodePoint(displayName, displayLocale);
    }

    // InputMethodSubtype's display name in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  display name
    // ------ ------- - ----------------------
    //  en_US qwerty  F  English (US)            exception
    //  en_GB qwerty  F  English (UK)            exception
    //  es_US spanish F  Español (EE.UU.)        exception
    //  fr    azerty  F  Français
    //  fr_CA qwerty  F  Français (Canada)
    //  fr_CH swiss   F  Français (Suisse)
    //  de    qwertz  F  Deutsch
    //  de_CH swiss   T  Deutsch (Schweiz)
    //  zz    qwerty  F  Alphabet (QWERTY)       in system locale
    //  fr    qwertz  T  Français (QWERTZ)
    //  de    qwerty  T  Deutsch (QWERTY)
    //  en_US azerty  T  English (US) (AZERTY)   exception
    //  zz    azerty  T  Alphabet (AZERTY)       in system locale

    @NonNull
    private static String getReplacementString(@NonNull final InputMethodSubtype subtype,
            @NonNull final Locale displayLocale) {
        if (subtype.containsExtraValueKey(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME)) {
            return subtype.getExtraValueOf(UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME);
        }
        return getSubtypeLocaleDisplayNameInternal(SubtypeUtilsKt.locale(subtype), displayLocale);
    }

    @NonNull
    public static String getSubtypeDisplayNameInSystemLocale(
            @NonNull final InputMethodSubtype subtype) {
        final Locale displayLocale = ConfigurationCompatKt.locale(sResources.getConfiguration());
        return getSubtypeDisplayNameInternal(subtype, displayLocale);
    }

    @NonNull
    public static String getSubtypeNameForLogging(@Nullable final InputMethodSubtype subtype) {
        if (subtype == null) {
            return "<null subtype>";
        }
        return SubtypeUtilsKt.locale(subtype) + "/" + getKeyboardLayoutSetName(subtype);
    }

    @NonNull
    private static String getSubtypeDisplayNameInternal(@NonNull final InputMethodSubtype subtype,
            @NonNull final Locale displayLocale) {
        final String replacementString = getReplacementString(subtype, displayLocale);
        final int nameResId = subtype.getNameResId();
        return RunInLocaleKt.runInLocale(sResources, displayLocale,
            res -> {
                try {
                    return StringUtils.capitalizeFirstCodePoint(res.getString(nameResId, replacementString), displayLocale);
                } catch (Resources.NotFoundException e) {
                    Log.w(TAG, "Unknown subtype: mode=" + subtype.getMode()
                            + " nameResId=" + subtype.getNameResId()
                            + " locale=" + subtype.getLocale()
                            + " extra=" + subtype.getExtraValue()
                            + "\n" + DebugLogUtils.getStackTrace());
                    return "";
                }
            });
    }

    @Nullable
    public static String getKeyboardLayoutSetDisplayName(@NonNull final InputMethodSubtype subtype) {
        final String layoutName = getKeyboardLayoutSetName(subtype);
        return getKeyboardLayoutSetDisplayName(layoutName);
    }

    @Nullable
    public static String getKeyboardLayoutSetDisplayName(@NonNull final String layoutName) {
        if (layoutName.startsWith(CustomLayoutUtilsKt.CUSTOM_LAYOUT_PREFIX))
            return CustomLayoutUtilsKt.getLayoutDisplayName(layoutName);
        return sKeyboardLayoutToDisplayNameMap.get(layoutName);
    }

    @NonNull
    public static String getKeyboardLayoutSetName(final InputMethodSubtype subtype) {
        String keyboardLayoutSet = subtype.getExtraValueOf(KEYBOARD_LAYOUT_SET);
        if (keyboardLayoutSet == null && subtype.isAsciiCapable()) {
            keyboardLayoutSet = QWERTY;
        }
        if (keyboardLayoutSet == null) { // we could search for a subtype with the correct script, but this is a bug anyway...
            Log.w(TAG, "KeyboardLayoutSet not found, use QWERTY: " +
                    "locale=" + subtype.getLocale() + " extraValue=" + subtype.getExtraValue());
            return QWERTY;
        }
        return keyboardLayoutSet;
    }

    public static String getCombiningRulesExtraValue(final InputMethodSubtype subtype) {
        return subtype.getExtraValueOf(COMBINING_RULES);
    }
}
