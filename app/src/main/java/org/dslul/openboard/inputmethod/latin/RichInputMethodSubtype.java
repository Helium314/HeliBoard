/*
 * Copyright (C) 2014 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin;

import android.os.Build;
import android.util.Log;
import android.view.inputmethod.InputMethodSubtype;

import org.dslul.openboard.inputmethod.compat.InputMethodSubtypeCompatUtils;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;

import java.util.HashMap;
import java.util.Locale;

import static org.dslul.openboard.inputmethod.latin.common.Constants.Subtype.KEYBOARD_MODE;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Enrichment class for InputMethodSubtype to enable concurrent multi-lingual input.
 *
 * Right now, this returns the extra value of its primary subtype.
 */
// non final for easy mocking.
public class RichInputMethodSubtype {
    private static final String TAG = RichInputMethodSubtype.class.getSimpleName();

    private static final HashMap<Locale, Locale> sLocaleMap = initializeLocaleMap();
    private static HashMap<Locale, Locale> initializeLocaleMap() {
        final HashMap<Locale, Locale> map = new HashMap<>();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            // Locale#forLanguageTag is available on API Level 21+.
            // TODO: Remove this workaround once when we become able to deal with "sr-Latn".
            map.put(Locale.forLanguageTag("sr-Latn"), new Locale("sr_ZZ"));
        }
        return map;
    }

    @NonNull
    private final InputMethodSubtype mSubtype;
    @NonNull
    private final Locale mLocale;
    @NonNull
    private final Locale mOriginalLocale;

    public RichInputMethodSubtype(@NonNull final InputMethodSubtype subtype) {
        mSubtype = subtype;
        mOriginalLocale = InputMethodSubtypeCompatUtils.getLocaleObject(mSubtype);
        final Locale mappedLocale = sLocaleMap.get(mOriginalLocale);
        mLocale = mappedLocale != null ? mappedLocale : mOriginalLocale;
    }

    // Extra values are determined by the primary subtype. This is probably right, but
    // we may have to revisit this later.
    public String getExtraValueOf(@NonNull final String key) {
        return mSubtype.getExtraValueOf(key);
    }

    // The mode is also determined by the primary subtype.
    public String getMode() {
        return mSubtype.getMode();
    }

    public boolean isNoLanguage() {
        return SubtypeLocaleUtils.NO_LANGUAGE.equals(mSubtype.getLocale());
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
        return SubtypeLocaleUtils.getSubtypeLocaleDisplayName(mSubtype.getLocale());
    }

    // Get the RichInputMethodSubtype's middle display name in its locale.
    @NonNull
    public String getMiddleDisplayName() {
        if (isNoLanguage()) {
            return SubtypeLocaleUtils.getKeyboardLayoutSetDisplayName(mSubtype);
        }
        return SubtypeLocaleUtils.getSubtypeLanguageDisplayName(mSubtype.getLocale());
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

    @NonNull
    public Locale getOriginalLocale() {
        return mOriginalLocale;
    }

    public boolean isRtlSubtype() {
        // The subtype is considered RTL if the language of the main subtype is RTL.
        return LocaleUtils.isRtlLanguage(mLocale);
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
                    .setSubtypeIconResId(R.drawable.ic_ime_switcher_dark)
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
                    .setSubtypeIconResId(R.drawable.ic_ime_switcher_dark)
                    .setSubtypeLocale(SubtypeLocaleUtils.NO_LANGUAGE)
                    .setSubtypeMode(KEYBOARD_MODE)
                    .setSubtypeExtraValue(EXTRA_VALUE_OF_DUMMY_EMOJI_SUBTYPE)
                    .setIsAuxiliary(false)
                    .setOverridesImplicitlyEnabledSubtype(false)
                    .setSubtypeId(SUBTYPE_ID_OF_DUMMY_EMOJI_SUBTYPE)
                    .build());
    private static RichInputMethodSubtype sNoLanguageSubtype;
    private static RichInputMethodSubtype sEmojiSubtype;

    @NonNull
    public static RichInputMethodSubtype getNoLanguageSubtype() {
        RichInputMethodSubtype noLanguageSubtype = sNoLanguageSubtype;
        if (noLanguageSubtype == null) {
            final InputMethodSubtype rawNoLanguageSubtype = RichInputMethodManager.getInstance()
                    .findSubtypeByLocaleAndKeyboardLayoutSet(SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.QWERTY);
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
        RichInputMethodSubtype emojiSubtype = sEmojiSubtype;
        if (emojiSubtype == null) {
            final InputMethodSubtype rawEmojiSubtype = RichInputMethodManager.getInstance()
                    .findSubtypeByLocaleAndKeyboardLayoutSet(SubtypeLocaleUtils.NO_LANGUAGE, SubtypeLocaleUtils.EMOJI);
            if (rawEmojiSubtype != null) {
                emojiSubtype = new RichInputMethodSubtype(rawEmojiSubtype);
            }
        }
        if (emojiSubtype != null) {
            sEmojiSubtype = emojiSubtype;
            return emojiSubtype;
        }
        Log.w(TAG, "Can't find emoji subtype");
        Log.w(TAG, "No input method subtype found; returning dummy subtype: " + DUMMY_EMOJI_SUBTYPE);
        return DUMMY_EMOJI_SUBTYPE;
    }
}
