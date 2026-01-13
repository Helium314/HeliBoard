package helium314.keyboard.keyboard;

import androidx.annotation.StringRes;

import helium314.keyboard.latin.R;

public enum KeyboardMode {
    TEXT(R.string.keyboard_mode_text),
    URL(R.string.keyboard_mode_url),
    EMAIL(R.string.keyboard_mode_email),
    IM(R.string.keyboard_mode_im),
    PHONE(R.string.keyboard_mode_phone),
    NUMBER(R.string.keyboard_mode_number),
    DATE(R.string.keyboard_mode_date),
    TIME(R.string.keyboard_mode_time),
    DATETIME(R.string.keyboard_mode_date_time);

    @StringRes
    public final int contentDescription;

    KeyboardMode(@StringRes int contentDescription) {
        this.contentDescription = contentDescription;
    }

    public final boolean shouldSuppressEmojis() {
        return compareTo(PHONE) >= 0 || this == EMAIL;
    }

    public final boolean isWebMode() {
        return this == URL || this == EMAIL;
    }
}
