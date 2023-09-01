package org.dslul.openboard.inputmethod.latin.common;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;
import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;
import androidx.core.graphics.drawable.DrawableCompat;

import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme;

// todo: maybe kotlin? would make it much shorter and more readable
public class Colors {

    public final int navBar;
    public final int accent;
    public final int background;
    public final int keyBackground;
    public final int functionalKey;
    public final int spaceBar;
    public final int keyText;
    public final int keyHintText;
    public int adjustedBackground;
    public int adjustedKeyText;
    // todo (later): evaluate which colors, colorFilters and colorStateLists area actually necessary
    public ColorFilter backgroundFilter;
    public ColorFilter adjustedBackgroundFilter;
    public ColorFilter keyBackgroundFilter;
    public ColorFilter functionalKeyBackgroundFilter;
    public ColorFilter spaceBarFilter;
    public ColorFilter keyTextFilter;
    public ColorFilter accentColorFilter;
    public ColorFilter actionKeyIconColorFilter;

    private ColorStateList backgroundStateList;
    private ColorStateList keyStateList;
    private ColorStateList functionalKeyStateList;
    private ColorStateList actionKeyStateList;
    private ColorStateList spaceBarStateList;
    private ColorStateList adjustedBackgroundStateList;

    public static Colors newColors(String themeStyle, int accent, int background, int keyBackground, int functionalKey, int spaceBar, int keyText, int keyHintText) {
        if (themeStyle.equals(KeyboardTheme.THEME_STYLE_HOLO))
            return new HoloColors(accent, background, keyBackground, functionalKey, spaceBar, keyText, keyHintText);
        return new Colors(accent, background, keyBackground, functionalKey, spaceBar, keyText, keyHintText);
    }

    protected Colors(int _accent, int _background, int _keyBackground, int _functionalKey, int _spaceBar, int _keyText, int _keyHintText) {
        accent = _accent;
        background = _background;
        keyBackground = _keyBackground;
        functionalKey = _functionalKey;
        spaceBar = _spaceBar;
        keyText = _keyText;
        keyHintText = _keyHintText;
        navBar = background;
    }

