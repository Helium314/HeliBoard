/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.keyboard;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.text.InputType;

import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyTexts;
import helium314.keyboard.latin.utils.Log;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;

import helium314.keyboard.keyboard.internal.KeyboardBuilder;
import helium314.keyboard.keyboard.internal.KeyboardParams;
import helium314.keyboard.keyboard.internal.UniqueKeysCache;
import helium314.keyboard.keyboard.internal.keyboard_parser.LocaleKeyTextsKt;
import helium314.keyboard.latin.R;
import helium314.keyboard.latin.RichInputMethodSubtype;
import helium314.keyboard.latin.utils.InputTypeUtils;
import helium314.keyboard.latin.utils.ScriptUtils;
import helium314.keyboard.latin.utils.XmlParseUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * This class represents a set of keyboard layouts. Each of them represents a different keyboard
 * specific to a keyboard state, such as alphabet, symbols, and so on.  Layouts in the same
 * {@link KeyboardLayoutSet} are related to each other.
 * A {@link KeyboardLayoutSet} needs to be created for each
 * {@link android.view.inputmethod.EditorInfo}.
 */
public final class KeyboardLayoutSet {
    private static final String TAG = KeyboardLayoutSet.class.getSimpleName();
    private static final boolean DEBUG_CACHE = false;

    private static final String TAG_KEYBOARD_SET = "KeyboardLayoutSet";
    private static final String TAG_ELEMENT = "Element";

    private static final String KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX = "keyboard_layout_set_";

    private final Context mContext;
    @NonNull
    private final Params mParams;
    public final LocaleKeyTexts mLocaleKeyTexts;

    // How many layouts we forcibly keep in cache. This only includes ALPHABET (default) and
    // ALPHABET_AUTOMATIC_SHIFTED layouts - other layouts may stay in memory in the map of
    // soft-references, but we forcibly cache this many alphabetic/auto-shifted layouts.
    private static final int FORCIBLE_CACHE_SIZE = 4;
    // By construction of soft references, anything that is also referenced somewhere else
    // will stay in the cache. So we forcibly keep some references in an array to prevent
    // them from disappearing from sKeyboardCache.
    private static final Keyboard[] sForcibleKeyboardCache = new Keyboard[FORCIBLE_CACHE_SIZE];
    private static final HashMap<KeyboardId, SoftReference<Keyboard>> sKeyboardCache =
            new HashMap<>();
    @NonNull
    private static final UniqueKeysCache sUniqueKeysCache = UniqueKeysCache.newInstance();

    public static final class KeyboardLayoutSetException extends RuntimeException {
        public final KeyboardId mKeyboardId;

        public KeyboardLayoutSetException(final Throwable cause, final KeyboardId keyboardId) {
            super(cause);
            mKeyboardId = keyboardId;
        }
    }

    public static final class Params {
        String mKeyboardLayoutSetName;
        int mMode;
        boolean mDisableTouchPositionCorrectionDataForTest;
        // TODO: Use {@link InputAttributes} instead of these variables.
        EditorInfo mEditorInfo;
        boolean mIsPasswordField;
        boolean mVoiceInputKeyEnabled;
        boolean mDeviceLocked;
        boolean mNumberRowEnabled;
        boolean mLanguageSwitchKeyEnabled;
        boolean mEmojiKeyEnabled;
        boolean mOneHandedModeEnabled;
        RichInputMethodSubtype mSubtype;
        boolean mIsSpellChecker;
        int mKeyboardWidth;
        int mKeyboardHeight;
        String mScript = ScriptUtils.SCRIPT_LATIN;
        // Indicates if the user has enabled the split-layout preference
        // and the required ProductionFlags are enabled.
        boolean mIsSplitLayoutEnabled;
    }

    public static void onSystemLocaleChanged() {
        clearKeyboardCache();
        LocaleKeyTextsKt.clearCache();
    }

    public static void onKeyboardThemeChanged() {
        clearKeyboardCache();
    }

    private static void clearKeyboardCache() {
        sKeyboardCache.clear();
        sUniqueKeysCache.clear();
    }

