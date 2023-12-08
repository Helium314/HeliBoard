// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin.common

import android.content.Context
import android.content.SharedPreferences
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.View
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme.STYLE_HOLO
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme.STYLE_MATERIAL
import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView
import org.dslul.openboard.inputmethod.keyboard.MoreKeysKeyboardView
import org.dslul.openboard.inputmethod.keyboard.clipboard.ClipboardHistoryView
import org.dslul.openboard.inputmethod.keyboard.emoji.EmojiPageKeyboardView
import org.dslul.openboard.inputmethod.keyboard.emoji.EmojiPalettesView
import org.dslul.openboard.inputmethod.latin.KeyboardWrapperView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestionsView
import org.dslul.openboard.inputmethod.latin.suggestions.SuggestionStripView
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.adjustLuminosityAndKeepAlpha
import org.dslul.openboard.inputmethod.latin.utils.brighten
import org.dslul.openboard.inputmethod.latin.utils.brightenOrDarken
import org.dslul.openboard.inputmethod.latin.utils.darken
import org.dslul.openboard.inputmethod.latin.utils.isBrightColor
import org.dslul.openboard.inputmethod.latin.utils.isDarkColor

interface Colors {
    /** keep here [themeStyle] because it's actually required in KeyboardView for label placement */
    val themeStyle: String
    /**  keep adjusted background (double) as a value instead of calculating it each time */
    val adjustedBackground: Int
    val doubleAdjustedBackground: Int

    fun hasKeyBorders(context: Context): Boolean {
        val prefs: SharedPreferences = DeviceProtectedUtils.getSharedPreferences(context)
        return prefs.getBoolean(Settings.PREF_THEME_KEY_BORDERS, false);
    }
    @ColorInt fun get(color: ColorType): Int?
    fun setColorFilter(color: ColorType): ColorFilter?
    fun getDrawable(type: BackgroundType, attr: TypedArray, context: Context): Drawable
    fun setKeyboardBackground(view: View, context: Context)
    fun setBackgroundColor(background: Drawable, type: BackgroundType, context: Context)
    fun haveColorsChanged(context: Context): Boolean
}

@RequiresApi(Build.VERSION_CODES.S)
class DynamicColors(context: Context, override val themeStyle: String) : Colors {

    private val isNight = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES

    private val accent = getAccent(context)
    private val gesture = getGesture(context)
    private val background = getBackground(context)
    private val keyBackground = getKeyBackground(context)
    private val functionalKey = getFunctionalKey(context)
    private val spaceBar = getKeyBackground(context)
    private val keyText = getKeyText(context)
    private val keyHintText = getKeyHintText(context)
    private val spaceBarText = getSpaceBarText(context)

    private fun getAccent(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_accent1_100)
        else ContextCompat.getColor(context, android.R.color.system_accent1_200)
    private fun getGesture(context: Context) = if (isNight) accent
        else ContextCompat.getColor(context, android.R.color.system_accent1_600)
    private fun getBackground(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_neutral1_900)
        else ContextCompat.getColor(context, android.R.color.system_neutral1_50)
    private fun getKeyBackground(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_neutral1_800)
        else  ContextCompat.getColor(context, android.R.color.system_neutral1_0)
    private fun getFunctionalKey(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_accent2_300)
        else ContextCompat.getColor(context, android.R.color.system_accent2_100)
    private fun getKeyText(context: Context) = if (isNight) ContextCompat.getColor(context, android.R.color.system_neutral1_50)
        else ContextCompat.getColor(context, android.R.color.system_accent3_900)
    private fun getKeyHintText(context: Context) = if (isNight) keyText
        else ContextCompat.getColor(context, android.R.color.system_accent3_700)
    private fun getSpaceBarText(context: Context) = if (isNight) ColorUtils.setAlphaComponent(ContextCompat.getColor(context, android.R.color.system_neutral1_50), 127)
        else ColorUtils.setAlphaComponent(ContextCompat.getColor(context, android.R.color.system_accent3_700), 127)

    override fun haveColorsChanged(context: Context) =
        accent != getAccent(context)
                || gesture != getGesture(context)
                || background != getBackground(context)
                || keyBackground != getKeyBackground(context)
                || functionalKey != getFunctionalKey(context)
                || keyText != getKeyText(context)
                || keyHintText != getKeyHintText(context)
                || spaceBarText != getSpaceBarText(context)

