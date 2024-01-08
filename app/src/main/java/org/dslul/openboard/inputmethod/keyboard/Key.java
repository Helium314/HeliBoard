/*
 * Copyright (C) 2010 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package org.dslul.openboard.inputmethod.keyboard;

import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;

import org.dslul.openboard.inputmethod.keyboard.internal.KeyDrawParams;
import org.dslul.openboard.inputmethod.keyboard.internal.KeySpecParser;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyVisualAttributes;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet;
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams;
import org.dslul.openboard.inputmethod.keyboard.internal.MoreKeySpec;
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.PopupSet;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.utils.MoreKeysUtilsKt;

import java.util.Arrays;
import java.util.Locale;

import static org.dslul.openboard.inputmethod.keyboard.internal.KeyboardIconsSet.ICON_UNDEFINED;
import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_ALPHA_FROM_NUMPAD;
import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_OUTPUT_TEXT;
import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_SHIFT;
import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_SWITCH_ALPHA_SYMBOL;
import static org.dslul.openboard.inputmethod.latin.common.Constants.CODE_UNSPECIFIED;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Class for describing the position and characteristics of a single key in the keyboard.
 */
public class Key implements Comparable<Key> {
    /**
     * The key code (unicode or custom code) that this key generates.
     */
    private final int mCode;

    /** Label to display */
    private final String mLabel;
    /** Hint label to display on the key in conjunction with the label */
    private final String mHintLabel;
    /** Flags of the label */
    private final int mLabelFlags;
    public static final int LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM = 0x02;
    public static final int LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM = 0x04;
    public static final int LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER = 0x08;
    // Font typeface specification.
    private static final int LABEL_FLAGS_FONT_MASK = 0x30;
    public static final int LABEL_FLAGS_FONT_NORMAL = 0x10;
    public static final int LABEL_FLAGS_FONT_MONO_SPACE = 0x20;
    public static final int LABEL_FLAGS_FONT_DEFAULT = 0x30;
    // Start of key text ratio enum values
    private static final int LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK = 0x1C0;
    public static final int LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO = 0x40;
    public static final int LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO = 0x80;
    public static final int LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO = 0xC0;
    public static final int LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO = 0x140;
    // End of key text ratio mask enum values
    public static final int LABEL_FLAGS_HAS_POPUP_HINT = 0x200;
    public static final int LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT = 0x400;
    public static final int LABEL_FLAGS_HAS_HINT_LABEL = 0x800;
    // The bit to calculate the ratio of key label width against key width. If autoXScale bit is on
    // and autoYScale bit is off, the key label may be shrunk only for X-direction.
    // If both autoXScale and autoYScale bits are on, the key label text size may be auto scaled.
    public static final int LABEL_FLAGS_AUTO_X_SCALE = 0x4000;
    public static final int LABEL_FLAGS_AUTO_Y_SCALE = 0x8000;
    public static final int LABEL_FLAGS_AUTO_SCALE = LABEL_FLAGS_AUTO_X_SCALE
            | LABEL_FLAGS_AUTO_Y_SCALE;
    public static final int LABEL_FLAGS_PRESERVE_CASE = 0x10000;
    public static final int LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED = 0x20000;
    public static final int LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL = 0x40000;
    public static final int LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR = 0x80000;
    public static final int LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO = 0x100000;
    public static final int LABEL_FLAGS_DISABLE_HINT_LABEL = 0x40000000;
    public static final int LABEL_FLAGS_DISABLE_ADDITIONAL_MORE_KEYS = 0x80000000;

    /** Icon to display instead of a label. Icon takes precedence over a label */
    private final int mIconId;

    /** Width of the key, excluding the gap */
    private final int mWidth;
    /** Height of the key, excluding the gap */
    private final int mHeight;
    /**
     * The combined width in pixels of the horizontal gaps belonging to this key, both to the left
     * and to the right. I.e., mWidth + mHorizontalGap = total width belonging to the key.
     */
    private final int mHorizontalGap;
    /**
     * The combined height in pixels of the vertical gaps belonging to this key, both above and
     * below. I.e., mHeight + mVerticalGap = total height belonging to the key.
     */
    private final int mVerticalGap;
    /** X coordinate of the top-left corner of the key in the keyboard layout, excluding the gap. */
    private final int mX;
    /** Y coordinate of the top-left corner of the key in the keyboard layout, excluding the gap. */
    private final int mY;
    /** Hit bounding box of the key */
    @NonNull
    private final Rect mHitBox = new Rect();

    /** More keys. It is guaranteed that this is null or an array of one or more elements */
    @Nullable
    private final MoreKeySpec[] mMoreKeys;
    /** More keys column number and flags */
    private final int mMoreKeysColumnAndFlags;
    private static final int MORE_KEYS_COLUMN_NUMBER_MASK = 0x000000ff;
    // If this flag is specified, more keys keyboard should have the specified number of columns.
    // Otherwise more keys keyboard should have less than or equal to the specified maximum number
    // of columns.
    private static final int MORE_KEYS_FLAGS_FIXED_COLUMN = 0x00000100;
    // If this flag is specified, the order of more keys is determined by the order in the more
    // keys' specification. Otherwise the order of more keys is automatically determined.
    private static final int MORE_KEYS_FLAGS_FIXED_ORDER = 0x00000200;
    private static final int MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER = 0;
    private static final int MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER =
            MORE_KEYS_FLAGS_FIXED_COLUMN;
    private static final int MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER =
            (MORE_KEYS_FLAGS_FIXED_COLUMN | MORE_KEYS_FLAGS_FIXED_ORDER);
    private static final int MORE_KEYS_FLAGS_HAS_LABELS = 0x40000000;
    private static final int MORE_KEYS_FLAGS_NEEDS_DIVIDERS = 0x20000000;
    private static final int MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY = 0x10000000;
    // TODO: Rename these specifiers to !autoOrder! and !fixedOrder! respectively.
    public static final String MORE_KEYS_AUTO_COLUMN_ORDER = "!autoColumnOrder!";
    public static final String MORE_KEYS_FIXED_COLUMN_ORDER = "!fixedColumnOrder!";
    public static final String MORE_KEYS_HAS_LABELS = "!hasLabels!";
    private static final String MORE_KEYS_NEEDS_DIVIDERS = "!needsDividers!";
    private static final String MORE_KEYS_NO_PANEL_AUTO_MORE_KEY = "!noPanelAutoMoreKey!";

    /** Background type that represents different key background visual than normal one. */
    private final int mBackgroundType;
    public static final int BACKGROUND_TYPE_EMPTY = 0;
    public static final int BACKGROUND_TYPE_NORMAL = 1;
    public static final int BACKGROUND_TYPE_FUNCTIONAL = 2;
    public static final int BACKGROUND_TYPE_STICKY_OFF = 3;
    public static final int BACKGROUND_TYPE_STICKY_ON = 4;
    public static final int BACKGROUND_TYPE_ACTION = 5;
    public static final int BACKGROUND_TYPE_SPACEBAR = 6;

