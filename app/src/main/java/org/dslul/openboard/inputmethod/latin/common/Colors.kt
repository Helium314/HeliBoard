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
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme
import org.dslul.openboard.inputmethod.keyboard.MoreKeysKeyboardView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.suggestions.MoreSuggestionsView
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
    val adjustedBackground: Int
    val adjustedKeyText: Int

    // todo (later): evaluate which colors, colorFilters and colorStateLists are actually necessary
    //  also, ideally the color filters would be private and chosen internally depending on type
    val backgroundFilter: ColorFilter
    // workaround for error in ClipboardHistoryRecyclerView
    // java.lang.IllegalAccessError: Field 'org.dslul.openboard.inputmethod.latin.common.Colors.backgroundFilter' is inaccessible to class 'org.dslul.openboard.inputmethod.keyboard.clipboard.ClipboardHistoryRecyclerView$BottomDividerItemDecoration'
    // this should not happen, maybe it's a bug in kotlin? because it also doesn't recognize if the filter is accessed there, and wants to set it private
    fun getThatBackgroundFilter() = backgroundFilter
    val adjustedBackgroundFilter: ColorFilter
    val keyBackgroundFilter: ColorFilter
    val functionalKeyBackgroundFilter: ColorFilter
    val spaceBarFilter: ColorFilter
    val keyTextFilter: ColorFilter
    val accentColorFilter: ColorFilter
    val actionKeyIconColorFilter: ColorFilter?

    private val backgroundStateList: ColorStateList
    private val keyStateList: ColorStateList
    private val functionalKeyStateList: ColorStateList
    private val actionKeyStateList: ColorStateList
    private val spaceBarStateList: ColorStateList
    private val adjustedBackgroundStateList: ColorStateList

    val keyboardBackground: Drawable?

    init {
        if (themeStyle == KeyboardTheme.THEME_STYLE_HOLO) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
        } else {
            navBar = background
            keyboardBackground = null
        }

        // create color filters, todo: maybe better / simplify
        val states = arrayOf(intArrayOf(android.R.attr.state_pressed), intArrayOf(-android.R.attr.state_pressed))
        fun stateList(pressed: Int, normal: Int) =
            ColorStateList(states, intArrayOf(pressed, normal))
        // todo (idea): make better use of the states?
        //  could also use / create StateListDrawables in colors (though that's a style than a color...)
        //  this would better allow choosing e.g. cornered/rounded drawables for moreKeys or moreSuggestions
        backgroundFilter = colorFilter(background)
        adjustedKeyText = brightenOrDarken(keyText, true)

        // color to be used if exact background color would be bad contrast, e.g. more keys popup or no border space bar
        if (isDarkColor(background)) {
            adjustedBackground = brighten(background)
            adjustedBackgroundStateList = stateList(brighten(adjustedBackground), adjustedBackground)
        } else {
            adjustedBackground = darken(background)
            adjustedBackgroundStateList = stateList(darken(adjustedBackground), adjustedBackground)
        }
        adjustedBackgroundFilter = colorFilter(adjustedBackground)
        if (hasKeyBorders) {
            keyBackgroundFilter = colorFilter(keyBackground)
            functionalKeyBackgroundFilter = colorFilter(functionalKey)
            spaceBarFilter = colorFilter(spaceBar)
            backgroundStateList = stateList(brightenOrDarken(background, true), background)
            keyStateList = stateList(brightenOrDarken(keyBackground, true), keyBackground)
            functionalKeyStateList = stateList(brightenOrDarken(functionalKey, true), functionalKey)
            actionKeyStateList = if (themeStyle == KeyboardTheme.THEME_STYLE_HOLO) functionalKeyStateList
                else stateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = stateList(brightenOrDarken(spaceBar, true), spaceBar)
        } else {
            // need to set color to background if key borders are disabled, or there will be ugly keys
            keyBackgroundFilter = backgroundFilter
            functionalKeyBackgroundFilter = keyBackgroundFilter
            spaceBarFilter = keyBackgroundFilter
            backgroundStateList = stateList(brightenOrDarken(background, true), background)
            keyStateList = backgroundStateList
            functionalKeyStateList = backgroundStateList
            actionKeyStateList = if (themeStyle == KeyboardTheme.THEME_STYLE_HOLO) functionalKeyStateList
                else stateList(brightenOrDarken(accent, true), accent)
            spaceBarStateList = adjustedBackgroundStateList
        }
        keyTextFilter = colorFilter(keyText, BlendModeCompat.SRC_ATOP)
        accentColorFilter = colorFilter(accent)
        actionKeyIconColorFilter =
            if (isBrightColor(accent)) // the white icon may not have enough contrast, and can't be adjusted by the user
                colorFilter(Color.DKGRAY, BlendModeCompat.SRC_ATOP)
            else null
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
        }
        DrawableCompat.setTintMode(background, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(background, colorStateList)
    }

    // using !! for the color filter because null is only returned for unsupported modes, which are not used
    private fun colorFilter(color: Int, mode: BlendModeCompat = BlendModeCompat.MODULATE): ColorFilter =
        BlendModeColorFilterCompat.createBlendModeColorFilterCompat(color, mode)!!

    fun getDrawable(type: BackgroundType, attr: TypedArray): Drawable {
        val drawable = when (type) {
            BackgroundType.KEY, BackgroundType.ADJUSTED_BACKGROUND, BackgroundType.BACKGROUND ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()
            BackgroundType.FUNCTIONAL -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)?.mutate()
            BackgroundType.SPACE -> attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)?.mutate()
            BackgroundType.ACTION -> if (themeStyle == KeyboardTheme.THEME_STYLE_HOLO)
                attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)?.mutate()
                else attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()
        } ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()!! // keyBackground always exists

        setBackgroundColor(drawable, type)
        return drawable
    }

    fun setKeyboardBackground(view: View) {
        when (view) {
            is MoreSuggestionsView -> view.background.colorFilter = backgroundFilter
            is MoreKeysKeyboardView -> view.background.colorFilter = adjustedBackgroundFilter
            else -> if (keyboardBackground != null) view.background = keyboardBackground
                else view.background.colorFilter = backgroundFilter
        }
    }

}

enum class BackgroundType {
    BACKGROUND, KEY, FUNCTIONAL, ACTION, SPACE, ADJUSTED_BACKGROUND
}