    private val navBar: Int
    /** brightened or darkened variant of [background], to be used if exact background color would be
     *  bad contrast, e.g. more keys popup or no border space bar */
    override val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    override val doubleAdjustedBackground: Int
    /** brightened or darkened variant of [keyText] */
    private val adjustedKeyText: Int

    private val backgroundFilter: ColorFilter
    private val adjustedBackgroundFilter: ColorFilter
    private val keyTextFilter: ColorFilter
    private val accentColorFilter: ColorFilter
    /** color filter for the white action key icons in material theme, switches to gray if necessary for contrast */
    private val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList
    private val suggestionBackgroundList: ColorStateList

    /** custom drawable used for keyboard background */
    private val keyboardBackground: Drawable?

    /** darkened variant of [accent] because the accent color is always light for dynamic colors */
    private val adjustedAccent: Int = darken(accent)
    /** further darkened variant of [adjustedAccent] */
    private val doubleAdjustedAccent: Int = darken(adjustedAccent)

    /** darkened variant of [functionalKey] used in day mode */
    private val adjustedFunctionalKey: Int = darken(functionalKey)
    /** further darkened variant of [adjustedFunctionalKey] */
    private val doubleAdjustedFunctionalKey: Int = darken(adjustedFunctionalKey)

    /** brightened variant of [keyBackground] used in night mode */
    private val adjustedKeyBackground: Int = brighten(keyBackground)
    /** further brightened variant of [adjustedKeyBackground] */
    private val doubleAdjustedKeyBackground: Int = brighten(adjustedKeyBackground)

    init {
        accentColorFilter = colorFilter(doubleAdjustedAccent)

        if (themeStyle == STYLE_HOLO) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
        } else {
            navBar = background
            keyboardBackground = null
        }

        // todo (idea): make better use of the states?
        //  could also use / create StateListDrawables in colors (though that's a style than a color...)
        //  this would better allow choosing e.g. cornered/rounded drawables for moreKeys or moreSuggestions
        backgroundFilter = colorFilter(background)
        adjustedKeyText = brightenOrDarken(keyText, true)

        if (isDarkColor(background)) {
            adjustedBackground = brighten(background)
            doubleAdjustedBackground = brighten(adjustedBackground)
        } else {
            adjustedBackground = darken(background)
            doubleAdjustedBackground = darken(adjustedBackground)
        }
        adjustedBackgroundStateList =
            if (themeStyle == STYLE_HOLO) {
                stateList(accent, adjustedBackground)
            } else if (isNight) {
                if (hasKeyBorders(context)) stateList(doubleAdjustedAccent, keyBackground)
                else stateList(adjustedAccent, adjustedKeyBackground)
            } else {
                stateList(accent, Color.WHITE)
            }

        suggestionBackgroundList = if (!hasKeyBorders(context) && themeStyle == STYLE_MATERIAL)
            stateList(doubleAdjustedBackground, Color.TRANSPARENT)
        else
            stateList(adjustedBackground, Color.TRANSPARENT)

        adjustedBackgroundFilter =
            if (themeStyle == STYLE_HOLO) colorFilter(adjustedBackground)
            else colorFilter(keyBackground)

        if (hasKeyBorders(context)) {
            backgroundStateList =
                if (!isNight) stateList(adjustedFunctionalKey, background)
                else stateList(adjustedKeyBackground, background)

            keyStateList =
                if (!isNight) stateList(adjustedBackground, keyBackground)
                else stateList(adjustedKeyBackground, keyBackground)

            functionalKeyStateList =
                if (!isNight) stateList(doubleAdjustedFunctionalKey, functionalKey)
                else stateList(functionalKey, doubleAdjustedKeyBackground)

            actionKeyStateList =
                if (!isNight) stateList(gesture, accent)
                else stateList(doubleAdjustedAccent, accent)

            spaceBarStateList =
                if (themeStyle == STYLE_HOLO) stateList(spaceBar, spaceBar)
                else keyStateList

        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            backgroundStateList =
                if (!isNight) stateList(adjustedFunctionalKey, background)
                else stateList(adjustedKeyBackground, background)

            keyStateList =
                if (!isNight) stateList(adjustedFunctionalKey, Color.TRANSPARENT)
                else stateList(functionalKey, Color.TRANSPARENT)

            functionalKeyStateList =
                if (themeStyle == STYLE_HOLO) stateList(functionalKey, Color.TRANSPARENT)
                else keyStateList

            actionKeyStateList =
                if (themeStyle == STYLE_HOLO) stateList(accent, Color.TRANSPARENT)
                else if (!isNight) stateList(gesture, accent)
                else stateList(doubleAdjustedAccent, accent)

            spaceBarStateList =
                if (!isNight) stateList(gesture, adjustedFunctionalKey)
                else stateList(adjustedKeyBackground, spaceBar)
        }
        keyTextFilter = colorFilter(keyText)