    private final int mActionFlags;
    private static final int ACTION_FLAGS_IS_REPEATABLE = 0x01;
    private static final int ACTION_FLAGS_NO_KEY_PREVIEW = 0x02;
    private static final int ACTION_FLAGS_ALT_CODE_WHILE_TYPING = 0x04;
    private static final int ACTION_FLAGS_ENABLE_LONG_PRESS = 0x08;

    @Nullable
    private final KeyVisualAttributes mKeyVisualAttributes;
    @Nullable
    private final OptionalAttributes mOptionalAttributes;

    private static final class OptionalAttributes {
        /** Text to output when pressed. This can be multiple characters, like ".com" */
        public final String mOutputText;
        public final int mAltCode;
        /** Icon for disabled state */
        public final int mDisabledIconId;
        /** The visual insets */
        public final int mVisualInsetsLeft;
        public final int mVisualInsetsRight;

        private OptionalAttributes(final String outputText, final int altCode,
                final int disabledIconId, final int visualInsetsLeft, final int visualInsetsRight) {
            mOutputText = outputText;
            mAltCode = altCode;
            mDisabledIconId = disabledIconId;
            mVisualInsetsLeft = visualInsetsLeft;
            mVisualInsetsRight = visualInsetsRight;
        }

        @Nullable
        public static OptionalAttributes newInstance(final String outputText, final int altCode,
                final int disabledIconId, final int visualInsetsLeft, final int visualInsetsRight) {
            if (outputText == null && altCode == CODE_UNSPECIFIED
                    && disabledIconId == ICON_UNDEFINED && visualInsetsLeft == 0
                    && visualInsetsRight == 0) {
                return null;
            }
            return new OptionalAttributes(outputText, altCode, disabledIconId, visualInsetsLeft,
                    visualInsetsRight);
        }
    }

    private final int mHashCode;

    /** The current pressed state of this key */
    private boolean mPressed;
    /** Key is enabled and responds on press */
    private boolean mEnabled = true;

    /**
     * Constructor for a key on <code>MoreKeyKeyboard</code> and on <code>MoreSuggestions</code>.
     */
    public Key(@Nullable final String label, final int iconId, final int code,
            @Nullable final String outputText, @Nullable final String hintLabel,
            final int labelFlags, final int backgroundType, final int x, final int y,
            final int width, final int height, final int horizontalGap, final int verticalGap) {
        mWidth = width - horizontalGap;
        mHeight = height - verticalGap;
        mHorizontalGap = horizontalGap;
        mVerticalGap = verticalGap;
        mHintLabel = hintLabel;
        mLabelFlags = labelFlags;
        mBackgroundType = backgroundType;
        // TODO: Pass keyActionFlags as an argument.
        mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW;
        mMoreKeys = null;
        mMoreKeysColumnAndFlags = 0;
        mLabel = label;
        mOptionalAttributes = OptionalAttributes.newInstance(outputText, CODE_UNSPECIFIED,
                ICON_UNDEFINED, 0 /* visualInsetsLeft */, 0 /* visualInsetsRight */);
        mCode = code;
        mEnabled = (code != CODE_UNSPECIFIED);
        mIconId = iconId;
        // Horizontal gap is divided equally to both sides of the key.
        mX = x + mHorizontalGap / 2;
        mY = y;
        mHitBox.set(x, y, x + width + 1, y + height);
        mKeyVisualAttributes = null;

        mHashCode = computeHashCode(this);
    }

    /**
     * Copy constructor for DynamicGridKeyboard.GridKey.
     *
     * @param key the original key.
     * @param moreKeys the more keys that should be assigned to this key.
     * @param labelHint the label hint that should be assigned to this key.
     * @param backgroundType the background type that should be assigned to this key.
     */
    protected Key(@NonNull final Key key, @Nullable final MoreKeySpec[] moreKeys,
                @Nullable final String labelHint, final int backgroundType) {
        // Final attributes.
        mCode = key.mCode;
        mLabel = key.mLabel;
        mHintLabel = labelHint;
        mLabelFlags = key.mLabelFlags;
        mIconId = key.mIconId;
        mWidth = key.mWidth;
        mHeight = key.mHeight;
        mHorizontalGap = key.mHorizontalGap;
        mVerticalGap = key.mVerticalGap;
        mX = key.mX;
        mY = key.mY;
        mHitBox.set(key.mHitBox);
        mMoreKeys = moreKeys;
        mMoreKeysColumnAndFlags = key.mMoreKeysColumnAndFlags;
        mBackgroundType = backgroundType;
        mActionFlags = key.mActionFlags;
        mKeyVisualAttributes = key.mKeyVisualAttributes;
        mOptionalAttributes = key.mOptionalAttributes;
        mHashCode = key.mHashCode;
        // Key state.
        mPressed = key.mPressed;
        mEnabled = key.mEnabled;
    }

    /** constructor for creating emoji recent keys when there is no keyboard to take keys from */
    public Key(@NonNull final Key key, @Nullable final MoreKeySpec[] moreKeys,
             @Nullable final String labelHint, final int backgroundType, final int code, @Nullable final String outputText) {
        // Final attributes.
        mCode = outputText == null ? code : CODE_OUTPUT_TEXT;
        mLabel = outputText == null ? StringUtils.newSingleCodePointString(code) : outputText;
        mHintLabel = labelHint;
        mLabelFlags = key.mLabelFlags;
        mIconId = key.mIconId;
        mWidth = key.mWidth;
        mHeight = key.mHeight;
        mHorizontalGap = key.mHorizontalGap;
        mVerticalGap = key.mVerticalGap;
        mX = key.mX;
        mY = key.mY;
        mHitBox.set(key.mHitBox);
        mMoreKeys = moreKeys;
        mMoreKeysColumnAndFlags = key.mMoreKeysColumnAndFlags;
        mBackgroundType = backgroundType;
        mActionFlags = key.mActionFlags;
        mKeyVisualAttributes = key.mKeyVisualAttributes;
        mOptionalAttributes = outputText == null ? null : Key.OptionalAttributes.newInstance(outputText, CODE_UNSPECIFIED, ICON_UNDEFINED, 0, 0);
        mHashCode = key.mHashCode;
        // Key state.
        mPressed = key.mPressed;
        mEnabled = key.mEnabled;
    }