    KeyboardLayoutSet(final Context context, @NonNull final Params params) {
        mContext = context;
        mParams = params;
        mLocaleKeyTexts = LocaleKeyTextsKt.getOrCreate(context, params.mSubtype.getLocale());
    }

    @NonNull
    public Keyboard getKeyboard(final int baseKeyboardLayoutSetElementId) {
        final int keyboardLayoutSetElementId;
        switch (mParams.mMode) {
            case KeyboardId.MODE_PHONE:
                if (baseKeyboardLayoutSetElementId == KeyboardId.ELEMENT_SYMBOLS) {
                    keyboardLayoutSetElementId = KeyboardId.ELEMENT_PHONE_SYMBOLS;
                } else {
                    keyboardLayoutSetElementId = KeyboardId.ELEMENT_PHONE;
                }
                break;
            case KeyboardId.MODE_NUMPAD:
                    keyboardLayoutSetElementId = KeyboardId.ELEMENT_NUMPAD;
                break;
            case KeyboardId.MODE_NUMBER:
            case KeyboardId.MODE_DATE:
            case KeyboardId.MODE_TIME:
            case KeyboardId.MODE_DATETIME:
                keyboardLayoutSetElementId = KeyboardId.ELEMENT_NUMBER;
                break;
            default:
                keyboardLayoutSetElementId = baseKeyboardLayoutSetElementId;
                break;
        }

        // Note: The keyboard for each shift state, and mode are represented as an elementName
        // attribute in a keyboard_layout_set XML file.  Also each keyboard layout XML resource is
        // specified as an elementKeyboard attribute in the file.
        // The KeyboardId is an internal key for a Keyboard object.

        final KeyboardId id = new KeyboardId(keyboardLayoutSetElementId, mParams);
        try {
            return getKeyboard(id);
        } catch (final RuntimeException e) {
            Log.e(TAG, "Can't create keyboard: " + id, e);
            throw new KeyboardLayoutSetException(e, id);
        }
    }

    @NonNull
    private Keyboard getKeyboard(final KeyboardId id) {
        final SoftReference<Keyboard> ref = sKeyboardCache.get(id);
        final Keyboard cachedKeyboard = (ref == null) ? null : ref.get();
        if (cachedKeyboard != null) {
            if (DEBUG_CACHE) {
                Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size() + ": HIT  id=" + id);
            }
            return cachedKeyboard;
        }

