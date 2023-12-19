// SPDX-License-Identifier: GPL-3.0-only

package org.dslul.openboard.inputmethod.latin.common

import android.content.Context
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
import android.widget.ImageView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.DrawableCompat
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme.STYLE_HOLO
import org.dslul.openboard.inputmethod.keyboard.KeyboardTheme.STYLE_MATERIAL
import org.dslul.openboard.inputmethod.latin.common.ColorType.*
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.utils.adjustLuminosityAndKeepAlpha
import org.dslul.openboard.inputmethod.latin.utils.brighten
import org.dslul.openboard.inputmethod.latin.utils.brightenOrDarken
import org.dslul.openboard.inputmethod.latin.utils.darken
import org.dslul.openboard.inputmethod.latin.utils.isBrightColor
import org.dslul.openboard.inputmethod.latin.utils.isDarkColor

interface Colors {
    // these theme parameters should no be in here, but are still used
    /** used in KeyboardView for label placement */
    val themeStyle: String
    /** used in parser to decide background of ZWNJ key */
    val hasKeyBorders: Boolean

    /** use to check whether colors have changed, for colors (in)directly derived from context,
     *  e.g. night mode or potentially changing system colors */
    fun haveColorsChanged(context: Context): Boolean

    /** get the colorInt */
    @ColorInt fun get(color: ColorType): Int

    /** apply a color to the [drawable], may be through color filter or tint (with or without state list) */
    fun setColor(drawable: Drawable, color: ColorType)

    /** set a foreground color to the [view] */
    fun setColor(view: ImageView, color: ColorType)

    /** set a background to the [view], may replace or adjust existing background */
    fun setBackground(view: View, color: ColorType)

    /** returns a colored drawable selected from [attr], which must contain using R.styleable.KeyboardView_* */
    fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable
}

