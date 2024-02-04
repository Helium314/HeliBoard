/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin;

import android.view.inputmethod.InputMethodSubtype;

import helium314.keyboard.latin.common.Constants;
import helium314.keyboard.latin.common.LocaleUtils;
import helium314.keyboard.latin.utils.CustomLayoutUtilsKt;
import helium314.keyboard.latin.utils.Log;
import helium314.keyboard.latin.utils.SubtypeLocaleUtils;
import helium314.keyboard.latin.utils.SubtypeUtilsKt;

import java.util.Locale;

import static helium314.keyboard.latin.common.Constants.Subtype.KEYBOARD_MODE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Enrichment class for InputMethodSubtype to enable concurrent multi-lingual input.
 * <p>
 * Right now, this returns the extra value of its primary subtype.
 */
// non final for easy mocking.
public class RichInputMethodSubtype {
    private static final String TAG = RichInputMethodSubtype.class.getSimpleName();

    @NonNull
    private final InputMethodSubtype mSubtype;
    @NonNull
    private final Locale mLocale;
    // The subtype is considered RTL if the language of the main subtype is RTL.
    // Cached because it might get read frequently, e.g. when moving pointer with space bar
    private final boolean mIsRtl;

    public RichInputMethodSubtype(@NonNull final InputMethodSubtype subtype) {
        mSubtype = subtype;
        mLocale = SubtypeUtilsKt.locale(mSubtype);
        mIsRtl = LocaleUtils.isRtlLanguage(mLocale);
    }

    // Extra values are determined by the primary subtype. This is probably right, but
    // we may have to revisit this later.
    public String getExtraValueOf(@NonNull final String key) {
        return mSubtype.getExtraValueOf(key);
    }

    public boolean hasExtraValue(@NonNull final String key) {
        return mSubtype.containsExtraValueKey(key);
    }

    // The mode is also determined by the primary subtype.
    public String getMode() {
        return mSubtype.getMode();
    }

    public boolean isNoLanguage() {
        return SubtypeLocaleUtils.NO_LANGUAGE.equals(mLocale.getLanguage());
    }

    public boolean isCustom() {
        return getKeyboardLayoutSetName().startsWith(CustomLayoutUtilsKt.CUSTOM_LAYOUT_PREFIX);
    }

    public String getNameForLogging() {
        return toString();
    }

