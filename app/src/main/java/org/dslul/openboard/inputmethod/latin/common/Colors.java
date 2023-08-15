package org.dslul.openboard.inputmethod.latin.common;

import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.ColorFilter;

import androidx.core.graphics.BlendModeColorFilterCompat;
import androidx.core.graphics.BlendModeCompat;
import androidx.annotation.ColorInt;
import androidx.core.graphics.ColorUtils;

import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme;

public class Colors {

    public final boolean isCustom;
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

    public ColorStateList backgroundStateList;
    public ColorStateList keyStateList;
    public ColorStateList functionalKeyStateList;
    public ColorStateList actionKeyStateList;
    public ColorStateList spaceBarStateList;
    public ColorStateList adjustedBackgroundStateList;

    public Colors(int _accent, int _background, int _keyBackground, int _functionalKey, int _spaceBar, int _keyText, int _keyHintText) {
        isCustom = true;
        accent = _accent;
        background = _background;
        keyBackground = _keyBackground;
        functionalKey = _functionalKey;
        spaceBar = _spaceBar;
        keyText = _keyText;
        keyHintText = _keyHintText;
        navBar = background;
    }

    // todo (later): remove this and isCustom, once the old themes can be completely replaced
    public Colors(int themeId, final boolean isNight) {
        isCustom = false;
        if (KeyboardTheme.getIsDayNight(themeId)) {
            if (!isNight)
                navBar = Color.rgb(236, 239, 241);
            else if (themeId == KeyboardTheme.THEME_ID_LXX_DARK)
                navBar = Color.rgb(38, 50, 56);
            else
                navBar = Color.BLACK;
        } else if (KeyboardTheme.THEME_VARIANT_LIGHT.equals(KeyboardTheme.getThemeVariant(themeId))) {
            navBar = Color.rgb(236, 239, 241);
        } else if (themeId == KeyboardTheme.THEME_ID_LXX_DARK) {
            navBar = Color.rgb(38, 50, 56);
        } else {
            // dark border is 13/13/13, but that's ok
            navBar = Color.BLACK;
        }
        accent = 0;
        background = 0;
        keyBackground = 0;
        functionalKey = 0;
        spaceBar = 0;
        keyText = 0;
        keyHintText = 0;
    }

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

    @ColorInt
    public static int brighten(@ColorInt int color) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] += 0.05f;
        return ColorUtils.HSLToColor(hsl);
    }

    @ColorInt
    public static int darken(@ColorInt int color) {
        float[] hsl = new float[3];
        ColorUtils.colorToHSL(color, hsl);
        hsl[2] -= 0.05f;
        return ColorUtils.HSLToColor(hsl);
    }

}