@RequiresApi(Build.VERSION_CODES.S)
class DynamicColors(context: Context, override val themeStyle: String, override val hasKeyBorders: Boolean) : Colors {

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
    private val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    private val doubleAdjustedBackground: Int
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
                if (hasKeyBorders) stateList(doubleAdjustedAccent, keyBackground)
                else stateList(adjustedAccent, adjustedKeyBackground)
            } else {
                stateList(accent, Color.WHITE)
            }

        suggestionBackgroundList = if (!hasKeyBorders && themeStyle == STYLE_MATERIAL)
            stateList(doubleAdjustedBackground, Color.TRANSPARENT)
        else
            stateList(adjustedBackground, Color.TRANSPARENT)

        adjustedBackgroundFilter =
            if (themeStyle == STYLE_HOLO) colorFilter(adjustedBackground)
            else colorFilter(keyBackground)

        if (hasKeyBorders) {
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

    override fun get(color: ColorType): Int = when (color) {
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, ACTION_KEY_BACKGROUND,
        CLIPBOARD_PIN, SHIFT_KEY_ICON -> accent
        EMOJI_CATEGORY_BACKGROUND, GESTURE_PREVIEW, MORE_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, KEY_PREVIEW -> adjustedBackground
        TOOL_BAR_EXPAND_KEY_BACKGROUND -> if (!isNight) accent else doubleAdjustedBackground
        GESTURE_TRAIL -> gesture
        KEY_TEXT, SUGGESTION_AUTO_CORRECT, REMOVE_SUGGESTION_ICON, CLEAR_CLIPBOARD_HISTORY_KEY,
        KEY_ICON, ONE_HANDED_MODE_BUTTON, EMOJI_CATEGORY, TOOL_BAR_KEY -> keyText
        KEY_HINT_TEXT -> keyHintText
        SPACE_BAR_TEXT -> spaceBarText
        FUNCTIONAL_KEY_BACKGROUND -> functionalKey
        SPACE_BAR_BACKGROUND -> spaceBar
        BACKGROUND, KEYBOARD_WRAPPER_BACKGROUND, CLIPBOARD_BACKGROUND, EMOJI_BACKGROUND, KEYBOARD_BACKGROUND -> background
        KEY_BACKGROUND -> keyBackground
        ACTION_KEY_MORE_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackground else accent
        SUGGESTION_BACKGROUND -> if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) adjustedBackground else background
        NAVIGATION_BAR -> navBar
        MORE_SUGGESTIONS_HINT, SUGGESTED_WORD, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD -> adjustedKeyText
        ACTION_KEY_ICON, TOOL_BAR_EXPAND_KEY -> Color.WHITE
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        val colorStateList = when (color) {
            BACKGROUND -> backgroundStateList
            KEY_BACKGROUND -> keyStateList
            FUNCTIONAL_KEY_BACKGROUND -> functionalKeyStateList
            ACTION_KEY_BACKGROUND -> actionKeyStateList
            SPACE_BAR_BACKGROUND -> spaceBarStateList
            MORE_KEYS_BACKGROUND -> adjustedBackgroundStateList
            SUGGESTION_BACKGROUND -> if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) adjustedBackgroundStateList
            else backgroundStateList
            ACTION_KEY_MORE_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackgroundStateList
            else actionKeyStateList
            else -> null // use color filter
        }
        if (colorStateList == null) {
            drawable.colorFilter = getColorFilter(color)
            return
        }
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, colorStateList)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        view.colorFilter = getColorFilter(color)
    }

    private fun getColorFilter(color: ColorType): ColorFilter? = when (color) {
        EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN, SHIFT_KEY_ICON -> accentColorFilter
        REMOVE_SUGGESTION_ICON, CLEAR_CLIPBOARD_HISTORY_KEY, EMOJI_CATEGORY, KEY_TEXT,
            KEY_ICON, ONE_HANDED_MODE_BUTTON, TOOL_BAR_KEY, TOOL_BAR_EXPAND_KEY -> keyTextFilter
        KEY_PREVIEW -> adjustedBackgroundFilter
        ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> colorFilter(get(color))
    }

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable {
        val drawable = when (color) {
            KEY_BACKGROUND, BACKGROUND, SUGGESTION_BACKGROUND, ACTION_KEY_MORE_KEYS_BACKGROUND, MORE_KEYS_BACKGROUND ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            FUNCTIONAL_KEY_BACKGROUND -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
            SPACE_BAR_BACKGROUND -> {
                if (hasKeyBorders) attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)
                else attr.getDrawable(R.styleable.KeyboardView_spacebarNoBorderBackground)
            }
            ACTION_KEY_BACKGROUND -> {
                if (themeStyle == STYLE_HOLO && hasKeyBorders) // no borders has a very small pressed drawable otherwise
                    attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
                else
                    attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            }
            else -> null // keyBackground
        }?.mutate() ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()!! // keyBackground always exists

        setColor(drawable, color)
        return drawable
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // set white to make the color filters word
        when (color) {
            CLEAR_CLIPBOARD_HISTORY_KEY -> setColor(view.background, SUGGESTION_BACKGROUND)
            EMOJI_CATEGORY_BACKGROUND -> view.setBackgroundColor(get(color))
            KEY_PREVIEW -> view.background.colorFilter = adjustedBackgroundFilter
            FUNCTIONAL_KEY_BACKGROUND, KEY_BACKGROUND, BACKGROUND, SPACE_BAR_BACKGROUND, SUGGESTION_BACKGROUND -> setColor(view.background, color)
            MORE_SUGGESTIONS_BACKGROUND -> view.background.colorFilter = backgroundFilter
            MORE_KEYS_BACKGROUND ->
                if (themeStyle != STYLE_HOLO)
                    setColor(view.background, MORE_KEYS_BACKGROUND)
                else view.background.colorFilter = adjustedBackgroundFilter
            KEYBOARD_BACKGROUND -> view.setBackgroundColor(Color.TRANSPARENT)
            EMOJI_BACKGROUND, CLIPBOARD_BACKGROUND, KEYBOARD_WRAPPER_BACKGROUND -> {
                if (keyboardBackground != null) view.background = keyboardBackground
                else view.background.colorFilter = backgroundFilter
            }
            else -> view.background.colorFilter = backgroundFilter
        }
    }
}