    /** set background colors including state list to the drawable */
    // todo: this can be used for setting more complicated filters
    //  may be necessary for reproducing holo theme (extend Colors and override this in sth like HoloColors?)
    public void setBackgroundColor(final Drawable background, final int type) {
        final ColorStateList list;
        switch (type) {
            case TYPE_KEY:
                list = keyStateList;
                break;
            case TYPE_SPACE:
                list = spaceBarStateList;
                break;
            case TYPE_ADJUSTED_BACKGROUND:
                list = adjustedBackgroundStateList;
                break;
            case TYPE_ACTION:
                list = actionKeyStateList;
                break;
            case TYPE_FUNCTIONAL:
                list = functionalKeyStateList;
                break;
            case TYPE_BACKGROUND:
            default:
                list = backgroundStateList;
        }
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY);
        DrawableCompat.setTintList(background, list);
    }

    @Nullable
    public Drawable getKeyboardBackground() {
        return null;
    }

    public static final int TYPE_BACKGROUND = 0;
    public static final int TYPE_KEY = 1;
    public static final int TYPE_FUNCTIONAL = 2;
    public static final int TYPE_ACTION = 3;
    public static final int TYPE_SPACE = 4;
    public static final int TYPE_ADJUSTED_BACKGROUND = 5;

    public void createColorFilters(final boolean hasKeyBorders) {
        final int[][] states = new int[][] {
                // are other states used?
                //  looks like only microphone ("shortcut") key can ever be disabled, but then it's not shown anyway...
                //  and checked seems unused
                new int[] { android.R.attr.state_pressed}, // pressed
                new int[] { -android.R.attr.state_pressed}, // not pressed
        };

        backgroundFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(background, BlendModeCompat.MODULATE);
        adjustedKeyText = brightenOrDarken(keyText, true);

        // color to be used if exact background color would be bad contrast, e.g. more keys popup or no border space bar
        if (isDarkColor(background)) {
            adjustedBackground = brighten(background);
            adjustedBackgroundStateList = new ColorStateList(states, new int[] { brighten(adjustedBackground), adjustedBackground });
        } else {
            adjustedBackground = darken(background);
            adjustedBackgroundStateList = new ColorStateList(states, new int[] { darken(adjustedBackground), adjustedBackground });
        }
        adjustedBackgroundFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(adjustedBackground, BlendModeCompat.MODULATE);

        if (hasKeyBorders) {
            keyBackgroundFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(keyBackground, BlendModeCompat.MODULATE);
            functionalKeyBackgroundFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(functionalKey, BlendModeCompat.MODULATE);
            spaceBarFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(spaceBar, BlendModeCompat.MODULATE);

            backgroundStateList = new ColorStateList(states, new int[] { brightenOrDarken(background, true), background });
            keyStateList = new ColorStateList(states, new int[] { brightenOrDarken(keyBackground, true), keyBackground });
            functionalKeyStateList = new ColorStateList(states, new int[] { brightenOrDarken(functionalKey, true), functionalKey });
            actionKeyStateList = new ColorStateList(states, new int[] { brightenOrDarken(accent, true), accent });
            spaceBarStateList = new ColorStateList(states, new int[] { brightenOrDarken(spaceBar, true), spaceBar });
        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            keyBackgroundFilter = backgroundFilter;
            functionalKeyBackgroundFilter = keyBackgroundFilter;
            spaceBarFilter = keyBackgroundFilter;

            backgroundStateList = new ColorStateList(states, new int[] { brightenOrDarken(background, true), background });
            keyStateList = backgroundStateList;
            functionalKeyStateList = backgroundStateList;
            actionKeyStateList = new ColorStateList(states, new int[] { brightenOrDarken(accent, true), accent });
            spaceBarStateList = adjustedBackgroundStateList;
        }
        keyTextFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(keyText, BlendModeCompat.SRC_ATOP);
        accentColorFilter = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(accent, BlendModeCompat.MODULATE);
        actionKeyIconColorFilter = isBrightColor(accent) // the white icon may not have enough contrast, and can't be adjusted by the user
                ? BlendModeColorFilterCompat.createBlendModeColorFilterCompat(Color.DKGRAY, BlendModeCompat.SRC_ATOP)
                : null;
    }

    // todo: move static functions to some utility class?
    public static boolean isBrightColor(final int color) {
        if (android.R.color.transparent == color) {
            return true;
        }
        return getBrightnessSquared(color) >= 210*210;
    }

    private static boolean isDarkColor(final int color) {
        if (android.R.color.transparent == color) {
            return true;
        }
        return getBrightnessSquared(color) < 50*50;
    }

    public static int brightenOrDarken(final int color, final boolean preferDarken) {
        if (preferDarken) {
            if (isDarkColor(color)) return brighten(color);
            else return darken(color);
        } else if (isBrightColor(color)) return darken(color);
        else return brighten(color);
    }

    private static int getBrightnessSquared(final int color) {
        // See http://www.nbdtech.com/Blog/archive/2008/04/27/Calculating-the-Perceived-Brightness-of-a-Color.aspx
        int[] rgb = {Color.red(color), Color.green(color), Color.blue(color)};
        // we are only interested whether brightness is greater, so no need for sqrt
        return (int) (rgb[0] * rgb[0] * .241 + rgb[1] * rgb[1] * .691 + rgb[2] * rgb[2] * .068);
    }

    protected static int adjustLuminosityAndKeepAlpha(@ColorInt final int color, final float amount) {
        final int alpha = Color.alpha(color);
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] += amount;
        final int newColor = ColorUtils.HSLToColor(hsl);
        return Color.argb(alpha, Color.red(newColor), Color.green(newColor), Color.blue(newColor));
    }

    @ColorInt
    public static int brighten(@ColorInt final int color) {
        return adjustLuminosityAndKeepAlpha(color, 0.05f);
    }

    @ColorInt
    public static int darken(@ColorInt final int color) {
        return adjustLuminosityAndKeepAlpha(color, -0.05f);
    }

}
