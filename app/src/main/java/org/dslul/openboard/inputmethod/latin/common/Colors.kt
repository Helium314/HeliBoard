// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin.common

import android.content.res.ColorStateList
import android.content.res.TypedArray
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.View
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.drawable.DrawableCompat
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme.THEME_STYLE_HOLO
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme.THEME_STYLE_MATERIAL
import org.dslul.openboard.inputmethod.keyboard.MainKeyboardView
import org.dslul.openboard.inputmethod.keyboard.MoreKeysKeyboardView
import org.dslul.openboard.inputmethod.keyboard.clipboard.ClipboardHistoryView
import org.dslul.openboard.inputmethod.keyboard.emoji.EmojiPageKeyboardView
import org.dslul.openboard.inputmethod.keyboard.emoji.EmojiPalettesView
import org.dslul.openboard.inputmethod.latin.KeyboardWrapperView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestionsView
import org.dslul.openboard.inputmethod.latin.suggestions.SuggestionStripView
import org.dslul.openboard.inputmethod.latin.utils.*

class Colors (
    val themeStyle: String,
    val hasKeyBorders: Boolean,
    val accent: Int,
    val background: Int,
    val keyBackground: Int,
    val functionalKey: Int,
    val spaceBar: Int,
    val keyText: Int,
    val keyHintText: Int
) {
    val navBar: Int
    /** brightened or darkened variant of [background], to be used if exact background color would be
     *  bad contrast, e.g. more keys popup or no border space bar */
    val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    val doubleAdjustedBackground: Int
    /** brightened or darkened variant of [keyText] */
    val adjustedKeyText: Int
    val spaceBarText: Int

    // todo (later): evaluate which colors, colorFilters and colorStateLists are actually necessary
    //  also, ideally the color filters would be private and chosen internally depending on type
    val backgroundFilter: ColorFilter
    val adjustedBackgroundFilter: ColorFilter
    val keyBackgroundFilter: ColorFilter
    val functionalKeyBackgroundFilter: ColorFilter
    val spaceBarFilter: ColorFilter
    val keyTextFilter: ColorFilter
    val accentColorFilter: ColorFilter
    /** color filter for the white action key icons in material theme, switches to gray if necessary for contrast */
    val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList
    private val suggestionBackgroundList: ColorStateList

    /** custom drawable used for keyboard background */
    private val keyboardBackground: Drawable?

    init {
        accentColorFilter = colorFilter(accent)
        if (themeStyle == THEME_STYLE_HOLO) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
            spaceBarText = keyText
        } else {
            navBar = background
            keyboardBackground = null
            spaceBarText = keyHintText
        }

        // create color filters, todo: maybe better / simplify
        val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed))
        fun stateList(pressed: Int, normal: Int) = ColorStateList(states, intArrayOf(pressed, normal))
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
        suggestionBackgroundList = if (!hasKeyBorders && themeStyle == THEME_STYLE_MATERIAL)
            stateList(doubleAdjustedBackground, Color.TRANSPARENT)
        else
            stateList(adjustedBackground, Color.TRANSPARENT)

        adjustedBackgroundFilter = colorFilter(adjustedBackground)
        if (hasKeyBorders) {
            keyBackgroundFilter = colorFilter(keyBackground)
            functionalKeyBackgroundFilter = colorFilter(functionalKey)
            spaceBarFilter = colorFilter(spaceBar)
            backgroundStateList = stateList(brightenOrDarken(background, true), background)
            keyStateList = if (themeStyle == THEME_STYLE_HOLO) stateList(keyBackground, keyBackground)
                else stateList(brightenOrDarken(keyBackground, true), keyBackground)
            functionalKeyStateList = stateList(brightenOrDarken(functionalKey, true), functionalKey)
            actionKeyStateList = if (themeStyle == THEME_STYLE_HOLO) functionalKeyStateList
                else stateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = if (themeStyle == THEME_STYLE_HOLO) stateList(spaceBar, spaceBar)
                else stateList(brightenOrDarken(spaceBar, true), spaceBar)
        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            keyBackgroundFilter = backgroundFilter
            functionalKeyBackgroundFilter = keyBackgroundFilter
            spaceBarFilter = colorFilter(spaceBar)
            backgroundStateList = stateList(brightenOrDarken(background, true), background)
            keyStateList = backgroundStateList
            functionalKeyStateList = backgroundStateList
            actionKeyStateList = if (themeStyle == THEME_STYLE_HOLO) functionalKeyStateList
                else stateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = stateList(brightenOrDarken(spaceBar, true), spaceBar)
        }
        keyTextFilter = colorFilter(keyText, BlendModeCompat.SRC_ATOP)
        actionKeyIconColorFilter = when {
            themeStyle == THEME_STYLE_HOLO -> keyTextFilter
            // the white icon may not have enough contrast, and can't be adjusted by the user
            isBrightColor(accent) -> colorFilter(Color.DKGRAY, BlendModeCompat.SRC_ATOP)
            else -> null
        }
    }

    /** set background colors including state list to the drawable  */
    fun setBackgroundColor(background: Drawable, type: BackgroundType) {
        val colorStateList = when (type) {
            BackgroundType.BACKGROUND -> backgroundStateList
            BackgroundType.KEY -> keyStateList
            BackgroundType.FUNCTIONAL -> functionalKeyStateList
            BackgroundType.ACTION -> actionKeyStateList
            BackgroundType.SPACE -> spaceBarStateList
            BackgroundType.ADJUSTED_BACKGROUND -> adjustedBackgroundStateList
            BackgroundType.SUGGESTION -> if (!hasKeyBorders && themeStyle == THEME_STYLE_MATERIAL)
                    adjustedBackgroundStateList
                else backgroundStateList
            BackgroundType.ACTION_MORE_KEYS -> if (themeStyle == THEME_STYLE_HOLO)
                    adjustedBackgroundStateList
                else actionKeyStateList
        }
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(background, colorStateList)
    }

    // using !! for the color filter because null is only returned for unsupported blend modes, which are not used
    private fun colorFilter(color: Int, mode: BlendModeCompat = BlendModeCompat.MODULATE): ColorFilter =
        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, mode)!!

    fun getDrawable(type: BackgroundType, attr: TypedArray): Drawable {
        val drawable = when (type) {
            BackgroundType.KEY, BackgroundType.ADJUSTED_BACKGROUND, BackgroundType.BACKGROUND,
            BackgroundType.SUGGESTION, BackgroundType.ACTION_MORE_KEYS ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            BackgroundType.FUNCTIONAL -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
            BackgroundType.SPACE -> attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)
            BackgroundType.ACTION -> {
                if (themeStyle == THEME_STYLE_HOLO && hasKeyBorders) // no borders has a very small pressed drawable otherwise
                    attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
                else
                    attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            }
        }?.mutate() ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()!! // keyBackground always exists

        setBackgroundColor(drawable, type)
        return drawable
    }

    fun setKeyboardBackground(view: View) {
        when (view) {
            is MoreSuggestionsView -> view.background.colorFilter = backgroundFilter
            is MoreKeysKeyboardView -> view.background.colorFilter = adjustedBackgroundFilter
            is SuggestionStripView -> setBackgroundColor(view.background, BackgroundType.SUGGESTION)
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
