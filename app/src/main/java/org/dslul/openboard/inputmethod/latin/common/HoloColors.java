package org.dslul.openboard.inputmethod.latin.common;

import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

public class HoloColors extends Colors {

    protected HoloColors(int _accent, int _background, int _keyBackground, int _functionalKey, int _spaceBar, int _keyText, int _keyHintText) {
        super(_accent, _background, _keyBackground, _functionalKey, _spaceBar, _keyText, _keyHintText);
    }

    @Override
    public Drawable getKeyboardBackground() {
        // thanks a lot google for omitting something extremely exotic like a "subtract" color
        // filter that could be simply applied on top of a brighter version of keyboard_background_holo
        final int bottomColor = adjustLuminosityAndKeepAlpha(background, -0.2f); // does it need adjusting?
        return new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] { background, bottomColor });
    }

}