    /** constructor from KeyParams */
    private Key(KeyParams keyParams) {
        // stuff to copy
        mCode = keyParams.mCode;
        mLabel = keyParams.mLabel;
        mHintLabel = keyParams.mHintLabel;
        mLabelFlags = keyParams.mLabelFlags;
        mIconId = keyParams.mIconId;
        mMoreKeys = keyParams.mMoreKeys;
        mMoreKeysColumnAndFlags = keyParams.mMoreKeysColumnAndFlags;
        mBackgroundType = keyParams.mBackgroundType;
        mActionFlags = keyParams.mActionFlags;
        mKeyVisualAttributes = keyParams.mKeyVisualAttributes;
        mOptionalAttributes = keyParams.mOptionalAttributes;
        mEnabled = keyParams.mEnabled;

        // stuff to create

        // get the "correct" float gap: may shift keys by one pixel, but results in more uniform gaps between keys
        final float horizontalGapFloat = isSpacer() ? 0 : (keyParams.mKeyboardParams.mRelativeHorizontalGap * keyParams.mKeyboardParams.mOccupiedWidth);
        mHorizontalGap = Math.round(horizontalGapFloat);
        mVerticalGap = Math.round(keyParams.mKeyboardParams.mRelativeVerticalGap * keyParams.mKeyboardParams.mOccupiedHeight);
        mWidth = Math.round(keyParams.mFullWidth - horizontalGapFloat);
        // height is always rounded down, because rounding up may make the keyboard too high to fit, leading to issues
        mHeight = (int) (keyParams.mFullHeight - keyParams.mKeyboardParams.mVerticalGap);
        if (!isSpacer() && (mWidth == 0 || mHeight == 0)) {
            throw new IllegalStateException("key needs positive width and height");
        }
        // Horizontal gap is divided equally to both sides of the key.
        mX = Math.round(keyParams.xPos + horizontalGapFloat / 2);
        mY = Math.round(keyParams.yPos);
        mHitBox.set(Math.round(keyParams.xPos), Math.round(keyParams.yPos), Math.round(keyParams.xPos + keyParams.mFullWidth) + 1,
                Math.round(keyParams.yPos + keyParams.mFullHeight));
        mHashCode = computeHashCode(this);
    }

    private Key(@NonNull final Key key, @Nullable final MoreKeySpec[] moreKeys) {
        // Final attributes.
        mCode = key.mCode;
        mLabel = key.mLabel;
        mHintLabel = key.mHintLabel;
        mLabelFlags = key.mLabelFlags;
        mIconId = key.mIconId;
        mWidth = key.mWidth;
        mHeight = key.mHeight;
        mHorizontalGap = key.mHorizontalGap;
        mVerticalGap = key.mVerticalGap;
        mX = key.mX;
        mY = key.mY;
        mHitBox.set(key.mHitBox);
        mMoreKeys = moreKeys;
        mMoreKeysColumnAndFlags = key.mMoreKeysColumnAndFlags;
        mBackgroundType = key.mBackgroundType;
        if (moreKeys == null && mCode > Constants.CODE_SPACE && (key.mActionFlags & ACTION_FLAGS_ENABLE_LONG_PRESS) != 0)
            mActionFlags = key.mActionFlags - ACTION_FLAGS_ENABLE_LONG_PRESS;
        else
            mActionFlags = key.mActionFlags;
        mKeyVisualAttributes = key.mKeyVisualAttributes;
        mOptionalAttributes = key.mOptionalAttributes;
        mHashCode = key.mHashCode;
        // Key state.
        mPressed = key.mPressed;
        mEnabled = key.mEnabled;
    }

    @NonNull
    public static Key removeRedundantMoreKeys(@NonNull final Key key,
            @NonNull final MoreKeySpec.LettersOnBaseLayout lettersOnBaseLayout) {
        final MoreKeySpec[] moreKeys = key.getMoreKeys();
        final MoreKeySpec[] filteredMoreKeys = MoreKeySpec.removeRedundantMoreKeys(
                moreKeys, lettersOnBaseLayout);
        return (filteredMoreKeys == moreKeys) ? key : new Key(key, filteredMoreKeys);
    }

    private static boolean needsToUpcase(final int labelFlags, final int keyboardElementId) {
        if ((labelFlags & LABEL_FLAGS_PRESERVE_CASE) != 0) return false;
        return switch (keyboardElementId) {
            case KeyboardId.ELEMENT_ALPHABET_MANUAL_SHIFTED, KeyboardId.ELEMENT_ALPHABET_AUTOMATIC_SHIFTED,
                    KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCKED, KeyboardId.ELEMENT_ALPHABET_SHIFT_LOCK_SHIFTED -> true;
            default -> false;
        };
    }

    private static int computeHashCode(final Key key) {
        return Arrays.hashCode(new Object[] {
                key.mX,
                key.mY,
                key.mWidth,
                key.mHeight,
                key.mCode,
                key.mLabel,
                key.mHintLabel,
                key.mIconId,
                key.mBackgroundType,
                Arrays.hashCode(key.mMoreKeys),
                key.getOutputText(),
                key.mActionFlags,
                key.mLabelFlags,
                // Key can be distinguishable without the following members.
                // key.mOptionalAttributes.mAltCode,
                // key.mOptionalAttributes.mDisabledIconId,
                // key.mOptionalAttributes.mPreviewIconId,
                // key.mHorizontalGap,
                // key.mVerticalGap,
                // key.mOptionalAttributes.mVisualInsetLeft,
                // key.mOptionalAttributes.mVisualInsetRight,
                // key.mMaxMoreKeysColumn,
        });
    }

    private boolean equalsInternal(final Key o) {
        if (this == o) return true;
        return o.mX == mX
                && o.mY == mY
                && o.mWidth == mWidth
                && o.mHeight == mHeight
                && o.mCode == mCode
                && TextUtils.equals(o.mLabel, mLabel)
                && TextUtils.equals(o.mHintLabel, mHintLabel)
                && o.mIconId == mIconId
                && o.mBackgroundType == mBackgroundType
                && Arrays.equals(o.mMoreKeys, mMoreKeys)
                && TextUtils.equals(o.getOutputText(), getOutputText())
                && o.mActionFlags == mActionFlags
                && o.mLabelFlags == mLabelFlags;
    }

    @Override
    public int compareTo(Key o) {
        if (equalsInternal(o)) return 0;
        if (mHashCode > o.mHashCode) return 1;
        return -1;
    }

    @Override
    public int hashCode() {
        return mHashCode;
    }

    @Override
    public boolean equals(final Object o) {
        return o instanceof Key && equalsInternal((Key)o);
    }

    @Override
    public String toString() {
        return toShortString() + " " + getX() + "," + getY() + " " + getWidth() + "x" + getHeight();
    }

    public String toShortString() {
        final int code = getCode();
        if (code == Constants.CODE_OUTPUT_TEXT) {
            return getOutputText();
        }
        return Constants.printableCode(code);
    }

