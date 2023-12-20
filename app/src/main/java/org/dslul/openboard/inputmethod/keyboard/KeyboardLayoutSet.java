/*
 * Copyright (C) 2011 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard;

import android.app.KeyguardManager;
import android.content.Context;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.os.Build;
import android.text.InputType;

import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.LocaleKeyTexts;
import org.dslul.openboard.inputmethod.latin.utils.Log;
import android.util.SparseArray;
import android.util.Xml;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodSubtype;

import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardBuilder;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams;
import org.dslul.openboard.inputmethod.keyboard.internal.UniqueKeysCache;
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.LocaleKeyTextsKt;
import org.dslul.openboard.inputmethod.latin.InputAttributes;
import org.dslul.openboard.inputmethod.latin.R;
import org.dslul.openboard.inputmethod.latin.RichInputMethodSubtype;
import org.dslul.openboard.inputmethod.latin.utils.InputTypeUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.SubtypeLocaleUtils;
import org.dslul.openboard.inputmethod.latin.utils.XmlParseUtils;
import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.lang.ref.SoftReference;
import java.util.HashMap;

import static org.dslul.openboard.inputmethod.latin.common.Constants.ImeOption.FORCE_ASCII;

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
    private static final String TAG_FEATURE = "Feature";

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
    private final static HashMap<InputMethodSubtype, Integer> sScriptIdsForSubtypes =
            new HashMap<>();

    public static final class KeyboardLayoutSetException extends RuntimeException {
        public final KeyboardId mKeyboardId;

        public KeyboardLayoutSetException(final Throwable cause, final KeyboardId keyboardId) {
            super(cause);
            mKeyboardId = keyboardId;
        }
    }

    private static final class ElementParams {
        int mKeyboardXmlId;
        boolean mProximityCharsCorrectionEnabled;
        boolean mSupportsSplitLayout;
        boolean mAllowRedundantMoreKeys;

        public ElementParams() {
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
        int mScriptId = ScriptUtils.SCRIPT_LATIN;
        // Indicates if the user has enabled the split-layout preference
        // and the required ProductionFlags are enabled.
        boolean mIsSplitLayoutEnabledByUser;
        // Indicates if split layout is actually enabled, taking into account
        // whether the user has enabled it, and the keyboard layout supports it.
        boolean mIsSplitLayoutEnabled;
        // Sparse array of KeyboardLayoutSet element parameters indexed by element's id.
        final SparseArray<ElementParams> mKeyboardLayoutSetElementIdToParamsMap = new SparseArray<>();
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

    public static int getScriptId(final Resources resources,
                                  @NonNull final InputMethodSubtype subtype) {
        final Integer value = sScriptIdsForSubtypes.get(subtype);
        if (null == value) {
            final int scriptId = Builder.readScriptId(resources, subtype);
            sScriptIdsForSubtypes.put(subtype, scriptId);
            return scriptId;
        }
        return value;
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

        ElementParams elementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                keyboardLayoutSetElementId);
        if (elementParams == null) {
            elementParams = mParams.mKeyboardLayoutSetElementIdToParamsMap.get(
                    KeyboardId.ELEMENT_ALPHABET);
        }
        // Note: The keyboard for each shift state, and mode are represented as an elementName
        // attribute in a keyboard_layout_set XML file.  Also each keyboard layout XML resource is
        // specified as an elementKeyboard attribute in the file.
        // The KeyboardId is an internal key for a Keyboard object.

        mParams.mIsSplitLayoutEnabled = mParams.mIsSplitLayoutEnabledByUser
                && elementParams.mSupportsSplitLayout;

        final KeyboardId id = new KeyboardId(keyboardLayoutSetElementId, mParams);
        try {
            return getKeyboard(elementParams, id);
        } catch (final RuntimeException e) {
            Log.e(TAG, "Can't create keyboard: " + id, e);
            throw new KeyboardLayoutSetException(e, id);
        }
    }

    @NonNull
    private Keyboard getKeyboard(final ElementParams elementParams, final KeyboardId id) {
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
        builder.setAllowRedundantMoreKeys(elementParams.mAllowRedundantMoreKeys);
        final int keyboardXmlId = elementParams.mKeyboardXmlId;
        builder.loadFromXml(keyboardXmlId, id);
        if (mParams.mDisableTouchPositionCorrectionDataForTest) {
            builder.disableTouchPositionCorrectionDataForTest();
        }
        builder.setProximityCharsCorrectionEnabled(elementParams.mProximityCharsCorrectionEnabled);
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

    public int getScriptId() {
        return mParams.mScriptId;
    }

    public static final class Builder {
        private final Context mContext;
        private final String mPackageName;
        private final Resources mResources;

        private final Params mParams = new Params();

        private static final EditorInfo EMPTY_EDITOR_INFO = new EditorInfo();

        public Builder(final Context context, @Nullable final EditorInfo ei) {
            mContext = context;
            mPackageName = context.getPackageName();
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
            // TODO: Consolidate with {@link InputAttributes}.
            @SuppressWarnings("deprecation") final boolean deprecatedForceAscii = InputAttributes.inPrivateImeOptions(
                    mPackageName, FORCE_ASCII, mParams.mEditorInfo);
            final boolean forceAscii = (mParams.mEditorInfo.imeOptions & EditorInfo.IME_FLAG_FORCE_ASCII) != 0
                    || deprecatedForceAscii;
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

        public Builder setSplitLayoutEnabledByUser(final boolean enabled) {
            mParams.mIsSplitLayoutEnabledByUser = enabled;
            return this;
        }

        public Builder setOneHandedModeEnabled(boolean enabled) {
            mParams.mOneHandedModeEnabled = enabled;
            return this;
        }

        // Super redux version of reading the script ID for some subtype from Xml.
        static int readScriptId(final Resources resources, final InputMethodSubtype subtype) {
            final String layoutSetName = KEYBOARD_LAYOUT_SET_RESOURCE_PREFIX
                    + SubtypeLocaleUtils.getKeyboardLayoutSetName(subtype);
            final int xmlId = getXmlId(resources, layoutSetName);
            try (XmlResourceParser parser = resources.getXml(xmlId)) {
                while (parser.getEventType() != XmlPullParser.END_DOCUMENT) {
                    // Bovinate through the XML stupidly searching for TAG_FEATURE, and read
                    // the script Id from it.
                    parser.next();
                    final String tag = parser.getName();
                    if (TAG_FEATURE.equals(tag)) {
                        return readScriptIdFromTagFeature(resources, parser);
                    }
                }
            } catch (final IOException | XmlPullParserException e) {
                throw new RuntimeException(e.getMessage() + " in " + layoutSetName, e);
            }
            // If the tag is not found, then the default script is Latin.
            return ScriptUtils.SCRIPT_LATIN;
        }

        private static int readScriptIdFromTagFeature(final Resources resources,
                                                      final XmlPullParser parser) throws IOException, XmlPullParserException {
            final TypedArray featureAttr = resources.obtainAttributes(Xml.asAttributeSet(parser),
                    R.styleable.KeyboardLayoutSet_Feature);
            try {
                final int scriptId =
                        featureAttr.getInt(R.styleable.KeyboardLayoutSet_Feature_supportedScript,
                                ScriptUtils.SCRIPT_UNKNOWN);
                XmlParseUtils.checkEndTag(TAG_FEATURE, parser);
                return scriptId;
            } finally {
                featureAttr.recycle();
            }
        }

        public KeyboardLayoutSet build() {
            if (mParams.mSubtype == null)
                throw new RuntimeException("KeyboardLayoutSet subtype is not specified");
            final int xmlId = getXmlId(mResources, mParams.mKeyboardLayoutSetName);
            try {
                parseKeyboardLayoutSet(mResources, xmlId);
            } catch (final IOException | XmlPullParserException e) {
                throw new RuntimeException(e.getMessage() + " in " + mParams.mKeyboardLayoutSetName,
                        e);
            }
            return new KeyboardLayoutSet(mContext, mParams);
        }

        private static int getXmlId(final Resources resources, final String keyboardLayoutSetName) {
            final String packageName = resources.getResourcePackageName(
                    R.xml.keyboard_layout_set_qwerty);
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
                    } else if (TAG_FEATURE.equals(tag)) {
                        mParams.mScriptId = readScriptIdFromTagFeature(mResources, parser);
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
                XmlParseUtils.checkAttributeExists(a,
                        R.styleable.KeyboardLayoutSet_Element_elementKeyboard, "elementKeyboard",
                        TAG_ELEMENT, parser);
                XmlParseUtils.checkEndTag(TAG_ELEMENT, parser);

                final ElementParams elementParams = new ElementParams();
                final int elementName = a.getInt(
                        R.styleable.KeyboardLayoutSet_Element_elementName, 0);
                elementParams.mKeyboardXmlId = a.getResourceId(
                        R.styleable.KeyboardLayoutSet_Element_elementKeyboard, 0);
                elementParams.mProximityCharsCorrectionEnabled = a.getBoolean(
                        R.styleable.KeyboardLayoutSet_Element_enableProximityCharsCorrection,
                        false);
                elementParams.mSupportsSplitLayout = false; // this is to avoid xml parser reading split layouts, todo (later): remove mSupportsSplitLayout
                elementParams.mAllowRedundantMoreKeys = a.getBoolean(
                        R.styleable.KeyboardLayoutSet_Element_allowRedundantMoreKeys, true);
                mParams.mKeyboardLayoutSetElementIdToParamsMap.put(elementName, elementParams);
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
}