    // InputMethodSubtype's display name for spacebar text in its locale.
    //        isAdditionalSubtype (T=true, F=false)
    // locale layout  |  Middle      Full
    // ------ ------- - --------- ----------------------
    //  en_US qwerty  F  English   English (US)           exception
    //  en_GB qwerty  F  English   English (UK)           exception
    //  es_US spanish F  Español   Español (EE.UU.)       exception
    //  fr    azerty  F  Français  Français
    //  fr_CA qwerty  F  Français  Français (Canada)
    //  fr_CH swiss   F  Français  Français (Suisse)
    //  de    qwertz  F  Deutsch   Deutsch
    //  de_CH swiss   T  Deutsch   Deutsch (Schweiz)
    //  zz    qwerty  F  QWERTY    QWERTY
    //  fr    qwertz  T  Français  Français
    //  de    qwerty  T  Deutsch   Deutsch
    //  en_US azerty  T  English   English (US)
    //  zz    azerty  T  AZERTY    AZERTY
    // Get the RichInputMethodSubtype's full display name in its locale.
    @NonNull
    public String getFullDisplayName() {
        if (isNoLanguage()) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(mSubtype);
        }
        return SubtypeLocaleUtils.getSubtypeLocaleDisplayName(mLocale);
    }

    // Get the RichInputMethodSubtype's middle display name in its locale.
    @NonNull
    public String getMiddleDisplayName() {
        if (isNoLanguage()) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(mSubtype);
        }
        return SubtypeLocaleUtils.getSubtypeLanguageDisplayName(mLocale);
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof RichInputMethodSubtype)) {
            return false;
        }
        final RichInputMethodSubtype other = (RichInputMethodSubtype)o;
        return mSubtype.equals(other.mSubtype) && mLocale.equals(other.mLocale);
    }

    @Override
    public int hashCode() {
        return mSubtype.hashCode() + mLocale.hashCode();
    }

    @Override
    public String toString() {
        return "Multi-lingual subtype: " + mSubtype + ", " + mLocale;
    }

    @NonNull
    public Locale getLocale() {
        return mLocale;
    }

    public boolean isRtlSubtype() {
        return mIsRtl;
    }

    // TODO: remove this method
    @NonNull
    public InputMethodSubtype getRawSubtype() { return mSubtype; }

    @NonNull
    public String getKeyboardLayoutSetName() {
        return SubtypeLocaleUtils.getKeyboardLayoutSetName(mSubtype);
    }

    public static RichInputMethodSubtype getRichInputMethodSubtype(
            @Nullable final InputMethodSubtype subtype) {
        if (subtype == null) {
            return getNoLanguageSubtype();
        } else {
            return new RichInputMethodSubtype(subtype);
        }
    }

    // Dummy no language QWERTY subtype. See {@link R.xml.method}.
    private static final int SUBTYPE_ID_OF_DUMMY_NO_LANGUAGE_SUBTYPE = 0xdde0bfd3;
    private static final String EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE =
            "KeyboardLayoutSet=" + SubtypeLocaleUtils.QWERTY
            + "," + Constants.Subtype.ExtraValue.ASCII_CAPABLE
            + "," + Constants.Subtype.ExtraValue.ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE
            + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE;
    @NonNull
    private static final RichInputMethodSubtype DUMMY_NO_LANGUAGE_SUBTYPE =
            new RichInputMethodSubtype(new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeNameResId(R.string.subtype_no_language_qwerty)
                    .setSubtypeIconResId(R.drawable.ic_ime_switcher)
                    .setSubtypeLocale(SubtypeLocaleUtils.NO_LANGUAGE)
                    .setSubtypeMode(KEYBOARD_MODE)
                    .setSubtypeExtraValue(EXTRA_VALUE_OF_DUMMY_NO_LANGUAGE_SUBTYPE)
                    .setIsAuxiliary(false)
                    .setOverridesImplicitlyEnabledSubtype(false)
                    .setSubtypeId(SUBTYPE_ID_OF_DUMMY_NO_LANGUAGE_SUBTYPE)
                    .setIsAsciiCapable(true)
                    .build());

    // Caveat: We probably should remove this when we add an Emoji subtype in {@link R.xml.method}.
    // Dummy Emoji subtype. See {@link R.xml.method}.
    private static final int SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE = 0xd78b2ed0;
    private static final String EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE =
            "KeyboardLayoutSet=" + SubtypeLocaleUtils.EMOJI
            + "," + Constants.Subtype.ExtraValue.EMOJI_CAPABLE;
    @NonNull
    private static final RichInputMethodSubtype DUMMY_EMOJI_SUBTYPE =
            new RichInputMethodSubtype(new InputMethodSubtype.InputMethodSubtypeBuilder()
                    .setSubtypeNameResId(R.string.subtype_emoji)
                    .setSubtypeIconResId(R.drawable.ic_ime_switcher)
                    .setSubtypeLocale(SubtypeLocaleUtils.NO_LANGUAGE)
                    .setSubtypeMode(KEYBOARD_MODE)
                    .setSubtypeExtraValue(EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE)
                    .setIsAuxiliary(false)
                    .setOverridesImplicitlyEnabledSubtype(false)
                    .setSubtypeId(SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE)
                    .build());
    private static RichInputMethodSubtype sNoLanguageSubtype;

    @NonNull
    public static RichInputMethodSubtype getNoLanguageSubtype() {
        RichInputMethodSubtype noLanguageSubtype = sNoLanguageSubtype;
        if (noLanguageSubtype == null) {
            final InputMethodSubtype rawNoLanguageSubtype = RichInputMethodManager.getInstance()
                    .findSubtypeByLocaleAndKeyboardLayoutSet(LocaleUtils.constructLocale(SubtypeLocaleUtils.NO_LANGUAGE), SubtypeLocaleUtils.QWERTY);
            if (rawNoLanguageSubtype != null) {
                noLanguageSubtype = new RichInputMethodSubtype(rawNoLanguageSubtype);
            }
        }
        if (noLanguageSubtype != null) {
            sNoLanguageSubtype = noLanguageSubtype;
            return noLanguageSubtype;
        }
        Log.w(TAG, "Can't find any language with QWERTY subtype");
        Log.w(TAG, "No input method subtype found; returning dummy subtype: " + DUMMY_NO_LANGUAGE_SUBTYPE);
        return DUMMY_NO_LANGUAGE_SUBTYPE;
    }

    @NonNull
    public static RichInputMethodSubtype getEmojiSubtype() {
        return DUMMY_EMOJI_SUBTYPE;
    }
}