        final KeyboardBuilder<KeyboardParams> builder =
                new KeyboardBuilder<>(mContext, new KeyboardParams(sUniqueKeysCache));
        sUniqueKeysCache.setEnabled(id.isAlphabetKeyboard());
        builder.load(id);
        if (mParams.mDisableTouchPositionCorrectionDataForTest) {
            builder.disableTouchPositionCorrectionDataForTest();
        }
        final Keyboard keyboard = builder.build();
        sKeyboardCache.put(id, new SoftReference<>(keyboard));
        if ((id.mElementId == KeyboardId.ELEMENT_ALPHABET
                || id.mElementId == KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED)
                && !mParams.mIsSpellChecker) {
            // We only forcibly cache the primary, "ALPHABET", layouts.
            for (int i = sForcibleKeyboardCache.length - 1; i >= 1; --i) {
                sForcibleKeyboardCache[i] = sForcibleKeyboardCache[i - 1];
            }
            sForcibleKeyboardCache[0] = keyboard;
            if (DEBUG_CACHE) {
                Log.d(TAG, "forcing caching of keyboard with id=" + id);
            }
        }
        if (DEBUG_CACHE) {
            Log.d(TAG, "keyboard cache size=" + sKeyboardCache.size() + ": "
                    + ((ref == null) ? "LOAD" : "GCed") + " id=" + id);
        }
        return keyboard;
    }

    public String getScript() {
        return mParams.mScript;
    }

    public static final class Builder {
        private final Context mContext;
        private final Resources mResources;

        private final Params mParams = new Params();

        private static final EditorInfo EMPTY_EDITOR_INFO = new EditorInfo();

        public Builder(final Context context, @Nullable final EditorInfo ei) {
            mContext = context;
            mResources = context.getResources();
            final Params params = mParams;

            final EditorInfo editorInfo = (ei != null) ? ei : EMPTY_EDITOR_INFO;
            params.mMode = getKeyboardMode(editorInfo);
            // TODO: Consolidate those with {@link InputAttributes}.
            params.mEditorInfo = editorInfo;
            params.mIsPasswordField = InputTypeUtils.isPasswordInputType(editorInfo.inputType);

            // When the device is still locked, features like showing the IME setting app need to
            // be locked down.
            final KeyguardManager km = (KeyguardManager) context.getSystemService(Context.KEYGUARD_SERVICE);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                params.mDeviceLocked = km.isDeviceLocked();
            } else {
                params.mDeviceLocked = km.isKeyguardLocked();
            }
        }

        public Builder setKeyboardGeometry(final int keyboardWidth, final int keyboardHeight) {
            mParams.mKeyboardWidth = keyboardWidth;
            mParams.mKeyboardHeight = keyboardHeight;
            return this;
        }

        public Builder setSubtype(@NonNull final RichInputMethodSubtype subtype) {
            final boolean asciiCapable = subtype.getRawSubtype().isAsciiCapable();
            final boolean forceAscii = (mParams.mEditorInfo.imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII) != 0;
            final RichInputMethodSubtype keyboardSubtype = (forceAscii && !asciiCapable)
                    ? RichInputMethodSubtype.getNoLanguageSubtype()
                    : subtype;
            mParams.mSubtype = keyboardSubtype;
            mParams.mKeyboardLayoutSetName = KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX
                    + keyboardSubtype.getKeyboardLayoutSetName();
            return this;
        }

        public Builder setIsSpellChecker(final boolean isSpellChecker) {
            mParams.mIsSpellChecker = isSpellChecker;
            return this;
        }

        public Builder setVoiceInputKeyEnabled(final boolean enabled) {
            mParams.mVoiceInputKeyEnabled = enabled;
            return this;
        }
        
        public Builder setNumberRowEnabled(final boolean enabled) {
            mParams.mNumberRowEnabled = enabled;
            return this;
        }

        public Builder setLanguageSwitchKeyEnabled(final boolean enabled) {
            mParams.mLanguageSwitchKeyEnabled = enabled;
            return this;
        }

        public Builder setEmojiKeyEnabled(final boolean enabled) {
            mParams.mEmojiKeyEnabled = enabled;
            return this;
        }

        public Builder disableTouchPositionCorrectionData() {
            mParams.mDisableTouchPositionCorrectionDataForTest = true;
            return this;
        }

        public Builder setSplitLayoutEnabled(final boolean enabled) {
            mParams.mIsSplitLayoutEnabled = enabled;
            return this;
        }

        public Builder setOneHandedModeEnabled(boolean enabled) {
            mParams.mOneHandedModeEnabled = enabled;
            return this;
        }

        public KeyboardLayoutSet build() {
            if (mParams.mSubtype == null)
                throw new RuntimeException("KeyboardLayoutSet subtype is not specified");
            mParams.mScript = ScriptUtils.script(mParams.mSubtype.getLocale());
            // todo: the whole parsing stuff below should be removed, but currently
            //  it simply breaks when it's not available
            //  for emojis, moreKeys and moreSuggestions there are relevant parameters included
            int xmlId = getXmlId(mResources, mParams.mKeyboardLayoutSetName);
            if (xmlId == 0)
                xmlId = R.xml.keyboard_layout_set_default;
            try {
                parseKeyboardLayoutSet(mResources, xmlId);
            } catch (final IOException | XmlPullParserException e) {
                throw new RuntimeException(e.getMessage() + " in " + mParams.mKeyboardLayoutSetName, e);
            }
            return new KeyboardLayoutSet(mContext, mParams);
        }

        private static int getXmlId(final Resources resources, final String keyboardLayoutSetName) {
            final String packageName = resources.getResourcePackageName(R.xml.keyboard_layout_set_default);
            return resources.getIdentifier(keyboardLayoutSetName, "xml", packageName);
        }

        private void parseKeyboardLayoutSet(final Resources res, final int resId)
                throws XmlPullParserException, IOException {
            try (XmlResourceParser parser = res.getXml(resId)) {
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    final int event = parser.next();
                    if (event == XmlPullParser.START_TAG) {
                        final String tag = parser.getName();
                        if (TAG_KEYBOARD_SET.equals(tag)) {
                            parseKeyboardLayoutSetContent(parser);
                        } else {
                            throw new XmlParseUtils.IllegalStartTag(parser, tag, TAG_KEYBOARD_SET);
                        }
                    }
                }
            }
        }

        private void parseKeyboardLayoutSetContent(final XmlPullParser parser)
                throws XmlPullParserException, IOException {
            while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                final int event = parser.next();
                if (event == XmlPullParser.START_TAG) {
                    final String tag = parser.getName();
                    if (TAG_ELEMENT.equals(tag)) {
                        parseKeyboardLayoutSetElement(parser);
                    } else {
                        throw new XmlParseUtils.IllegalStartTag(parser, tag, TAG_KEYBOARD_SET);
                    }
                } else if (event == XmlPullParser.END_TAG) {
                    final String tag = parser.getName();
                    if (TAG_KEYBOARD_SET.equals(tag)) {
                        break;
                    }
                    throw new XmlParseUtils.IllegalEndTag(parser, tag, TAG_KEYBOARD_SET);
                }
            }
        }

        private void parseKeyboardLayoutSetElement(final XmlPullParser parser)
                throws XmlPullParserException, IOException {
            final TypedArray a = mResources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.KeyboardLayoutSet_Element);
            try {
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardLayoutSet_Element_elementName, "elementName",
                        TAG_ELEMENT, parser);
                XmlParseUtils.checkEndTag(TAG_ELEMENT, parser);
            } finally {
                a.recycle();
            }
        }

        private static int getKeyboardMode(final EditorInfo editorInfo) {
            final int inputType = editorInfo.inputType;
            final int variation = inputType & InputType.TYPE_MASK_VARIATION;

            switch (inputType & InputType.TYPE_MASK_CLASS) {
                case InputType.TYPE_CLASS_NUMBER:
                    return KeyboardId.MODE_NUMBER;
                case InputType.TYPE_CLASS_DATETIME:
                    switch (variation) {
                        case InputType.TYPE_DATETIME_VARIATION_DATE:
                            return KeyboardId.MODE_DATE;
                        case InputType.TYPE_DATETIME_VARIATION_TIME:
                            return KeyboardId.MODE_TIME;
                        default: // InputType.TYPE_DATETIME_VARIATION_NORMAL
                            return KeyboardId.MODE_DATETIME;
                    }
                case InputType.TYPE_CLASS_PHONE:
                    return KeyboardId.MODE_PHONE;
                case InputType.TYPE_CLASS_TEXT:
                    if (InputTypeUtils.isEmailVariation(variation)) {
                        return KeyboardId.MODE_EMAIL;
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_URI) {
                        return KeyboardId.MODE_URL;
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_SHORT_MESSAGE) {
                        //return KeyboardId.MODE_IM;
                        return KeyboardId.MODE_TEXT;
                    } else if (variation == InputType.TYPE_TEXT_VARIATION_FILTER) {
                        return KeyboardId.MODE_TEXT;
                    } else {
                        return KeyboardId.MODE_TEXT;
                    }
                default:
                    return KeyboardId.MODE_TEXT;
            }
        }
    }

    // used for testing keyboard layout files without actually creating a keyboard
    public static KeyboardId getFakeKeyboardId(final int elementId) {
        final Params params = new Params();
        params.mEditorInfo = new EditorInfo();
        params.mSubtype = RichInputMethodSubtype.getEmojiSubtype();
        params.mSubtype.getKeyboardLayoutSetName();
        return new KeyboardId(elementId, params);
    }
}