    public String toLongString() {
        final int iconId = getIconId();
        final String topVisual = (iconId == KeyboardIconsSet.ICON_UNDEFINED)
                ? KeyboardIconsSet.PREFIX_ICON + KeyboardIconsSet.getIconName(iconId) : getLabel();
        final String hintLabel = getHintLabel();
        final String visual = (hintLabel == null) ? topVisual : topVisual + "^" + hintLabel;
        return toString() + " " + visual + "/" + backgroundName(mBackgroundType);
    }

    private static String backgroundName(final int backgroundType) {
        return switch (backgroundType) {
            case BACKGROUND_TYPE_EMPTY -> "empty";
            case BACKGROUND_TYPE_NORMAL -> "normal";
            case BACKGROUND_TYPE_FUNCTIONAL -> "functional";
            case BACKGROUND_TYPE_STICKY_OFF -> "stickyOff";
            case BACKGROUND_TYPE_STICKY_ON -> "stickyOn";
            case BACKGROUND_TYPE_ACTION -> "action";
            case BACKGROUND_TYPE_SPACEBAR -> "spacebar";
            default -> null;
        };
    }

    public int getCode() {
        return mCode;
    }

    @Nullable
    public String getLabel() {
        return mLabel;
    }

    @Nullable
    public String getHintLabel() {
        return mHintLabel;
    }

    @Nullable
    public MoreKeySpec[] getMoreKeys() {
        return mMoreKeys;
    }

    public void markAsLeftEdge(final KeyboardParams params) {
        mHitBox.left = params.mLeftPadding;
    }

    public void markAsRightEdge(final KeyboardParams params) {
        mHitBox.right = params.mOccupiedWidth - params.mRightPadding;
    }

    public void markAsTopEdge(final KeyboardParams params) {
        mHitBox.top = params.mTopPadding;
    }

    public void markAsBottomEdge(final KeyboardParams params) {
        mHitBox.bottom = params.mOccupiedHeight + params.mBottomPadding;
    }

    public final boolean isSpacer() {
        return this instanceof Spacer;
    }

    public final boolean isActionKey() {
        return mBackgroundType == BACKGROUND_TYPE_ACTION;
    }

    public final boolean isShift() {
        return mCode == CODE_SHIFT;
    }

    public final boolean isModifier() {
        return mCode == CODE_SHIFT || mCode == CODE_SWITCH_ALPHA_SYMBOL || mCode == CODE_ALPHA_FROM_NUMPAD;
    }

    public final boolean isRepeatable() {
        return (mActionFlags & ACTION_FLAGS_IS_REPEATABLE) != 0;
    }

    public final boolean noKeyPreview() {
        return (mActionFlags & ACTION_FLAGS_NO_KEY_PREVIEW) != 0;
    }

    public final boolean altCodeWhileTyping() {
        return (mActionFlags & ACTION_FLAGS_ALT_CODE_WHILE_TYPING) != 0;
    }

    public final boolean isLongPressEnabled() {
        // We need not start long press timer on the key which has activated shifted letter.
        return (mActionFlags & ACTION_FLAGS_ENABLE_LONG_PRESS) != 0
                && (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) == 0;
    }

    public KeyVisualAttributes getVisualAttributes() {
        return mKeyVisualAttributes;
    }

    @NonNull
    public final Typeface selectTypeface(final KeyDrawParams params) {
        return switch (mLabelFlags & LABEL_FLAGS_FONT_MASK) {
            case LABEL_FLAGS_FONT_NORMAL -> Typeface.DEFAULT;
            case LABEL_FLAGS_FONT_MONO_SPACE -> Typeface.MONOSPACE;
            default -> params.mTypeface; // The type-face is specified by keyTypeface attribute.
        };
    }

    public final int selectTextSize(final KeyDrawParams params) {
        return switch (mLabelFlags & LABEL_FLAGS_FOLLOW_KEY_TEXT_RATIO_MASK) {
            case LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO -> params.mLetterSize;
            case LABEL_FLAGS_FOLLOW_KEY_LARGE_LETTER_RATIO -> params.mLargeLetterSize;
            case LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO -> params.mLabelSize;
            case LABEL_FLAGS_FOLLOW_KEY_HINT_LABEL_RATIO -> params.mHintLabelSize;
            // No follow key ratio flag specified.
            default -> StringUtils.codePointCount(mLabel) == 1 ? params.mLetterSize : params.mLabelSize;
        };
    }

    public final int selectTextColor(final KeyDrawParams params) {
        if ((mLabelFlags & LABEL_FLAGS_FOLLOW_FUNCTIONAL_TEXT_COLOR) != 0) {
            return params.mFunctionalTextColor;
        }
        return isShiftedLetterActivated() ? params.mTextInactivatedColor : params.mTextColor;
    }

    public final int selectHintTextSize(final KeyDrawParams params) {
        if (hasHintLabel()) {
            return params.mHintLabelSize;
        }
        if (hasShiftedLetterHint()) {
            return params.mShiftedLetterHintSize;
        }
        return params.mHintLetterSize;
    }

    public final int selectHintTextColor(final KeyDrawParams params) {
        if (hasHintLabel()) {
            return params.mHintLabelColor;
        }
        if (hasShiftedLetterHint()) {
            return isShiftedLetterActivated() ? params.mShiftedLetterHintActivatedColor
                    : params.mShiftedLetterHintInactivatedColor;
        }
        return params.mHintLetterColor;
    }

    public final int selectMoreKeyTextSize(final KeyDrawParams params) {
        return hasLabelsInMoreKeys() ? params.mLabelSize : params.mLetterSize;
    }

    public final String getPreviewLabel() {
        return isShiftedLetterActivated() ? mHintLabel : mLabel;
    }

    private boolean previewHasLetterSize() {
        return (mLabelFlags & LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO) != 0
                || StringUtils.codePointCount(getPreviewLabel()) == 1;
    }

    public final int selectPreviewTextSize(final KeyDrawParams params) {
        if (previewHasLetterSize()) {
            return params.mPreviewTextSize;
        }
        return params.mLetterSize;
    }

    @NonNull
    public Typeface selectPreviewTypeface(final KeyDrawParams params) {
        if (previewHasLetterSize()) {
            return selectTypeface(params);
        }
        return Typeface.DEFAULT_BOLD;
    }

    public final boolean isAlignHintLabelToBottom(final int defaultFlags) {
        return ((mLabelFlags | defaultFlags) & LABEL_FLAGS_ALIGN_HINT_LABEL_TO_BOTTOM) != 0;
    }

