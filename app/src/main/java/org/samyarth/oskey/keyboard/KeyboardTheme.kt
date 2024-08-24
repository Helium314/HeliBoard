/*
 * Copyright (C) 2014 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */
package org.samyarth.oskey.keyboard

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.os.Build
import android.os.Build.VERSION_CODES
import androidx.core.content.ContextCompat
import org.samyarth.oskey.R
import org.samyarth.oskey.latin.common.AllColors
import org.samyarth.oskey.latin.common.Colors
import org.samyarth.oskey.latin.common.DefaultColors
import org.samyarth.oskey.latin.common.DynamicColors
import org.samyarth.oskey.latin.common.readAllColorsMap
import org.samyarth.oskey.latin.settings.Settings
import org.samyarth.oskey.latin.utils.DeviceProtectedUtils


class KeyboardTheme // Note: The themeId should be aligned with "themeId" attribute of Keyboard style in values/themes-<style>.xml.
private constructor(val themeId: Int, @JvmField val mStyleId: Int) {
    override fun equals(other: Any?) = if (other === this) true
        else (other as? KeyboardTheme)?.themeId == themeId

    override fun hashCode(): Int {
        return themeId
    }

    companion object {
        // old themes
        const val STYLE_MATERIAL = "Material"
        const val STYLE_HOLO = "Holo"
        const val STYLE_ROUNDED = "Rounded"

        // new themes using the custom colors
        const val THEME_LIGHT = "light"
        const val THEME_HOLO_WHITE = "holo_white"
        const val THEME_DARK = "dark"
        const val THEME_DARKER = "darker"
        const val THEME_BLACK = "black"
        const val THEME_DYNAMIC = "dynamic"
        const val THEME_USER = "user"
        const val THEME_USER_NIGHT = "user_night"
        const val THEME_BLUE_GRAY = "blue_gray"
        const val THEME_BROWN = "brown"
        const val THEME_CHOCOLATE = "chocolate"
        const val THEME_CLOUDY = "cloudy"
        const val THEME_FOREST = "forest"
        const val THEME_INDIGO = "indigo"
        const val THEME_OCEAN = "ocean"
        const val THEME_PINK = "pink"
        const val THEME_SAND = "sand"
        const val THEME_VIOLETTE = "violette"
        val COLORS = listOfNotNull(
            THEME_LIGHT, if (Build.VERSION.SDK_INT < VERSION_CODES.S) null else THEME_DYNAMIC, THEME_HOLO_WHITE, THEME_DARK,
            THEME_DARKER, THEME_BLACK, THEME_BLUE_GRAY, THEME_BROWN, THEME_CHOCOLATE, THEME_CLOUDY, THEME_FOREST,
            THEME_INDIGO, THEME_PINK, THEME_OCEAN, THEME_SAND, THEME_VIOLETTE, THEME_USER
        )
        val COLORS_DARK = listOfNotNull(
            THEME_HOLO_WHITE, THEME_DARK, if (Build.VERSION.SDK_INT < VERSION_CODES.S) null else THEME_DYNAMIC,
            THEME_DARKER, THEME_BLACK, THEME_CHOCOLATE, THEME_CLOUDY, THEME_FOREST, THEME_OCEAN, THEME_VIOLETTE, THEME_USER_NIGHT
        )
        val STYLES = arrayOf(STYLE_MATERIAL, STYLE_HOLO, STYLE_ROUNDED)

        // These should be aligned with Keyboard.themeId and Keyboard.Case.keyboardTheme
        // attributes' values in attrs.xml.
        private const val THEME_ID_HOLO_BASE = 0
        private const val THEME_ID_LXX_BASE = 1
        private const val THEME_ID_LXX_BASE_BORDER = 2
        private const val THEME_ID_ROUNDED_BASE = 3
        private const val THEME_ID_ROUNDED_BASE_BORDER = 4
        private const val DEFAULT_THEME_ID = THEME_ID_LXX_BASE

        private val KEYBOARD_THEMES = arrayOf(
            KeyboardTheme(THEME_ID_HOLO_BASE, R.style.KeyboardTheme_HoloBase),
            KeyboardTheme(THEME_ID_LXX_BASE, R.style.KeyboardTheme_LXX_Base),
            KeyboardTheme(THEME_ID_LXX_BASE_BORDER, R.style.KeyboardTheme_LXX_Base_Border),
            KeyboardTheme(THEME_ID_ROUNDED_BASE, R.style.KeyboardTheme_Rounded_Base),
            KeyboardTheme(THEME_ID_ROUNDED_BASE_BORDER, R.style.KeyboardTheme_Rounded_Base_Border)
        )

        @JvmStatic
        fun getKeyboardTheme(context: Context): KeyboardTheme {
            val prefs = DeviceProtectedUtils.getSharedPreferences(context)
            val style = prefs.getString(Settings.PREF_THEME_STYLE, STYLE_MATERIAL)
            val borders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false)
            val matchingId = when (style) {
                STYLE_HOLO -> THEME_ID_HOLO_BASE
                STYLE_ROUNDED -> if (borders) THEME_ID_ROUNDED_BASE_BORDER else THEME_ID_ROUNDED_BASE
                else -> if (borders) THEME_ID_LXX_BASE_BORDER else THEME_ID_LXX_BASE
            }
            return KEYBOARD_THEMES.firstOrNull { it.themeId == matchingId } ?: KEYBOARD_THEMES[DEFAULT_THEME_ID]
        }

        fun getThemeActionAndEmojiKeyLabelFlags(themeId: Int): Int {
            return if (themeId == THEME_ID_LXX_BASE || themeId == THEME_ID_ROUNDED_BASE) Key.LABEL_FLAGS_KEEP_BACKGROUND_ASPECT_RATIO else 0
        }

        @JvmStatic
        fun getThemeColors(themeColors: String, themeStyle: String, context: Context, prefs: SharedPreferences, isNight: Boolean): Colors {
            val hasBorders = prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false)
            val backgroundImage = Settings.readUserBackgroundImage(context, isNight)
            return when (themeColors) {
                THEME_USER -> if (prefs.getInt(Settings.getColorPref(Settings.PREF_SHOW_MORE_COLORS, isNight), 0) == 2)
                    AllColors(readAllColorsMap(prefs, false), themeStyle, hasBorders, backgroundImage)
                else DefaultColors(
                    themeStyle,
                    hasBorders,
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_ACCENT_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_BACKGROUND_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_KEYS_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_TEXT_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_HINT_TEXT_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SUGGESTION_TEXT_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, false),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_GESTURE_SUFFIX, false),
                    keyboardBackground = backgroundImage
                )
                THEME_USER_NIGHT -> if (prefs.getInt(Settings.getColorPref(Settings.PREF_SHOW_MORE_COLORS, isNight), 0) == 2)
                    AllColors(readAllColorsMap(prefs, true), themeStyle, hasBorders, backgroundImage)
                else DefaultColors(
                    themeStyle,
                    hasBorders,
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_ACCENT_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_BACKGROUND_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_KEYS_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_FUNCTIONAL_KEYS_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_TEXT_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_HINT_TEXT_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SUGGESTION_TEXT_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_SPACEBAR_TEXT_SUFFIX, true),
                    Settings.readUserColor(prefs, context, Settings.PREF_COLOR_GESTURE_SUFFIX, true),
                    keyboardBackground = backgroundImage
                )
                THEME_DYNAMIC -> {
                    if (Build.VERSION.SDK_INT >= VERSION_CODES.S) DynamicColors(context, themeStyle, hasBorders, backgroundImage)
                    else getThemeColors(THEME_LIGHT, themeStyle, context, prefs, isNight)
                }
                THEME_DARK -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                    Color.parseColor("#263238"),
                    Color.parseColor("#364248"),
                    Color.parseColor("#2d393f"),
                    Color.parseColor("#364248"),
                    ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                    ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark),
                    keyboardBackground = backgroundImage
                )
                THEME_HOLO_WHITE -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.WHITE,
                    Color.parseColor("#282828"),
                    Color.WHITE, // drawable is transparent
                    Color.parseColor("#444444"), // should be 222222, but the key drawable is already grey
                    Color.WHITE,
                    Color.WHITE,
                    Color.parseColor("#282828"),
                    Color.WHITE,
                    Color.parseColor("#80FFFFFF"),
                    keyboardBackground = backgroundImage
                )
                THEME_DARKER -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                    ContextCompat.getColor(context, R.color.keyboard_background_lxx_dark_border),
                    ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                    ContextCompat.getColor(context, R.color.key_background_functional_lxx_dark_border),
                    ContextCompat.getColor(context, R.color.key_background_normal_lxx_dark_border),
                    ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                    ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark),
                    keyboardBackground = backgroundImage
                )
                THEME_BLACK -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_dark),
                    ContextCompat.getColor(context, R.color.background_amoled_black),
                    ContextCompat.getColor(context, R.color.background_amoled_dark),
                    ContextCompat.getColor(context, R.color.background_amoled_dark),
                    ContextCompat.getColor(context, R.color.background_amoled_dark),
                    ContextCompat.getColor(context, R.color.key_text_color_lxx_dark),
                    ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_dark),
                    keyboardBackground = backgroundImage
                )
                THEME_BLUE_GRAY -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(120, 144, 156),
                    Color.rgb(236, 239, 241),
                    Color.WHITE,
                    Color.rgb(207, 216, 220),
                    Color.WHITE,
                    Color.BLACK,
                    Color.BLACK,
                    keyboardBackground = backgroundImage
                )
                THEME_BROWN -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(141, 110, 99),
                    Color.rgb(239, 235, 233),
                    Color.WHITE,
                    Color.rgb(215, 204, 200),
                    Color.WHITE,
                    Color.BLACK,
                    Color.BLACK,
                    keyboardBackground = backgroundImage
                )
                THEME_CHOCOLATE -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(80, 128, 255),
                    Color.rgb(140, 112, 94),
                    Color.rgb(193, 163, 146),
                    Color.rgb(168, 127, 103),
                    Color.rgb(193, 163, 146),
                    Color.WHITE,
                    Color.WHITE,
                    keyboardBackground = backgroundImage
                )
                THEME_CLOUDY -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(255, 113, 129),
                    Color.rgb(81, 97, 113),
                    Color.rgb(117, 128, 142),
                    Color.rgb(99, 109, 121),
                    Color.rgb(117, 128, 142),
                    Color.WHITE,
                    Color.WHITE,
                    keyboardBackground = backgroundImage
                )
                THEME_FOREST -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(75, 110, 75),
                    Color.rgb(181, 125, 88),
                    Color.rgb(228, 212, 191),
                    Color.rgb(212, 186, 153),
                    Color.rgb(228, 212, 191),
                    Color.rgb(0, 50, 0),
                    Color.rgb(0, 50, 0),
                    Color.rgb(0, 50, 0),
                    Color.rgb(0, 80, 0),
                    keyboardBackground = backgroundImage
                )
                THEME_INDIGO -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(92, 107, 192),
                    Color.rgb(232, 234, 246),
                    Color.WHITE,
                    Color.rgb(197, 202, 233),
                    Color.WHITE,
                    Color.BLACK,
                    Color.BLACK,
                    keyboardBackground = backgroundImage
                )
                THEME_OCEAN -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(255, 124, 0),
                    Color.rgb(89, 109, 155),
                    Color.rgb(132, 157, 212),
                    Color.rgb(81, 116, 194),
                    Color.rgb(132, 157, 212),
                    Color.WHITE,
                    Color.WHITE,
                    keyboardBackground = backgroundImage
                )
                THEME_PINK -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(236, 64, 122),
                    Color.rgb(252, 228, 236),
                    Color.WHITE,
                    Color.rgb(248, 187, 208),
                    Color.WHITE,
                    Color.BLACK,
                    Color.BLACK,
                    keyboardBackground = backgroundImage
                )
                THEME_SAND -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(110, 155, 255),
                    Color.rgb(242, 232, 218),
                    Color.WHITE,
                    Color.rgb(234, 211, 185),
                    Color.WHITE,
                    Color.BLACK,
                    Color.BLACK,
                    keyboardBackground = backgroundImage
                )
                THEME_VIOLETTE -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    Color.rgb(255, 96, 255),
                    Color.rgb(112, 112, 174),
                    Color.rgb(150, 150, 216),
                    Color.rgb(123, 123, 206),
                    Color.rgb(150, 150, 216),
                    Color.WHITE,
                    Color.WHITE,
                    keyboardBackground = backgroundImage
                )
                else /* THEME_LIGHT */ -> DefaultColors(
                    themeStyle,
                    hasBorders,
                    ContextCompat.getColor(context, R.color.gesture_trail_color_lxx_light),
                    ContextCompat.getColor(context, R.color.keyboard_background_lxx_light_border),
                    ContextCompat.getColor(context, R.color.key_background_normal_lxx_light_border),
                    ContextCompat.getColor(context, R.color.key_background_functional_lxx_light_border),
                    ContextCompat.getColor(context, R.color.key_background_normal_lxx_light_border),
                    ContextCompat.getColor(context, R.color.key_text_color_lxx_light),
                    ContextCompat.getColor(context, R.color.key_hint_letter_color_lxx_light),
                    keyboardBackground = backgroundImage
                )
            }
        }
    }
}
