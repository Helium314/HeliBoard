// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin.utils

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.core.graphics.ColorUtils

fun isBrightColor(@ColorInt color: Int) =
    if (android.R.color.transparent == color) true
    else getBrightnessSquared(color) >= 210 * 210

fun isDarkColor(@ColorInt color: Int) =
    if (android.R.color.transparent == color) true
    else getBrightnessSquared(color) < 50 * 50

fun isGoodContrast(@ColorInt color1: Int, @ColorInt color2: Int) =
    colorDistanceSquared(color1, color2) > 80 * 80

private fun colorDistanceSquared(@ColorInt color1: Int, @ColorInt color2: Int): Int {
    val diffR = Color.red(color1) - Color.red(color2)
    val diffG = Color.green(color1) - Color.green(color2)
    val diffB = Color.blue(color1) - Color.blue(color2)
    return diffR * diffR + diffB * diffB + diffG * diffG
}

@ColorInt
fun brightenOrDarken(@ColorInt color: Int, preferDarken: Boolean) =
    if (preferDarken) {
        if (isDarkColor(color)) brighten(color) else darken(color)
    } else if (isBrightColor(color)) darken(color) else brighten(color)

private fun getBrightnessSquared(@ColorInt color: Int): Int {
    // See http://www.nbdtech.com/Blog/archive/2008/04/27/Calculating-the-Perceived-Brightness-of-a-Color.aspx
    val rgb = intArrayOf(Color.red(color), Color.green(color), Color.blue(color))
    // we are only interested whether brightness is greater, so no need for sqrt
    return (rgb[0] * rgb[0] * .241 + rgb[1] * rgb[1] * .691 + rgb[2] * rgb[2] * .068).toInt()
}

@ColorInt
fun adjustLuminosityAndKeepAlpha(@ColorInt color: Int, amount: Float): Int {
    val alpha = Color.alpha(color)
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(color, hsl)
    hsl[2] += amount
    val newColor = ColorUtils.HSLToColor(hsl)
    return Color.argb(alpha, Color.red(newColor), Color.green(newColor), Color.blue(newColor))
}

@ColorInt
fun brighten(@ColorInt color: Int) =
    if (Color.red(color) < 20 && Color.green(color) < 15 && Color.blue(color) < 25)
        adjustLuminosityAndKeepAlpha(color, 0.09f) // really dark colors need more brightening
    else
        adjustLuminosityAndKeepAlpha(color, 0.06f)

@ColorInt
fun darken(@ColorInt color: Int) = adjustLuminosityAndKeepAlpha(color, -0.06f)