    public final boolean isAlignIconToBottom() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_ICON_TO_BOTTOM) != 0;
    }

    public final boolean isAlignLabelOffCenter() {
        return (mLabelFlags & LABEL_FLAGS_ALIGN_LABEL_OFF_CENTER) != 0;
    }

    public final boolean hasPopupHint() {
        return (mLabelFlags & LABEL_FLAGS_HAS_POPUP_HINT) != 0;
    }

    public final boolean hasShiftedLetterHint() {
        return (mLabelFlags & LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0
                && !TextUtils.isEmpty(mHintLabel);
    }

    public final boolean hasHintLabel() {
        return (mLabelFlags & LABEL_FLAGS_HAS_HINT_LABEL) != 0;
    }

    public final boolean needsAutoXScale() {
        return (mLabelFlags & LABEL_FLAGS_AUTO_X_SCALE) != 0;
    }

    public final boolean needsAutoScale() {
        return (mLabelFlags & LABEL_FLAGS_AUTO_SCALE) == LABEL_FLAGS_AUTO_SCALE;
    }

    public final boolean needsToKeepBackgroundAspectRatio(final int defaultFlags) {
        return ((mLabelFlags | defaultFlags) & LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO) != 0;
    }

    public final boolean hasCustomActionLabel() {
        return (mLabelFlags & LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0;
    }

    private final boolean isShiftedLetterActivated() {
        return (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0
                && !TextUtils.isEmpty(mHintLabel);
    }

    public final int getMoreKeysColumnNumber() {
        return mMoreKeysColumnAndFlags & MORE_KEYS_COLUMN_NUMBER_MASK;
    }

    public final boolean isMoreKeysFixedColumn() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_FIXED_COLUMN) != 0;
    }

    public final boolean isMoreKeysFixedOrder() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_FIXED_ORDER) != 0;
    }

    public final boolean hasLabelsInMoreKeys() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_HAS_LABELS) != 0;
    }

    public final int getMoreKeyLabelFlags() {
        final int labelSizeFlag = hasLabelsInMoreKeys()
                ? LABEL_FLAGS_FOLLOW_KEY_LABEL_RATIO
                : LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO;
        return labelSizeFlag | LABEL_FLAGS_AUTO_X_SCALE;
    }

    public final boolean needsDividersInMoreKeys() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_NEEDS_DIVIDERS) != 0;
    }

    public final boolean hasNoPanelAutoMoreKey() {
        return (mMoreKeysColumnAndFlags & MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY) != 0;
    }

    @Nullable
    public final String getOutputText() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs != null) ? attrs.mOutputText : null;
    }

    public final int getAltCode() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs != null) ? attrs.mAltCode : CODE_UNSPECIFIED;
    }

    public int getIconId() {
        return mIconId;
    }

    @Nullable
    public Drawable getIcon(final KeyboardIconsSet iconSet, final int alpha) {
        final OptionalAttributes attrs = mOptionalAttributes;
        final int disabledIconId = (attrs != null) ? attrs.mDisabledIconId : ICON_UNDEFINED;
        final int iconId = mEnabled ? getIconId() : disabledIconId;
        final Drawable icon = iconSet.getIconDrawable(iconId);
        if (icon != null) {
            icon.setAlpha(alpha);
        }
        return icon;
    }

    @Nullable
    public Drawable getPreviewIcon(final KeyboardIconsSet iconSet) {
        return iconSet.getIconDrawable(getIconId());
    }

    /**
     * Gets the background type of this key.
     * @return Background type.
     * @see Key#BACKGROUND_TYPE_EMPTY
     * @see Key#BACKGROUND_TYPE_NORMAL
     * @see Key#BACKGROUND_TYPE_FUNCTIONAL
     * @see Key#BACKGROUND_TYPE_STICKY_OFF
     * @see Key#BACKGROUND_TYPE_STICKY_ON
     * @see Key#BACKGROUND_TYPE_ACTION
     * @see Key#BACKGROUND_TYPE_SPACEBAR
     */
    public int getBackgroundType() {
        return mBackgroundType;
    }

    /**
     * Gets the width of the key in pixels, excluding the gap.
     * @return The width of the key in pixels, excluding the gap.
     */
    public int getWidth() {
        return mWidth;
    }

    /**
     * Gets the height of the key in pixels, excluding the gap.
     * @return The height of the key in pixels, excluding the gap.
     */
    public int getHeight() {
        return mHeight;
    }

    /**
     * The combined width in pixels of the horizontal gaps belonging to this key, both above and
     * below. I.e., getWidth() + getHorizontalGap() = total width belonging to the key.
     * @return Horizontal gap belonging to this key.
     */
    public int getHorizontalGap() {
        return mHorizontalGap;
    }

    /**
     * The combined height in pixels of the vertical gaps belonging to this key, both above and
     * below. I.e., getHeight() + getVerticalGap() = total height belonging to the key.
     * @return Vertical gap belonging to this key.
     */
    public int getVerticalGap() {
        return mVerticalGap;
    }

    /**
     * Gets the x-coordinate of the top-left corner of the key in pixels, excluding the gap.
     * @return The x-coordinate of the top-left corner of the key in pixels, excluding the gap.
     */
    public int getX() {
        return mX;
    }

    /**
     * Gets the y-coordinate of the top-left corner of the key in pixels, excluding the gap.
     * @return The y-coordinate of the top-left corner of the key in pixels, excluding the gap.
     */
    public int getY() {
        return mY;
    }

    public final int getDrawX() {
        final int x = getX();
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs == null) ? x : x + attrs.mVisualInsetsLeft;
    }

    public final int getDrawWidth() {
        final OptionalAttributes attrs = mOptionalAttributes;
        return (attrs == null) ? mWidth
                : mWidth - attrs.mVisualInsetsLeft - attrs.mVisualInsetsRight;
    }

    /**
     * Informs the key that it has been pressed, in case it needs to change its appearance or
     * state.
     * @see #onReleased()
     */
    public void onPressed() {
        mPressed = true;
    }

    /**
     * Informs the key that it has been released, in case it needs to change its appearance or
     * state.
     * @see #onPressed()
     */
    public void onReleased() {
        mPressed = false;
    }

    public final boolean isEnabled() {
        return mEnabled;
    }

    public void setEnabled(final boolean enabled) {
        mEnabled = enabled;
    }

    @NonNull
    public Rect getHitBox() {
        return mHitBox;
    }

    /**
     * Detects if a point falls on this key.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return whether or not the point falls on the key. If the key is attached to an edge, it
     * will assume that all points between the key and the edge are considered to be on the key.
     * @see #markAsLeftEdge(KeyboardParams) etc.
     */
    public boolean isOnKey(final int x, final int y) {
        return mHitBox.contains(x, y);
    }

    /**
     * Returns the square of the distance to the nearest edge of the key and the given point.
     * @param x the x-coordinate of the point
     * @param y the y-coordinate of the point
     * @return the square of the distance of the point from the nearest edge of the key
     */
    public int squaredDistanceToEdge(final int x, final int y) {
        final int left = getX();
        final int right = left + mWidth;
        final int top = getY();
        final int bottom = top + mHeight;
        final int edgeX = x < left ? left : Math.min(x, right);
        final int edgeY = y < top ? top : Math.min(y, bottom);
        final int dx = x - edgeX;
        final int dy = y - edgeY;
        return dx * dx + dy * dy;
    }

    static class KeyBackgroundState {
        private final int[] mReleasedState;
        private final int[] mPressedState;

        private KeyBackgroundState(final int ... attrs) {
            mReleasedState = attrs;
            mPressedState = Arrays.copyOf(attrs, attrs.length + 1);
            mPressedState[attrs.length] = android.R.attr.state_pressed;
        }

        public int[] getState(final boolean pressed) {
            return pressed ? mPressedState : mReleasedState;
        }

        public static final KeyBackgroundState[] STATES = {
            // 0: BACKGROUND_TYPE_EMPTY
            new KeyBackgroundState(android.R.attr.state_empty),
            // 1: BACKGROUND_TYPE_NORMAL
            new KeyBackgroundState(),
            // 2: BACKGROUND_TYPE_FUNCTIONAL
            new KeyBackgroundState(),
            // 3: BACKGROUND_TYPE_STICKY_OFF
            new KeyBackgroundState(android.R.attr.state_checkable),
            // 4: BACKGROUND_TYPE_STICKY_ON
            new KeyBackgroundState(android.R.attr.state_checkable, android.R.attr.state_checked),
            // 5: BACKGROUND_TYPE_ACTION
            new KeyBackgroundState(android.R.attr.state_active),
            // 6: BACKGROUND_TYPE_SPACEBAR
            new KeyBackgroundState(),
        };
    }

    /**
     * Returns the background drawable for the key, based on the current state and type of the key.
     * @return the background drawable of the key.
     * @see android.graphics.drawable.StateListDrawable#setState(int[])
     */
    @NonNull
    public final Drawable selectBackgroundDrawable(@NonNull final Drawable keyBackground,
            @NonNull final Drawable functionalKeyBackground,
            @NonNull final Drawable spacebarBackground,
           @NonNull final Drawable actionKeyBackground) {
        final Drawable background;
        if (isAccentColored()) {
            background = actionKeyBackground;
        } else if (isFunctional()) {
            background = functionalKeyBackground;
        } else if (mBackgroundType == BACKGROUND_TYPE_SPACEBAR) {
            background = spacebarBackground;
        } else {
            background = keyBackground;
        }
        final int[] state = KeyBackgroundState.STATES[mBackgroundType].getState(mPressed);
        background.setState(state);
        return background;
    }

    public final boolean isAccentColored() {
        if (isActionKey()) return true;
        final String iconName = KeyboardIconsSet.getIconName(getIconId());
        return iconName.equals(KeyboardIconsSet.NAME_NEXT_KEY)
                || iconName.equals(KeyboardIconsSet.NAME_PREVIOUS_KEY)
                || iconName.equals(KeyboardIconsSet.NAME_CLIPBOARD_ACTION_KEY)
                || iconName.equals(KeyboardIconsSet.NAME_EMOJI_ACTION_KEY);
    }

    public boolean isFunctional() {
        return mBackgroundType == BACKGROUND_TYPE_FUNCTIONAL
                || mBackgroundType == BACKGROUND_TYPE_STICKY_OFF
                || mBackgroundType == BACKGROUND_TYPE_STICKY_ON;
    }

    public static class Spacer extends Key {
        private Spacer(KeyParams keyParams) {
            super(keyParams);
        }

        /**
         * This constructor is being used only for divider in more keys keyboard.
         */
        protected Spacer(final KeyboardParams params, final int x, final int y, final int width,
                final int height) {
            super(null /* label */, ICON_UNDEFINED, CODE_UNSPECIFIED, null /* outputText */,
                    null /* hintLabel */, 0 /* labelFlags */, BACKGROUND_TYPE_EMPTY, x, y, width,
                    height, params.mHorizontalGap, params.mVerticalGap);
        }
    }

    // for creating keys that might get modified later
    public static class KeyParams {
        // params for building
        public boolean isSpacer;
        private final KeyboardParams mKeyboardParams; // for reading gaps and keyboard width / height
        public float mRelativeWidth;
        public float mRelativeHeight; // also should allow negative values, indicating absolute height is defined
        public float mRelativeVisualInsetLeft;
        public float mRelativeVisualInsetRight;

        // params that may change
        public float mFullWidth;
        public float mFullHeight;
        public float xPos;
        public float yPos;

        // params that remains constant
        public final int mCode;
        @Nullable public String mLabel;
        @Nullable public final String mHintLabel;
        public final int mLabelFlags;
        public final int mIconId;
        @Nullable public MoreKeySpec[] mMoreKeys;
        public final int mMoreKeysColumnAndFlags;
        public int mBackgroundType;
        public final int mActionFlags;
        @Nullable public final KeyVisualAttributes mKeyVisualAttributes;
        @Nullable public OptionalAttributes mOptionalAttributes;
        public final boolean mEnabled;

        public static KeyParams newSpacer(final KeyboardParams params, final float relativeWidth) {
            final KeyParams spacer = new KeyParams(params);
            spacer.mRelativeWidth = relativeWidth;
            spacer.mRelativeHeight = params.mDefaultRelativeRowHeight;
            return spacer;
        }

        public Key createKey() {
            if (isSpacer) return new Spacer(this);
            return new Key(this);
        }

        public void setDimensionsFromRelativeSize(final float newX, final float newY) {
            if (mRelativeHeight == 0)
                mRelativeHeight = mKeyboardParams.mDefaultRelativeRowHeight;
            if (!isSpacer && mRelativeWidth == 0)
                mRelativeWidth = mKeyboardParams.mDefaultRelativeKeyWidth;
            if (mRelativeHeight < 0)
                // todo (later): deal with it properly when it needs to be adjusted, i.e. when changing moreKeys or moreSuggestions
                throw new IllegalStateException("can't (yet) deal with absolute height");
            xPos = newX;
            yPos = newY;
            mFullWidth = mRelativeWidth * mKeyboardParams.mBaseWidth;
            mFullHeight = mRelativeHeight * mKeyboardParams.mBaseHeight;

            // set visual insets if any
            if (mRelativeVisualInsetRight != 0f || mRelativeVisualInsetLeft != 0f) {
                final int insetLeft = (int) (mRelativeVisualInsetLeft * mKeyboardParams.mBaseWidth);
                final int insetRight = (int) (mRelativeVisualInsetRight * mKeyboardParams.mBaseWidth);
                if (mOptionalAttributes == null) {
                    mOptionalAttributes = OptionalAttributes.newInstance(null, CODE_UNSPECIFIED, ICON_UNDEFINED, insetLeft, insetRight);
                } else {
                    mOptionalAttributes = OptionalAttributes
                            .newInstance(mOptionalAttributes.mOutputText, mOptionalAttributes.mAltCode, mOptionalAttributes.mDisabledIconId, insetLeft, insetRight);
                }
            }
        }

        private static int getMoreKeysColumnAndFlagsAndSetNullInArray(final KeyboardParams params, final String[] moreKeys) {
            // Get maximum column order number and set a relevant mode value.
            int moreKeysColumnAndFlags = MORE_KEYS_MODE_MAX_COLUMN_WITH_AUTO_ORDER | params.mMaxMoreKeysKeyboardColumn;
            int value;
            if ((value = MoreKeySpec.getIntValue(moreKeys, MORE_KEYS_AUTO_COLUMN_ORDER, -1)) > 0) {
                // Override with fixed column order number and set a relevant mode value.
                moreKeysColumnAndFlags = MORE_KEYS_MODE_FIXED_COLUMN_WITH_AUTO_ORDER | (value & MORE_KEYS_COLUMN_NUMBER_MASK);
            }
            if ((value = MoreKeySpec.getIntValue(moreKeys, MORE_KEYS_FIXED_COLUMN_ORDER, -1)) > 0) {
                // Override with fixed column order number and set a relevant mode value.
                moreKeysColumnAndFlags = MORE_KEYS_MODE_FIXED_COLUMN_WITH_FIXED_ORDER | (value & MORE_KEYS_COLUMN_NUMBER_MASK);
            }
            if (MoreKeySpec.getBooleanValue(moreKeys, MORE_KEYS_HAS_LABELS)) {
                moreKeysColumnAndFlags |= MORE_KEYS_FLAGS_HAS_LABELS;
            }
            if (MoreKeySpec.getBooleanValue(moreKeys, MORE_KEYS_NEEDS_DIVIDERS)) {
                moreKeysColumnAndFlags |= MORE_KEYS_FLAGS_NEEDS_DIVIDERS;
            }
            if (MoreKeySpec.getBooleanValue(moreKeys, MORE_KEYS_NO_PANEL_AUTO_MORE_KEY)) {
                moreKeysColumnAndFlags |= MORE_KEYS_FLAGS_NO_PANEL_AUTO_MORE_KEY;
            }
            return moreKeysColumnAndFlags;
        }

        public KeyParams(
                @NonNull final String keySpec,
                @NonNull final KeyboardParams params,
                final float relativeWidth,
                final int labelFlags,
                final int backgroundType,
                @Nullable final PopupSet<?> popupSet
        ) {
            this(keySpec, KeySpecParser.getCode(keySpec), params, relativeWidth, labelFlags, backgroundType, popupSet);
        }

        /**
         *  constructor that does not require attrs, style or absolute key dimension / position
         *  setDimensionsFromRelativeSize needs to be called before creating the key
         */
        public KeyParams(
                // todo (much later): replace keySpec? these encoded icons and codes are not really great
                @NonNull final String keySpec, // key text or some special string for KeySpecParser, e.g. "!icon/shift_key|!code/key_shift" (avoid using !text, should be removed)
                final int code,
                @NonNull final KeyboardParams params,
                final float relativeWidth,
                final int labelFlags,
                final int backgroundType,
                @Nullable final PopupSet<?> popupSet
        ) {
            mKeyboardParams = params;
            mBackgroundType = backgroundType;
            mLabelFlags = labelFlags;
            mRelativeWidth = relativeWidth;
            mRelativeHeight = params.mDefaultRelativeRowHeight;
            mIconId = KeySpecParser.getIconId(keySpec);

            final boolean needsToUpcase = needsToUpcase(mLabelFlags, params.mId.mElementId);
            final Locale localeForUpcasing = params.mId.getLocale();
            int actionFlags = 0;
            if (params.mId.isNumberLayout())
                actionFlags = ACTION_FLAGS_NO_KEY_PREVIEW;

            // label
            if ((mLabelFlags & LABEL_FLAGS_FROM_CUSTOM_ACTION_LABEL) != 0) {
                mLabel = params.mId.mCustomActionLabel;
            } else if (code >= Character.MIN_SUPPLEMENTARY_CODE_POINT) {
                // This is a workaround to have a key that has a supplementary code point in its label.
                // Because we can put a string in resource neither as a XML entity of a supplementary
                // code point nor as a surrogate pair.
                mLabel = new StringBuilder().appendCodePoint(code).toString();
            } else {
                final String label = KeySpecParser.getLabel(keySpec);
                mLabel = needsToUpcase
                        ? StringUtils.toTitleCaseOfKeyLabel(label, localeForUpcasing)
                        : label;
            }

            // moreKeys
            final String[] moreKeys = MoreKeysUtilsKt.createMoreKeysArray(popupSet, mKeyboardParams, mLabel != null ? mLabel : keySpec);
            mMoreKeysColumnAndFlags = getMoreKeysColumnAndFlagsAndSetNullInArray(params, moreKeys);
            final String[] finalMoreKeys = moreKeys == null ? null : MoreKeySpec.filterOutEmptyString(moreKeys);
            if (finalMoreKeys != null) {
                actionFlags |= ACTION_FLAGS_ENABLE_LONG_PRESS;
                mMoreKeys = new MoreKeySpec[finalMoreKeys.length];
                for (int i = 0; i < finalMoreKeys.length; i++) {
                    mMoreKeys[i] = new MoreKeySpec(finalMoreKeys[i], needsToUpcase, localeForUpcasing);
                }
            } else {
                mMoreKeys = null;
            }

            // hint label
            if ((mLabelFlags & LABEL_FLAGS_DISABLE_HINT_LABEL) != 0) {
                mHintLabel = null;
            } else {
                // maybe also always null for comma and period keys
                // todo: maybe remove mKeyboardParams.mHintLabelFromFirstMoreKey?
                final String hintLabel = MoreKeysUtilsKt.getHintLabel(popupSet, params, keySpec);
                mHintLabel = needsToUpcase
                        ? StringUtils.toTitleCaseOfKeyLabel(hintLabel, localeForUpcasing)
                        : hintLabel;
            }

            String outputText = KeySpecParser.getOutputText(keySpec);
            if (needsToUpcase) {
                outputText = StringUtils.toTitleCaseOfKeyLabel(outputText, localeForUpcasing);
            }
            // Choose the first letter of the label as primary code if not specified.
            if (code == CODE_UNSPECIFIED && TextUtils.isEmpty(outputText) && !TextUtils.isEmpty(mLabel)) {
                if (StringUtils.codePointCount(mLabel) == 1) {
                    // Use the first letter of the hint label if shiftedLetterActivated flag is
                    // specified.
                    if ((mLabelFlags & LABEL_FLAGS_HAS_SHIFTED_LETTER_HINT) != 0 && (mLabelFlags & LABEL_FLAGS_SHIFTED_LETTER_ACTIVATED) != 0
                            && !TextUtils.isEmpty(mHintLabel)) {
                        mCode = mHintLabel.codePointAt(0);
                    } else {
                        mCode = mLabel.codePointAt(0);
                    }
                } else {
                    // In some locale and case, the character might be represented by multiple code
                    // points, such as upper case Eszett of German alphabet.
                    outputText = mLabel;
                    mCode = CODE_OUTPUT_TEXT;
                }
            } else if (code == CODE_UNSPECIFIED && outputText != null) {
                if (StringUtils.codePointCount(outputText) == 1) {
                    mCode = outputText.codePointAt(0);
                    outputText = null;
                } else {
                    mCode = CODE_OUTPUT_TEXT;
                }
            } else {
                mCode = needsToUpcase ? StringUtils.toTitleCaseOfKeyCode(code, localeForUpcasing) : code;
            }

            // action flags don't need to be specified, they can be deduced from the key
            if (backgroundType == BACKGROUND_TYPE_SPACEBAR || mCode == Constants.CODE_LANGUAGE_SWITCH)
                actionFlags |= ACTION_FLAGS_ENABLE_LONG_PRESS;
            if (mCode <= Constants.CODE_SPACE && mCode != CODE_OUTPUT_TEXT)
                actionFlags |= ACTION_FLAGS_NO_KEY_PREVIEW;
            if (mCode == Constants.CODE_DELETE)
                actionFlags |= ACTION_FLAGS_IS_REPEATABLE;
            if (mCode == Constants.CODE_SETTINGS || mCode == Constants.CODE_LANGUAGE_SWITCH)
                actionFlags |= ACTION_FLAGS_ALT_CODE_WHILE_TYPING;
            mActionFlags = actionFlags;

            // todo: for what it is actually used? maybe it could be removed?
            final int altCodeInAttr; // settings and language switch keys have alt code space, all others nothing
            if (mCode == Constants.CODE_SETTINGS || mCode == Constants.CODE_LANGUAGE_SWITCH)
                altCodeInAttr = Constants.CODE_SPACE;
            else
                altCodeInAttr = CODE_UNSPECIFIED;
            final int altCode = needsToUpcase
                    ? StringUtils.toTitleCaseOfKeyCode(altCodeInAttr, localeForUpcasing)
                    : altCodeInAttr;
            mOptionalAttributes = OptionalAttributes.newInstance(outputText, altCode,
                    // disabled icon only ever for old version of shortcut key, visual insets can be replaced with spacer
                    KeyboardIconsSet.ICON_UNDEFINED, 0, 0);
            // KeyVisualAttributes for a key essentially are what the theme has, but on a per-key base
            // could be used e.g. for having a color gradient on key color
            mKeyVisualAttributes = null;
            mEnabled = true;
        }

        /** constructor for emoji parser */
        public KeyParams(@Nullable final String label, final int code, @Nullable final String hintLabel,
                   @Nullable final String moreKeySpecs, final int labelFlags, final KeyboardParams params) {
            mKeyboardParams = params;
            mHintLabel = hintLabel;
            mLabelFlags = labelFlags;
            mBackgroundType = BACKGROUND_TYPE_EMPTY;

            if (moreKeySpecs != null) {
                String[] moreKeys = MoreKeySpec.splitKeySpecs(moreKeySpecs);
                mMoreKeysColumnAndFlags = getMoreKeysColumnAndFlagsAndSetNullInArray(params, moreKeys);

                moreKeys = MoreKeySpec.insertAdditionalMoreKeys(moreKeys, null);
                int actionFlags = 0;
                if (moreKeys != null) {
                    actionFlags |= ACTION_FLAGS_ENABLE_LONG_PRESS;
                    mMoreKeys = new MoreKeySpec[moreKeys.length];
                    for (int i = 0; i < moreKeys.length; i++) {
                        mMoreKeys[i] = new MoreKeySpec(moreKeys[i], false, Locale.getDefault());
                    }
                } else {
                    mMoreKeys = null;
                }
                mActionFlags = actionFlags;
            } else {
                // TODO: Pass keyActionFlags as an argument.
                mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW;
                mMoreKeys = null;
                mMoreKeysColumnAndFlags = 0;
            }

            mLabel = label;
            mOptionalAttributes = code == Constants.CODE_OUTPUT_TEXT
                    ? OptionalAttributes.newInstance(label, CODE_UNSPECIFIED, ICON_UNDEFINED, 0, 0)
                    : null;
            mCode = code;
            mEnabled = (code != CODE_UNSPECIFIED);
            mIconId = KeyboardIconsSet.ICON_UNDEFINED;
            mKeyVisualAttributes = null;
        }

        /** constructor for a spacer whose size MUST be determined using setDimensionsFromRelativeSize */
        private KeyParams(final KeyboardParams params) {
            isSpacer = true; // this is only for spacer!
            mKeyboardParams = params;

            mCode = CODE_UNSPECIFIED;
            mLabel = null;
            mHintLabel = null;
            mKeyVisualAttributes = null;
            mOptionalAttributes = null;
            mIconId = KeyboardIconsSet.ICON_UNDEFINED;
            mBackgroundType = BACKGROUND_TYPE_NORMAL;
            mActionFlags = ACTION_FLAGS_NO_KEY_PREVIEW;
            mMoreKeys = null;
            mMoreKeysColumnAndFlags = 0;
            mLabelFlags = LABEL_FLAGS_FONT_NORMAL;
            mEnabled = true;
        }

        public KeyParams(final KeyParams keyParams) {
            xPos = keyParams.xPos;
            yPos = keyParams.yPos;
            mRelativeWidth = keyParams.mRelativeWidth;
            mRelativeHeight = keyParams.mRelativeHeight;
            isSpacer = keyParams.isSpacer;
            mKeyboardParams = keyParams.mKeyboardParams;
            mEnabled = keyParams.mEnabled;

            mCode = keyParams.mCode;
            mLabel = keyParams.mLabel;
            mHintLabel = keyParams.mHintLabel;
            mLabelFlags = keyParams.mLabelFlags;
            mIconId = keyParams.mIconId;
            mFullWidth = keyParams.mFullWidth;
            mFullHeight = keyParams.mFullHeight;
            mMoreKeys = keyParams.mMoreKeys;
            mMoreKeysColumnAndFlags = keyParams.mMoreKeysColumnAndFlags;
            mBackgroundType = keyParams.mBackgroundType;
            mActionFlags = keyParams.mActionFlags;
            mKeyVisualAttributes = keyParams.mKeyVisualAttributes;
            mOptionalAttributes = keyParams.mOptionalAttributes;
            mRelativeVisualInsetLeft = keyParams.mRelativeVisualInsetLeft;
            mRelativeVisualInsetRight = keyParams.mRelativeVisualInsetRight;
        }
    }
}