        actionKeyIconColorFilter = when {
            themeStyle == STYLE_HOLO -> keyTextFilter
            // the white icon may not have enough contrast, and can't be adjusted by the user
            isBrightColor(accent) -> colorFilter(Color.DKGRAY)
            else -> null
        }
    }

    override fun get(color: ColorType): Int? = when (color) {
        ColorType.ACCENT -> accent
        ColorType.GESTURE -> gesture
        ColorType.KEY_TEXT -> keyText
        ColorType.SPACEBAR_TEXT -> spaceBarText
        ColorType.NAV_BAR -> navBar
        ColorType.ADJUSTED_KEY_TEXT -> brightenOrDarken(keyText, true)
        ColorType.ACTION_KEY_ICON -> Color.WHITE
        else -> { null }
    }

    override fun setColorFilter(color: ColorType): ColorFilter? = when (color) {
        ColorType.ACCENT -> accentColorFilter
        ColorType.KEY_TEXT -> keyTextFilter
        ColorType.ADJUSTED_BACKGROUND -> adjustedBackgroundFilter
        ColorType.ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> { null }
    }

    /** set background colors including state list to the drawable  */
    override fun setBackgroundColor(background: Drawable, type: BackgroundType, context: Context) {
        val colorStateList = when (type) {
            BackgroundType.BACKGROUND, BackgroundType.SUGGESTION -> backgroundStateList
            BackgroundType.KEY -> keyStateList
            BackgroundType.FUNCTIONAL -> functionalKeyStateList
            BackgroundType.ACTION -> actionKeyStateList
            BackgroundType.SPACE -> spaceBarStateList
            BackgroundType.ADJUSTED_BACKGROUND -> adjustedBackgroundStateList
            BackgroundType.ACTION_MORE_KEYS -> if (themeStyle == STYLE_HOLO)
                adjustedBackgroundStateList
            else actionKeyStateList
        }
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(background, colorStateList)
    }

    override fun getDrawable(type: BackgroundType, attr: TypedArray, context: Context): Drawable {
        val drawable = when (type) {
            BackgroundType.KEY, BackgroundType.ADJUSTED_BACKGROUND, BackgroundType.BACKGROUND,
            BackgroundType.SUGGESTION, BackgroundType.ACTION_MORE_KEYS ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            BackgroundType.FUNCTIONAL -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
            BackgroundType.SPACE -> {
                if (hasKeyBorders(context)) attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)
                else attr.getDrawable(R.styleable.KeyboardView_spacebarNoBorderBackground)
            }
            BackgroundType.ACTION -> {
                if (themeStyle == STYLE_HOLO && hasKeyBorders(context)) // no borders has a very small pressed drawable otherwise
                    attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
                else
                    attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            }
        }?.mutate() ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()!! // keyBackground always exists

        setBackgroundColor(drawable, type, context)
        return drawable
    }

    override fun setKeyboardBackground(view: View, context: Context) {
        when (view) {
            is MoreSuggestionsView -> view.background.colorFilter = backgroundFilter
            is MoreKeysKeyboardView ->
                if (themeStyle != STYLE_HOLO)
                    setBackgroundColor(view.background, BackgroundType.ADJUSTED_BACKGROUND, context)
                else view.background.colorFilter = adjustedBackgroundFilter
            is SuggestionStripView -> setBackgroundColor(view.background, BackgroundType.SUGGESTION, context)
            is EmojiPageKeyboardView, // to make EmojiPalettesView background visible, which does not scroll
            is MainKeyboardView -> view.setBackgroundColor(Color.TRANSPARENT) // otherwise causes issues with wrapper view when using one-handed mode
            is KeyboardWrapperView, is EmojiPalettesView, is ClipboardHistoryView -> {
                if (keyboardBackground != null) view.background = keyboardBackground
                else view.background.colorFilter = backgroundFilter
            }
            else -> view.background.colorFilter = backgroundFilter
        }
    }
}

