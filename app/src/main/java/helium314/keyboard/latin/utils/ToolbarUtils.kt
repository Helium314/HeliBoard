// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.edit
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey.*

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
    button.isActivated = !when (key) {
        INCOGNITO -> Settings.readAlwaysIncognitoMode(DeviceProtectedUtils.getSharedPreferences(context))
        ONE_HANDED -> Settings.getInstance().current.mOneHandedModeEnabled
        AUTOCORRECT -> Settings.getInstance().current.mAutoCorrectionEnabledPerUserSettings
        else -> true
    }
    button.setImageDrawable(keyboardAttr.getDrawable(getStyleableIconId(key))?.mutate())
    return button
}

fun getCodeForToolbarKey(key: ToolbarKey) = when (key) {
    VOICE -> KeyCode.VOICE_INPUT
    SETTINGS -> KeyCode.SETTINGS
    CLIPBOARD -> KeyCode.CLIPBOARD
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_ALL
    COPY -> KeyCode.CLIPBOARD_COPY
    ONE_HANDED -> if (Settings.getInstance().current.mOneHandedModeEnabled) KeyCode.STOP_ONE_HANDED_MODE else KeyCode.START_ONE_HANDED_MODE
    LEFT -> KeyCode.ARROW_LEFT
    RIGHT -> KeyCode.ARROW_RIGHT
    UP -> KeyCode.ARROW_UP
    DOWN -> KeyCode.ARROW_DOWN
    UNDO -> KeyCode.UNDO
    REDO -> KeyCode.REDO
    INCOGNITO -> KeyCode.TOGGLE_INCOGNITO_MODE
    AUTOCORRECT -> KeyCode.TOGGLE_AUTOCORRECT
    FULL_LEFT -> KeyCode.MOVE_START_OF_LINE
    FULL_RIGHT -> KeyCode.MOVE_END_OF_LINE
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_WORD
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
    FULL_LEFT -> R.styleable.Keyboard_iconFullLeft
    FULL_RIGHT -> R.styleable.Keyboard_iconFullRight
    SELECT_WORD -> R.styleable.Keyboard_iconSelectWord
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, UNDO, REDO, SETTINGS, SELECT_ALL, SELECT_WORD, COPY, ONE_HANDED, LEFT, RIGHT, UP, DOWN,
    FULL_LEFT, FULL_RIGHT, INCOGNITO, AUTOCORRECT, CLEAR_CLIPBOARD
}

fun toToolbarKeyString(keys: Collection<ToolbarKey>) = keys.joinToString(";") { it.name }

val defaultToolbarPref = entries.filterNot { it == CLEAR_CLIPBOARD }.joinToString(";") {
    when (it) {
        INCOGNITO, AUTOCORRECT, UP, DOWN, ONE_HANDED, FULL_LEFT, FULL_RIGHT -> "${it.name},false"
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