class DefaultColors (
    override val themeStyle: String,
    override val hasKeyBorders: Boolean,
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
    private val adjustedBackground: Int
    /** further brightened or darkened variant of [adjustedBackground] */
    private val doubleAdjustedBackground: Int
    /** brightened or darkened variant of [keyText] */
    private val adjustedKeyText: Int

    private val backgroundFilter: ColorFilter
    private val adjustedBackgroundFilter: ColorFilter
    private val keyTextFilter: ColorFilter
    private val accentColorFilter: ColorFilter = colorFilter(accent)

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
        if (themeStyle == STYLE_HOLO) {
            val darkerBackground = adjustLuminosityAndKeepAlpha(background, -0.2f)
            navBar = darkerBackground
            keyboardBackground = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(background, darkerBackground))
        } else {
            navBar = background
            keyboardBackground = null
        }

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
        suggestionBackgroundList = if (!hasKeyBorders && themeStyle == STYLE_MATERIAL)
            stateList(doubleAdjustedBackground, Color.TRANSPARENT)
        else
            stateList(adjustedBackground, Color.TRANSPARENT)

        adjustedBackgroundFilter = colorFilter(adjustedBackground)
        if (hasKeyBorders) {
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

    override fun get(color: ColorType): Int = when (color) {
        TOOL_BAR_KEY_ENABLED_BACKGROUND, EMOJI_CATEGORY_SELECTED, ACTION_KEY_BACKGROUND,
            CLIPBOARD_PIN, SHIFT_KEY_ICON -> accent
        EMOJI_CATEGORY_BACKGROUND, GESTURE_PREVIEW, MORE_KEYS_BACKGROUND, MORE_SUGGESTIONS_BACKGROUND, KEY_PREVIEW -> adjustedBackground
        TOOL_BAR_EXPAND_KEY_BACKGROUND -> doubleAdjustedBackground
        GESTURE_TRAIL -> gesture
        KEY_TEXT, SUGGESTION_AUTO_CORRECT, REMOVE_SUGGESTION_ICON, CLEAR_CLIPBOARD_HISTORY_KEY,
            KEY_ICON, ONE_HANDED_MODE_BUTTON, EMOJI_CATEGORY, TOOL_BAR_KEY, TOOL_BAR_EXPAND_KEY -> keyText
        KEY_HINT_TEXT -> keyHintText
        SPACE_BAR_TEXT -> spaceBarText
        FUNCTIONAL_KEY_BACKGROUND -> functionalKey
        SPACE_BAR_BACKGROUND -> spaceBar
        BACKGROUND, KEYBOARD_WRAPPER_BACKGROUND, CLIPBOARD_BACKGROUND, EMOJI_BACKGROUND, KEYBOARD_BACKGROUND -> background
        KEY_BACKGROUND -> keyBackground
        ACTION_KEY_MORE_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackground else accent
        SUGGESTION_BACKGROUND -> if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) adjustedBackground else background
        NAVIGATION_BAR -> navBar
        MORE_SUGGESTIONS_HINT, SUGGESTED_WORD, SUGGESTION_TYPED_WORD, SUGGESTION_VALID_WORD -> adjustedKeyText
        ACTION_KEY_ICON -> Color.WHITE
    }

    override fun setColor(drawable: Drawable, color: ColorType) {
        val colorStateList = when (color) {
            BACKGROUND -> backgroundStateList
            KEY_BACKGROUND -> keyStateList
            FUNCTIONAL_KEY_BACKGROUND -> functionalKeyStateList
            ACTION_KEY_BACKGROUND -> actionKeyStateList
            SPACE_BAR_BACKGROUND -> spaceBarStateList
            MORE_KEYS_BACKGROUND -> adjustedBackgroundStateList
            SUGGESTION_BACKGROUND -> if (!hasKeyBorders && themeStyle == STYLE_MATERIAL) adjustedBackgroundStateList
                else backgroundStateList
            ACTION_KEY_MORE_KEYS_BACKGROUND -> if (themeStyle == STYLE_HOLO) adjustedBackgroundStateList
                else actionKeyStateList
            else -> null // use color filter
        }
        if (colorStateList == null) {
            drawable.colorFilter = getColorFilter(color)
            return
        }
        DrawableCompat.setTintMode(drawable, PorterDuff.Mode.MULTIPLY)
        DrawableCompat.setTintList(drawable, colorStateList)
    }

    override fun setColor(view: ImageView, color: ColorType) {
        view.colorFilter = getColorFilter(color)
    }

    override fun setBackground(view: View, color: ColorType) {
        if (view.background == null)
            view.setBackgroundColor(Color.WHITE) // set white to make the color filters word
        when (color) {
            CLEAR_CLIPBOARD_HISTORY_KEY -> setColor(view.background, SUGGESTION_BACKGROUND)
            EMOJI_CATEGORY_BACKGROUND -> view.setBackgroundColor(get(color))
            KEY_PREVIEW, MORE_KEYS_BACKGROUND -> view.background.colorFilter = adjustedBackgroundFilter
            FUNCTIONAL_KEY_BACKGROUND, KEY_BACKGROUND, BACKGROUND, SPACE_BAR_BACKGROUND, SUGGESTION_BACKGROUND -> setColor(view.background, color)
            KEYBOARD_BACKGROUND -> view.setBackgroundColor(Color.TRANSPARENT)
            MORE_SUGGESTIONS_BACKGROUND -> view.background.colorFilter = backgroundFilter
            EMOJI_BACKGROUND, CLIPBOARD_BACKGROUND, KEYBOARD_WRAPPER_BACKGROUND -> {
                if (keyboardBackground != null) view.background = keyboardBackground
                else view.background.colorFilter = backgroundFilter
            }
            else -> view.background.colorFilter = backgroundFilter
        }
    }

    private fun getColorFilter(color: ColorType): ColorFilter? = when (color) {
        EMOJI_CATEGORY_SELECTED, CLIPBOARD_PIN, SHIFT_KEY_ICON -> accentColorFilter
        REMOVE_SUGGESTION_ICON, CLEAR_CLIPBOARD_HISTORY_KEY, EMOJI_CATEGORY, KEY_TEXT, KEY_ICON,
            ONE_HANDED_MODE_BUTTON, TOOL_BAR_KEY, TOOL_BAR_EXPAND_KEY -> keyTextFilter
        KEY_PREVIEW -> adjustedBackgroundFilter
        ACTION_KEY_ICON -> actionKeyIconColorFilter
        else -> colorFilter(get(color)) // create color filter (not great for performance, so the frequently used filters should be stored)
    }

    override fun selectAndColorDrawable(attr: TypedArray, color: ColorType): Drawable {
        val drawable = when (color) {
            KEY_BACKGROUND, BACKGROUND, SUGGESTION_BACKGROUND, ACTION_KEY_MORE_KEYS_BACKGROUND, MORE_KEYS_BACKGROUND ->
                attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            FUNCTIONAL_KEY_BACKGROUND -> attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
            SPACE_BAR_BACKGROUND -> {
                if (hasKeyBorders) attr.getDrawable(R.styleable.KeyboardView_spacebarBackground)
                else attr.getDrawable(R.styleable.KeyboardView_spacebarNoBorderBackground)
            }
            ACTION_KEY_BACKGROUND -> {
                if (themeStyle == STYLE_HOLO && hasKeyBorders) // no borders has a very small pressed drawable otherwise
                    attr.getDrawable(R.styleable.KeyboardView_functionalKeyBackground)
                else
                    attr.getDrawable(R.styleable.KeyboardView_keyBackground)
            }
            else -> null // keyBackground
        }?.mutate() ?: attr.getDrawable(R.styleable.KeyboardView_keyBackground)?.mutate()!! // keyBackground always exists

        setColor(drawable, color)
        return drawable
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

enum class ColorType {
    ACTION_KEY_ICON,
    ACTION_KEY_BACKGROUND,
    ACTION_KEY_MORE_KEYS_BACKGROUND,
    BACKGROUND,
    CLEAR_CLIPBOARD_HISTORY_KEY,
    CLIPBOARD_PIN,
    CLIPBOARD_BACKGROUND,
    EMOJI_BACKGROUND,
    EMOJI_CATEGORY,
    EMOJI_CATEGORY_BACKGROUND,
    EMOJI_CATEGORY_SELECTED,
    FUNCTIONAL_KEY_BACKGROUND,
    GESTURE_TRAIL,
    GESTURE_PREVIEW,
    KEY_BACKGROUND,
    KEY_ICON,
    KEY_TEXT,
    KEY_HINT_TEXT,
    KEY_PREVIEW,
    KEYBOARD_BACKGROUND,
    KEYBOARD_WRAPPER_BACKGROUND,
    MORE_SUGGESTIONS_HINT,
    MORE_SUGGESTIONS_BACKGROUND,
    MORE_KEYS_BACKGROUND,
    NAVIGATION_BAR,
    SHIFT_KEY_ICON,
    SPACE_BAR_BACKGROUND,
    SPACE_BAR_TEXT,
    ONE_HANDED_MODE_BUTTON,
    REMOVE_SUGGESTION_ICON,
    SUGGESTED_WORD,
    SUGGESTION_AUTO_CORRECT,
    SUGGESTION_BACKGROUND,
    SUGGESTION_TYPED_WORD,
    SUGGESTION_VALID_WORD,
    TOOL_BAR_EXPAND_KEY,
    TOOL_BAR_EXPAND_KEY_BACKGROUND,
    TOOL_BAR_KEY,
    TOOL_BAR_KEY_ENABLED_BACKGROUND,
}
