package helium314.keyboard.keyboard;

import androidx.annotation.StringRes;

import helium314.keyboard.latin.R;
import helium314.keyboard.latin.WordComposer;

public enum KeyboardElement {
    ALPHABET(R.string.spoken_description_mode_alpha),
    ALPHABET_AUTOMATIC_SHIFTED(R.string.spoken_description_mode_alpha),
    ALPHABET_MANUAL_SHIFTED(R.string.spoken_description_shiftmode_on),
    ALPHABET_SHIFT_LOCKED(R.string.spoken_description_shiftmode_locked),
    ALPHABET_SHIFT_LOCK_SHIFTED(R.string.spoken_description_shiftmode_locked), // todo: what?
    SYMBOLS(R.string.spoken_description_mode_symbol),
    SYMBOLS_SHIFTED(R.string.spoken_description_mode_symbol_shift),
    DPAD(R.string.spoken_description_mode_dpad),
    NUMPAD(R.string.spoken_description_mode_numpad),
    NUMBER(R.string.spoken_description_mode_number),
    PHONE(R.string.spoken_description_mode_phone),
    PHONE_SYMBOLS(R.string.spoken_description_mode_phone_shift),
    EMOJI_RECENTS(R.string.spoken_description_emoji_category_recents),
    EMOJI_CATEGORY1(R.string.spoken_description_emoji_category_eight_smiley),
    EMOJI_CATEGORY2(R.string.spoken_description_emoji_category_eight_smiley_people),
    EMOJI_CATEGORY3(R.string.spoken_description_emoji_category_eight_animals_nature),
    EMOJI_CATEGORY4(R.string.spoken_description_emoji_category_eight_food_drink),
    EMOJI_CATEGORY5(R.string.spoken_description_emoji_category_eight_travel_places),
    EMOJI_CATEGORY6(R.string.spoken_description_emoji_category_eight_activity),
    EMOJI_CATEGORY7(R.string.spoken_description_emoji_category_objects),
    EMOJI_CATEGORY8(R.string.spoken_description_emoji_category_symbols),
    EMOJI_CATEGORY9(R.string.spoken_description_emoji_category_flags),
    EMOJI_CATEGORY10(R.string.spoken_description_emoji_category_emoticons),
    EMOJI_BOTTOM_ROW(0),
    CLIPBOARD(R.string.spoken_description_mode_clipboard),
    CLIPBOARD_BOTTOM_ROW(0);

    private static final int EMOJI_LAYOUT_COUNT = 11;

    @StringRes
    public final int contentDescription;

    KeyboardElement(@StringRes int contentDescription) {
        this.contentDescription = contentDescription;
    }

    public final boolean isAlphabetLayout() {
        return compareTo(SYMBOLS) < 0;
    }

    public final boolean isAlphabetShifted() {
        return isAlphabetLayout() && this != ALPHABET;
    }

    public final boolean isAlphabetShiftedManually() {
        return isAlphabetLayout() && compareTo(ALPHABET_AUTOMATIC_SHIFTED) > 0;
    }

    public final boolean isAlphaOrSymbolLayout() {
        return compareTo(SYMBOLS_SHIFTED) <= 0;
    }

    public final boolean isNumberLayout() {
        return compareTo(NUMPAD) >= 0 && compareTo(PHONE_SYMBOLS) <= 0;
    }

    public final boolean isEmojiLayout() {
        return compareTo(EMOJI_RECENTS) >= 0 && compareTo(EMOJI_CATEGORY10) <= 0;
    }

    public final boolean isBottomRow() {
        return this == EMOJI_BOTTOM_ROW || this == CLIPBOARD_BOTTOM_ROW;
    }

    public final int capsMode() {
        return switch (this) {
            case ALPHABET_AUTOMATIC_SHIFTED -> WordComposer.CAPS_MODE_AUTO_SHIFTED;
            case ALPHABET_MANUAL_SHIFTED -> WordComposer.CAPS_MODE_MANUAL_SHIFTED;
            case ALPHABET_SHIFT_LOCKED,
                 ALPHABET_SHIFT_LOCK_SHIFTED -> WordComposer.CAPS_MODE_MANUAL_SHIFT_LOCKED;
            default -> WordComposer.CAPS_MODE_OFF;
        };
    }

}
