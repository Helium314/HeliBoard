package org.dslul.openboard.inputmethod.latin.common;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

public class HoloColors extends Colors {
    private final Drawable keyboardBackground = new GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            new int[] { background, adjustLuminosityAndKeepAlpha(background, -0.2f) }
    );

    protected HoloColors(int _accent, int _background, int _keyBackground, int _functionalKey, int _spaceBar, int _keyText, int _keyHintText) {
        super(_accent, _background, _keyBackground, _functionalKey, _spaceBar, _keyText, _keyHintText);
    }

    @Override
    public Drawable getKeyboardBackground() {
        // thanks a lot google for omitting something extremely exotic like a "subtract" color
        // filter that could be simply applied on top of a brighter version of keyboard_background_holo
        return keyboardBackground;
    }

}
