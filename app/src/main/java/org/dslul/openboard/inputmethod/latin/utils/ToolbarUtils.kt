// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.edit
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants.*
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.ToolbarKey.*
import java.util.EnumSet

fun createToolbarKey(context: Context, keyboardAttr: TypedArray, key: ToolbarKey): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER
    button.tag = key
    val contentDescriptionId = context.resources.getIdentifier(key.name.lowercase(), "string", context.packageName)
    if (contentDescriptionId != 0)
        button.contentDescription = context.getString(contentDescriptionId)
    if (key == LEFT || key == RIGHT || key == UP || key == DOWN) {
        // arrows look a little awkward when not scaled
        button.scaleX = 1.2f
        button.scaleY = 1.2f
    }
    button.isActivated = when (key) {
        INCOGNITO -> Settings.readAlwaysIncognitoMode(DeviceProtectedUtils.getSharedPreferences(context))
        ONE_HANDED -> Settings.getInstance().current.mOneHandedModeEnabled
        AUTOCORRECT -> Settings.getInstance().current.mAutoCorrectionEnabledPerUserSettings
        else -> true
    }
    button.setImageDrawable(keyboardAttr.getDrawable(getStyleableIconId(key)))
    return button
}

fun getCodeForToolbarKey(key: ToolbarKey) = when (key) {
    VOICE -> CODE_SHORTCUT
    SETTINGS -> CODE_SETTINGS
    CLIPBOARD -> CODE_CLIPBOARD
    SELECT_ALL -> CODE_SELECT_ALL
    COPY -> CODE_COPY
    ONE_HANDED -> if (Settings.getInstance().current.mOneHandedModeEnabled) CODE_STOP_ONE_HANDED_MODE else CODE_START_ONE_HANDED_MODE
    LEFT -> CODE_LEFT
    RIGHT -> CODE_RIGHT
    UP -> CODE_UP
    DOWN -> CODE_DOWN
    UNDO -> CODE_UNDO
    REDO -> CODE_REDO
    INCOGNITO -> CODE_TOGGLE_INCOGNITO
    AUTOCORRECT -> CODE_TOGGLE_AUTOCORRECT
    CLEAR_CLIPBOARD -> null // not managed via code input
}

private fun getStyleableIconId(key: ToolbarKey) = when (key) {
    VOICE -> R.styleable.Keyboard_iconShortcutKey
    SETTINGS -> R.styleable.Keyboard_iconSettingsKey
    CLIPBOARD -> R.styleable.Keyboard_iconClipboardNormalKey
    SELECT_ALL -> R.styleable.Keyboard_iconSelectAll
    COPY -> R.styleable.Keyboard_iconCopyKey
    ONE_HANDED -> R.styleable.Keyboard_iconStartOneHandedMode
    LEFT -> R.styleable.Keyboard_iconArrowLeft
    RIGHT -> R.styleable.Keyboard_iconArrowRight
    UP -> R.styleable.Keyboard_iconArrowUp
    DOWN -> R.styleable.Keyboard_iconArrowDown
    UNDO -> R.styleable.Keyboard_iconUndo
    REDO -> R.styleable.Keyboard_iconRedo
    INCOGNITO -> R.styleable.Keyboard_iconIncognitoKey
    AUTOCORRECT -> R.styleable.Keyboard_iconLanguageSwitchKey
    CLEAR_CLIPBOARD -> R.styleable.Keyboard_iconClearClipboardKey
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, UNDO, REDO, SETTINGS, SELECT_ALL, COPY, ONE_HANDED, LEFT, RIGHT, UP, DOWN, INCOGNITO, AUTOCORRECT, CLEAR_CLIPBOARD
}

fun toToolbarKeyString(keys: Collection<ToolbarKey>) = keys.joinToString(";") { it.name }

val defaultToolbarPref = entries.filterNot { it == CLEAR_CLIPBOARD }.joinToString(";") {
    when (it) {
        INCOGNITO, AUTOCORRECT, UP, DOWN, ONE_HANDED -> "${it.name},false"
        else -> "${it.name},true"
    }
}

/** add missing keys, typically because a new key has been added */
fun upgradeToolbarPref(prefs: SharedPreferences) {
    val list = prefs.getString(Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)!!.split(";").toMutableList()
    val splitDefault = defaultToolbarPref.split(";")
    if (list.size == splitDefault.size) return
    splitDefault.forEach { entry ->
        val keyWithComma = entry.substringBefore(",") + ","
        if (list.none { it.startsWith(keyWithComma) })
            list.add("${keyWithComma}true")
    }
    // likely not needed, but better prepare for possibility of key removal
    list.removeAll {
        try {
            ToolbarKey.valueOf(it.substringBefore(","))
            false
        } catch (_: IllegalArgumentException) {
            true
        }
    }
    prefs.edit { putString(Settings.PREF_TOOLBAR_KEYS, list.joinToString(";")) }
}

fun getEnabledToolbarKeys(prefs: SharedPreferences): List<ToolbarKey> {
    val string = prefs.getString(Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)!!
    return string.split(";").mapNotNull {
        val split = it.split(",")
        if (split.last() == "true") {
            try {
                ToolbarKey.valueOf(split.first())
            } catch (_: IllegalArgumentException) {
                null
            }
        } else null
    }
}