class OriginalColors (
    context: Context,
    override val themeStyle: String,
    private val accent: Int,
    private val gesture: Int,
    private val background: Int,
    private val keyBackground: Int,
    private val functionalKey: Int,
    private val spaceBar: Int,
    private val keyText: Int,
    private val keyHintText: Int,
    private val spaceBarText: Int
) : Colors {
    private val navBar: Int
    /** brightened or darkened variant of [background], to be used if exact background color would be
     *  bad contrast, e.g. more keys popup or no border space bar */
    override val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    override val doubleAdjustedBackground: Int
    /** brightened or darkened variant of [keyText] */
    private val adjustedKeyText: Int

    private val backgroundFilter: ColorFilter
    private val adjustedBackgroundFilter: ColorFilter
    //    val keyBackgroundFilter: ColorFilter
    //    val functionalKeyBackgroundFilter: ColorFilter
    //    val spaceBarFilter: ColorFilter
    private val keyTextFilter: ColorFilter
    private val accentColorFilter: ColorFilter
    /** color filter for the white action key icons in material theme, switches to gray if necessary for contrast */
    private val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList
    private val suggestionBackgroundList: ColorStateList

    /** custom drawable used for keyboard background */
    private val keyboardBackground: Drawable?

    override fun haveColorsChanged(context: Context) = false

    init {
        accentColorFilter = colorFilter(accent)
        if (themeStyle == STYLE_HOLO) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
        } else {
            navBar = background
            keyboardBackground = null
        }

        // todo (idea): make better use of the states?
        //  could also use / create StateListDrawables in colors (though that's a style than a color...)
        //  this would better allow choosing e.g. cornered/rounded drawables for moreKeys or moreSuggestions
        backgroundFilter = colorFilter(background)
        adjustedKeyText = brightenOrDarken(keyText, true)

        if (isDarkColor(background)) {
            adjustedBackground = brighten(background)
            doubleAdjustedBackground = brighten(adjustedBackground)
        } else {
            adjustedBackground = darken(background)
            doubleAdjustedBackground = darken(adjustedBackground)
        }
        adjustedBackgroundStateList = stateList(doubleAdjustedBackground, adjustedBackground)
        suggestionBackgroundList = if (!hasKeyBorders(context) && themeStyle == STYLE_MATERIAL)
            stateList(doubleAdjustedBackground, Color.TRANSPARENT)
        else
            stateList(adjustedBackground, Color.TRANSPARENT)

        adjustedBackgroundFilter = colorFilter(adjustedBackground)
        if (hasKeyBorders(context)) {
//            keyBackgroundFilter = colorFilter(keyBackground)
//            functionalKeyBackgroundFilter = colorFilter(functionalKey)
//            spaceBarFilter = colorFilter(spaceBar)
            backgroundStateList = stateList(brightenOrDarken(background, true), background)
            keyStateList = if (themeStyle == STYLE_HOLO) stateList(keyBackground, keyBackground)
                else stateList(brightenOrDarken(keyBackground, true), keyBackground)
            functionalKeyStateList = stateList(brightenOrDarken(functionalKey, true), functionalKey)
            actionKeyStateList = if (themeStyle == STYLE_HOLO) functionalKeyStateList
                else stateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = if (themeStyle == STYLE_HOLO) stateList(spaceBar, spaceBar)
                else stateList(brightenOrDarken(spaceBar, true), spaceBar)
        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
//            keyBackgroundFilter = backgroundFilter
//            functionalKeyBackgroundFilter = keyBackgroundFilter
//            spaceBarFilter = colorFilter(spaceBar)
            backgroundStateList = stateList(brightenOrDarken(background, true), background)
            keyStateList = stateList(brightenOrDarken(background, true), Color.TRANSPARENT)
            functionalKeyStateList = keyStateList
            actionKeyStateList = if (themeStyle == STYLE_HOLO) functionalKeyStateList
                else stateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = stateList(brightenOrDarken(spaceBar, true), spaceBar)
        }
        keyTextFilter = colorFilter(keyText)
        actionKeyIconColorFilter = when {
            themeStyle == STYLE_HOLO -> keyTextFilter
            // the white icon may not have enough contrast, and can't be adjusted by the user
            isBrightColor(accent) -> colorFilter(Color.DKGRAY)
            else -> null
        }
    }

    override fun get(color: ColorType): Int? = when (color) {
        ColorType.ACCENT -> accent
        ColorType.GESTURE -> gesture
        ColorType.KEY_TEXT -> keyText
        ColorType.SPACEBAR_TEXT -> spaceBarText
        ColorType.NAV_BAR -> navBar
        ColorType.ADJUSTED_KEY_TEXT -> brightenOrDarken(keyText, true)
        ColorType.ACTION_KEY_ICON -> Color.WHITE
        else -> { null }
    }

    override fun setColorFilter(color: ColorType): ColorFilter? = when (color) {
        ColorType.ACCENT -> accentColorFilter
        ColorType.KEY_TEXT -> keyTextFilter
        ColorType.ADJUSTED_BACKGROUND -> adjustedBackgroundFilter
        ColorType.ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> { null }
    }

    /** set background colors including state list to the drawable  */
    override fun setBackgroundColor(background: Drawable, type: BackgroundType, context: Context) {
        val colorStateList = when (type) {
            BackgroundType.BACKGROUND -> backgroundStateList
            BackgroundType.KEY -> keyStateList
            BackgroundType.FUNCTIONAL -> functionalKeyStateList
            BackgroundType.ACTION -> actionKeyStateList
            BackgroundType.SPACE -> spaceBarStateList
            BackgroundType.ADJUSTED_BACKGROUND -> adjustedBackgroundStateList
            BackgroundType.SUGGESTION -> if (!hasKeyBorders(context) && themeStyle == STYLE_MATERIAL)
                    adjustedBackgroundStateList
                else backgroundStateList
            BackgroundType.ACTION_MORE_KEYS -> if (themeStyle == STYLE_HOLO)
                    adjustedBackgroundStateList
                else actionKeyStateList
        }
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(background, colorStateList)
    }

    override fun getDrawable(type: BackgroundType, attr: TypedArray, context: Context): Drawable {
        val drawable = when (type) {
            BackgroundType.KEY, BackgroundType.ADJUSTED_BACKGROUND, BackgroundType.BACKGROUND,
            BackgroundType.SUGGESTION, BackgroundType.ACTION_MORE_KEYS ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            BackgroundType.FUNCTIONAL -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
            BackgroundType.SPACE -> {
                if (hasKeyBorders(context)) attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)
                else attr.getDrawable(R.styleable.KeyboardView_spacebarNoBorderBackground)
            }
            BackgroundType.ACTION -> {
                if (themeStyle == STYLE_HOLO && hasKeyBorders(context)) // no borders has a very small pressed drawable otherwise
                    attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
                else
                    attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            }
        }?.mutate() ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()!! // keyBackground always exists

        setBackgroundColor(drawable, type, context)
        return drawable
    }

    override fun setKeyboardBackground(view: View, context: Context) {
        when (view) {
            is MoreSuggestionsView -> view.background.colorFilter = backgroundFilter
            is MoreKeysKeyboardView -> view.background.colorFilter = adjustedBackgroundFilter
            is SuggestionStripView -> setBackgroundColor(view.background, BackgroundType.SUGGESTION, context)
            is EmojiPageKeyboardView, // to make EmojiPalettesView background visible, which does not scroll
            is MainKeyboardView -> view.setBackgroundColor(Color.TRANSPARENT) // otherwise causes issues with wrapper view when using one-handed mode
            is KeyboardWrapperView, is EmojiPalettesView, is ClipboardHistoryView -> {
                if (keyboardBackground != null) view.background = keyboardBackground
                else view.background.colorFilter = backgroundFilter
            }
            else -> view.background.colorFilter = backgroundFilter
        }
    }

}

private fun colorFilter(color: Int, mode: BlendModeCompat = BlendModeCompat.MODULATE): ColorFilter {
    // using !! for the color filter because null is only returned for unsupported blend modes, which are not used
    return BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, mode)!!
}

private fun stateList(pressed: Int, normal: Int): ColorStateList {
    val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed))
    return ColorStateList(states, intArrayOf(pressed, normal))
}

enum class ColorType { ACCENT, GESTURE, KEY_TEXT, SPACEBAR_TEXT, NAV_BAR, ADJUSTED_BACKGROUND,
    ADJUSTED_KEY_TEXT, ACTION_KEY_ICON }

enum class BackgroundType {
    /** generic background */
    BACKGROUND,
    /** key background */
    KEY,
    /** functional key background */
    FUNCTIONAL,
    /** action key background */
    ACTION,
    /** action key more keys background */
    ACTION_MORE_KEYS,
    /** space bar background */
    SPACE,
    /** background with some contrast to [BACKGROUND], based on adjustedBackground color */
    ADJUSTED_BACKGROUND,
    /** background for suggestions and similar, transparent when not pressed */
    SUGGESTION
}
