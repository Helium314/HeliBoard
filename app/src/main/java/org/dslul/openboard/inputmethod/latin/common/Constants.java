/*
 * Copyright (C) 2012 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.common;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;

import javax.annotation.Nonnull;

public final class Constants {

    public static final class Color {
        /**
         * The alpha value for fully opaque.
         */
        public final static int ALPHA_OPAQUE = 255;
    }

    public static final class ImeOption {
        /**
         * The private IME option used to indicate that no microphone should be shown for a given
         * text field. For instance, this is specified by the search dialog when the dialog is
         * already showing a voice search button.
         *
         * @deprecated Use {@link ImeOption#NO_MICROPHONE} with package name prefixed.
         */
        @SuppressWarnings("dep-ann")
        public static final String NO_MICROPHONE_COMPAT = "nm";

        /**
         * The private IME option used to indicate that no microphone should be shown for a given
         * text field. For instance, this is specified by the search dialog when the dialog is
         * already showing a voice search button.
         */
        public static final String NO_MICROPHONE = "noMicrophoneKey";

        /**
         * The private IME option used to indicate that no settings key should be shown for a given
         * text field.
         */
        public static final String NO_SETTINGS_KEY = "noSettingsKey";

        /**
         * The private IME option used to indicate that the given text field needs ASCII code points
         * input.
         *
         * @deprecated Use EditorInfo#IME_FLAG_FORCE_ASCII.
         */
        @SuppressWarnings("dep-ann")
        public static final String FORCE_ASCII = "forceAscii";

        /**
         * The private IME option used to suppress the floating gesture preview for a given text
         * field. This overrides the corresponding keyboard settings preference.
         * {@link org.dslul.openboard.inputmethod.latin.settings.SettingsValues#mGestureFloatingPreviewTextEnabled}
         */
        public static final String NO_FLOATING_GESTURE_PREVIEW = "noGestureFloatingPreview";

        private ImeOption() {
            // This utility class is not publicly instantiable.
        }
    }

    public static final class Subtype {
        /**
         * The subtype mode used to indicate that the subtype is a keyboard.
         */
        public static final String KEYBOARD_MODE = "keyboard";

        public static final class ExtraValue {
            /**
             * The subtype extra value used to indicate that this subtype is capable of
             * entering ASCII characters.
             */
            public static final String ASCII_CAPABLE = "AsciiCapable";

            /**
             * The subtype extra value used to indicate that this subtype is enabled
             * when the default subtype is not marked as ascii capable.
             */
            public static final String ENABLED_WHEN_DEFAULT_IS_NOT_ASCII_CAPABLE =
                    "EnabledWhenDefaultIsNotAsciiCapable";

            /**
             * The subtype extra value used to indicate that this subtype is capable of
             * entering emoji characters.
             */
            public static final String EMOJI_CAPABLE = "EmojiCapable";

            /**
             * The subtype extra value used to indicate that this subtype requires a network
             * connection to work.
             */
            public static final String REQ_NETWORK_CONNECTIVITY = "requireNetworkConnectivity";

            /**
             * The subtype extra value used to indicate that the display name of this subtype
             * contains a "%s" for printf-like replacement and it should be replaced by
             * this extra value.
             * This extra value is supported on JellyBean and later.
             */
            public static final String UNTRANSLATABLE_STRING_IN_SUBTYPE_NAME =
                    "UntranslatableReplacementStringInSubtypeName";

            /**
             * The subtype extra value used to indicate this subtype keyboard layout set name.
             * This extra value is private to LatinIME.
             */
            public static final String KEYBOARD_LAYOUT_SET = "KeyboardLayoutSet";

            /**
             * The subtype extra value used to indicate that this subtype is an additional subtype
             * that the user defined. This extra value is private to LatinIME.
             */
            public static final String IS_ADDITIONAL_SUBTYPE = "isAdditionalSubtype";

            /**
             * The subtype extra value used to specify the combining rules.
             */
            public static final String COMBINING_RULES = "CombiningRules";

            private ExtraValue() {
                // This utility class is not publicly instantiable.
            }
        }

        private Subtype() {
            // This utility class is not publicly instantiable.
        }
    }

    public static final class TextUtils {
        /**
         * Capitalization mode for {@link android.text.TextUtils#getCapsMode}: don't capitalize
         * characters.  This value may be used with
         * {@link android.text.TextUtils#CAP_MODE_CHARACTERS},
         * {@link android.text.TextUtils#CAP_MODE_WORDS}, and
         * {@link android.text.TextUtils#CAP_MODE_SENTENCES}.
         */
        // TODO: Straighten this out. It's bizarre to have to use android.text.TextUtils.CAP_MODE_*
        // except for OFF that is in Constants.TextUtils.
        public static final int CAP_MODE_OFF = 0;

        private TextUtils() {
            // This utility class is not publicly instantiable.
        }
    }

    public static final int NOT_A_CODE = -1;
    public static final int NOT_A_CURSOR_POSITION = -1;
    // TODO: replace the following constants with state in InputTransaction?
    public static final int NOT_A_COORDINATE = -1;
    public static final int SUGGESTION_STRIP_COORDINATE = -2;
    public static final int EXTERNAL_KEYBOARD_COORDINATE = -4;

    // A hint on how many characters to cache from the TextView. A good value of this is given by
    // how many characters we need to be able to almost always find the caps mode.
    public static final int EDITOR_CONTENTS_CACHE_SIZE = 1024;
    // How many characters we accept for the recapitalization functionality. This needs to be
    // large enough for all reasonable purposes, but avoid purposeful attacks. 100k sounds about
    // right for this.
    public static final int MAX_CHARACTERS_FOR_RECAPITALIZATION = 1024 * 100;

    // Key events coming any faster than this are long-presses.
    public static final int LONG_PRESS_MILLISECONDS = 200;
    // TODO: Set this value appropriately.
    public static final int GET_SUGGESTED_WORDS_TIMEOUT = 200;
    // How many continuous deletes at which to start deleting at a higher speed.
    public static final int DELETE_ACCELERATE_AT = 20;

    public static final String WORD_SEPARATOR = " ";

    public static boolean isValidCoordinate(final int coordinate) {
        // Detect {@link NOT_A_COORDINATE}, {@link SUGGESTION_STRIP_COORDINATE},
        // and {@link SPELL_CHECKER_COORDINATE}.
        return coordinate >= 0;
    }

    /**
     * Custom request code used in
     * {@link org.dslul.openboard.inputmethod.keyboard.KeyboardActionListener#onCustomRequest(int)}.
     */
    // The code to show input method picker.
    public static final int CUSTOM_CODE_SHOW_INPUT_METHOD_PICKER = 1;

    /**
     * Some common keys code. Must be positive.
     */
    public static final int CODE_ENTER = '\n';
    public static final int CODE_TAB = '\t';
    public static final int CODE_SPACE = ' ';
    public static final int CODE_PERIOD = '.';
    public static final int CODE_COMMA = ',';
    public static final int CODE_DASH = '-';
    public static final int CODE_SINGLE_QUOTE = '\'';
    public static final int CODE_DOUBLE_QUOTE = '"';
    public static final int CODE_SLASH = '/';
    public static final int CODE_BACKSLASH = '\\';
    public static final int CODE_VERTICAL_BAR = '|';
    public static final int CODE_COMMERCIAL_AT = '@';
    public static final int CODE_PLUS = '+';
    public static final int CODE_PERCENT = '%';
    public static final int CODE_CLOSING_PARENTHESIS = ')';
    public static final int CODE_CLOSING_SQUARE_BRACKET = ']';
    public static final int CODE_CLOSING_CURLY_BRACKET = '}';
    public static final int CODE_CLOSING_ANGLE_BRACKET = '>';
    public static final int CODE_INVERTED_QUESTION_MARK = 0xBF; // ¿
    public static final int CODE_INVERTED_EXCLAMATION_MARK = 0xA1; // ¡
    public static final int CODE_GRAVE_ACCENT = '`';
    public static final int CODE_CIRCUMFLEX_ACCENT = '^';
    public static final int CODE_TILDE = '~';

    public static final String REGEXP_PERIOD = "\\.";
    public static final String STRING_SPACE = " ";

    /**
     * Special keys code. Must be negative.
     * These should be aligned with constants in
     * {@link org.dslul.openboard.inputmethod.keyboard.internal.KeyboardCodesSet}.
     */
    public static final int CODE_SHIFT = -1;
    public static final int CODE_CAPSLOCK = -2;
    public static final int CODE_SWITCH_ALPHA_SYMBOL = -3;
    public static final int CODE_OUTPUT_TEXT = -4;
    public static final int CODE_DELETE = -5;
    public static final int CODE_SETTINGS = -6;
    public static final int CODE_SHORTCUT = -7;
    public static final int CODE_ACTION_NEXT = -8;
    public static final int CODE_ACTION_PREVIOUS = -9;
    public static final int CODE_LANGUAGE_SWITCH = -10;
    public static final int CODE_EMOJI = -11;
    public static final int CODE_CLIPBOARD = -12;
    public static final int CODE_SHIFT_ENTER = -13;
    public static final int CODE_SYMBOL_SHIFT = -14;
    public static final int CODE_ALPHA_FROM_EMOJI = -15;
    public static final int CODE_ALPHA_FROM_CLIPBOARD = -16;
    public static final int CODE_START_ONE_HANDED_MODE = -17;
    public static final int CODE_STOP_ONE_HANDED_MODE = -18;
    public static final int CODE_SWITCH_ONE_HANDED_MODE = -19;
    // Code value representing the code is not specified.
    public static final int CODE_UNSPECIFIED = -20;

    public static boolean isLetterCode(final int code) {
        return code >= CODE_SPACE;
    }

    @Nonnull
    public static String printableCode(final int code) {
        switch (code) {
        case CODE_SHIFT: return "shift";
        case CODE_CAPSLOCK: return "capslock";
        case CODE_SWITCH_ALPHA_SYMBOL: return "symbol";
        case CODE_OUTPUT_TEXT: return "text";
        case CODE_DELETE: return "delete";
        case CODE_SETTINGS: return "settings";
        case CODE_SHORTCUT: return "shortcut";
        case CODE_ACTION_NEXT: return "actionNext";
        case CODE_ACTION_PREVIOUS: return "actionPrevious";
        case CODE_LANGUAGE_SWITCH: return "languageSwitch";
        case CODE_EMOJI: return "emoji";
        case CODE_CLIPBOARD: return "clipboard";
        case CODE_SHIFT_ENTER: return "shiftEnter";
        case CODE_ALPHA_FROM_EMOJI: return "alpha";
        case CODE_ALPHA_FROM_CLIPBOARD: return "alpha";
        case CODE_UNSPECIFIED: return "unspec";
        case CODE_TAB: return "tab";
        case CODE_ENTER: return "enter";
        case CODE_SPACE: return "space";
        case CODE_START_ONE_HANDED_MODE: return "startOneHandedMode";
        case CODE_STOP_ONE_HANDED_MODE: return "stopOneHandedMode";
        case CODE_SWITCH_ONE_HANDED_MODE: return "switchOneHandedMode";
        default:
            if (code < CODE_SPACE) return String.format("\\u%02X", code);
            if (code < 0x100) return String.format("%c", code);
            if (code < 0x10000) return String.format("\\u%04X", code);
            return String.format("\\U%05X", code);
        }
    }

    @Nonnull
    public static String printableCodes(@Nonnull final int[] codes) {
        final StringBuilder sb = new StringBuilder();
        boolean addDelimiter = false;
        for (final int code : codes) {
            if (code == NOT_A_CODE) break;
            if (addDelimiter) sb.append(", ");
            sb.append(printableCode(code));
            addDelimiter = true;
        }
        return "[" + sb + "]";
    }

    /**
     * Screen metrics (a.k.a. Device form factor) constants of
     * {@link org.dslul.openboard.inputmethod.latin.R.integer#config_screen_metrics}.
     */
    public static final int SCREEN_METRICS_SMALL_PHONE = 0;
    public static final int SCREEN_METRICS_LARGE_PHONE = 1;
    public static final int SCREEN_METRICS_LARGE_TABLET = 2;
    public static final int SCREEN_METRICS_SMALL_TABLET = 3;

    @UsedForTesting
    public static boolean isPhone(final int screenMetrics) {
        return screenMetrics == SCREEN_METRICS_SMALL_PHONE
                || screenMetrics == SCREEN_METRICS_LARGE_PHONE;
    }

    @UsedForTesting
    public static boolean isTablet(final int screenMetrics) {
        return screenMetrics == SCREEN_METRICS_SMALL_TABLET
                || screenMetrics == SCREEN_METRICS_LARGE_TABLET;
    }

    /**
     * Default capacity of gesture points container.
     * This constant is used by {@link org.dslul.openboard.inputmethod.keyboard.internal.BatchInputArbiter}
     * and etc. to preallocate regions that contain gesture event points.
     */
    public static final int DEFAULT_GESTURE_POINTS_CAPACITY = 128;

    public static final int MAX_IME_DECODER_RESULTS = 20;
    public static final int DECODER_SCORE_SCALAR = 1000000;
    public static final int DECODER_MAX_SCORE = 1000000000;

    public static final int EVENT_BACKSPACE = 1;
    public static final int EVENT_REJECTION = 2;
    public static final int EVENT_REVERT = 3;

    private Constants() {
        // This utility class is not publicly instantiable.
    }
}
