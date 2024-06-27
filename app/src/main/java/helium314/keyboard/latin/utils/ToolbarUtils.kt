// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.content.res.TypedArray
import android.graphics.drawable.Drawable
import android.widget.ImageButton
import android.widget.ImageView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.edit
import helium314.keyboard.keyboard.KeyboardTheme
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey.*
import java.util.EnumMap
import java.util.Locale

fun createToolbarKey(context: Context, keyboardAttr: TypedArray, key: ToolbarKey): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER
    button.tag = key
    val contentDescriptionId = context.resources.getIdentifier(key.name.lowercase(), "string", context.packageName)
    if (contentDescriptionId != 0)
        button.contentDescription = context.getString(contentDescriptionId)
    if (key == LEFT || key == RIGHT || key == UP || key == DOWN) {
        // arrows look a little awkward when not scaled
        // todo: this should apply to the main keyboard as well
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
    CUT -> KeyCode.CLIPBOARD_CUT
    ONE_HANDED -> if (Settings.getInstance().current.mOneHandedModeEnabled) KeyCode.STOP_ONE_HANDED_MODE else KeyCode.START_ONE_HANDED_MODE
    LEFT -> KeyCode.ARROW_LEFT
    RIGHT -> KeyCode.ARROW_RIGHT
    UP -> KeyCode.ARROW_UP
    DOWN -> KeyCode.ARROW_DOWN
    WORD_LEFT -> KeyCode.WORD_LEFT
    WORD_RIGHT -> KeyCode.WORD_RIGHT
    PAGE_UP -> KeyCode.PAGE_UP
    PAGE_DOWN -> KeyCode.PAGE_DOWN
    UNDO -> KeyCode.UNDO
    REDO -> KeyCode.REDO
    INCOGNITO -> KeyCode.TOGGLE_INCOGNITO_MODE
    AUTOCORRECT -> KeyCode.TOGGLE_AUTOCORRECT
    FULL_LEFT -> KeyCode.MOVE_START_OF_LINE
    FULL_RIGHT -> KeyCode.MOVE_END_OF_LINE
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_WORD
    CLEAR_CLIPBOARD -> KeyCode.CLIPBOARD_CLEAR_HISTORY
    CLOSE_HISTORY -> KeyCode.ALPHA
    EMOJI -> KeyCode.EMOJI
}

fun getCodeForToolbarKeyLongClick(key: ToolbarKey) = when (key) {
    LEFT -> KeyCode.WORD_LEFT
    RIGHT -> KeyCode.WORD_RIGHT
    UP -> KeyCode.PAGE_UP
    DOWN -> KeyCode.PAGE_DOWN
    WORD_LEFT -> KeyCode.MOVE_START_OF_LINE
    WORD_RIGHT -> KeyCode.MOVE_END_OF_LINE
    PAGE_UP -> KeyCode.MOVE_START_OF_PAGE
    PAGE_DOWN -> KeyCode.MOVE_END_OF_PAGE
    UNDO -> KeyCode.REDO
    REDO -> KeyCode.UNDO
    COPY -> KeyCode.CLIPBOARD_COPY_ALL
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_ALL
    CLIPBOARD -> KeyCode.CLIPBOARD_PASTE
    else -> KeyCode.UNSPECIFIED
}

fun getStyleableIconId(key: ToolbarKey) = when (key) {
    VOICE -> R.styleable.Keyboard_iconShortcutKey
    SETTINGS -> R.styleable.Keyboard_iconSettingsKey
    CLIPBOARD -> R.styleable.Keyboard_iconClipboardNormalKey
    SELECT_ALL -> R.styleable.Keyboard_iconSelectAll
    COPY -> R.styleable.Keyboard_iconCopyKey
    CUT -> R.styleable.Keyboard_iconCutKey
    ONE_HANDED -> R.styleable.Keyboard_iconStartOneHandedMode
    LEFT -> R.styleable.Keyboard_iconArrowLeft
    RIGHT -> R.styleable.Keyboard_iconArrowRight
    UP -> R.styleable.Keyboard_iconArrowUp
    DOWN -> R.styleable.Keyboard_iconArrowDown
    WORD_LEFT -> R.styleable.Keyboard_iconWordLeft
    WORD_RIGHT -> R.styleable.Keyboard_iconWordRight
    PAGE_UP -> R.styleable.Keyboard_iconPageUp
    PAGE_DOWN -> R.styleable.Keyboard_iconPageDown
    UNDO -> R.styleable.Keyboard_iconUndo
    REDO -> R.styleable.Keyboard_iconRedo
    INCOGNITO -> R.styleable.Keyboard_iconIncognitoKey
    AUTOCORRECT -> R.styleable.Keyboard_iconAutoCorrect
    CLEAR_CLIPBOARD -> R.styleable.Keyboard_iconClearClipboardKey
    FULL_LEFT -> R.styleable.Keyboard_iconFullLeft
    FULL_RIGHT -> R.styleable.Keyboard_iconFullRight
    SELECT_WORD -> R.styleable.Keyboard_iconSelectWord
    CLOSE_HISTORY -> R.styleable.Keyboard_iconClose
    EMOJI -> R.styleable.Keyboard_iconEmojiNormalKey
}

fun getToolbarIconByName(name: String, context: Context): Drawable? {
    val key = entries.firstOrNull { it.name == name } ?: return null
    val themeContext = ContextThemeWrapper(context, KeyboardTheme.getKeyboardTheme(context).mStyleId)
    val attrs = themeContext.obtainStyledAttributes(null, R.styleable.Keyboard)
    val icon = attrs.getDrawable(getStyleableIconId(key))?.mutate()
    attrs.recycle()
    return icon
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, UNDO, REDO, SETTINGS, SELECT_ALL, SELECT_WORD, COPY, CUT, ONE_HANDED, LEFT, RIGHT, UP, DOWN,
    WORD_LEFT, WORD_RIGHT, PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, INCOGNITO, AUTOCORRECT, CLEAR_CLIPBOARD,
    CLOSE_HISTORY, EMOJI
}

val toolbarKeyStrings = entries.associateWithTo(EnumMap(ToolbarKey::class.java)) { it.toString().lowercase(Locale.US) }

val defaultToolbarPref = entries.filterNot { it == CLOSE_HISTORY }.joinToString(";") {
    when (it) {
        INCOGNITO, AUTOCORRECT, UP, DOWN, ONE_HANDED, WORD_LEFT, WORD_RIGHT, PAGE_UP, PAGE_DOWN,
        FULL_LEFT, FULL_RIGHT, CUT, CLEAR_CLIPBOARD, EMOJI -> "${it.name},false"
        else -> "${it.name},true"
    }
}

val defaultPinnedToolbarPref = entries.filterNot { it == CLOSE_HISTORY }.joinToString(";") {
    "${it.name},false"
}

val defaultClipboardToolbarPref by lazy {
    val default = listOf(ONE_HANDED, UNDO, UP, DOWN, LEFT, RIGHT, CLEAR_CLIPBOARD, COPY, CUT, SELECT_WORD, CLOSE_HISTORY)
    val others = entries.filterNot { it in default }
    default.joinToString(";") { "${it.name},true" } + ";" + others.joinToString(";") { "${it.name},false" }
}

/** add missing keys, typically because a new key has been added */
fun upgradeToolbarPrefs(prefs: SharedPreferences) {
    upgradeToolbarPref(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)
    upgradeToolbarPref(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)
}

private fun upgradeToolbarPref(prefs: SharedPreferences, pref: String, default: String) {
    if (!prefs.contains(pref)) return
    val list = prefs.getString(pref, default)!!.split(";").toMutableList()
    val splitDefault = defaultToolbarPref.split(";")
    splitDefault.forEach { entry ->
        val keyWithComma = entry.substringBefore(",") + ","
        if (list.none { it.startsWith(keyWithComma) })
            list.add("${keyWithComma}false")
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
    prefs.edit { putString(pref, list.joinToString(";")) }
}

fun getEnabledToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_TOOLBAR_KEYS, defaultToolbarPref)

fun getPinnedToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)

fun getEnabledClipboardToolbarKeys(prefs: SharedPreferences) = getEnabledToolbarKeys(prefs, Settings.PREF_CLIPBOARD_TOOLBAR_KEYS, defaultClipboardToolbarPref)

fun addPinnedKey(prefs: SharedPreferences, key: ToolbarKey) {
    // remove the existing version of this key and add the enabled one after the last currently enabled key
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)!!
    val keys = string.split(";").toMutableList()
    keys.removeAll { it.startsWith(key.name + ",") }
    val lastEnabledIndex = keys.indexOfLast { it.endsWith("true") }
    keys.add(lastEnabledIndex + 1, key.name + ",true")
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, keys.joinToString(";")) }
}

fun removePinnedKey(prefs: SharedPreferences, key: ToolbarKey) {
    // just set it to disabled
    val string = prefs.getString(Settings.PREF_PINNED_TOOLBAR_KEYS, defaultPinnedToolbarPref)!!
    val result = string.split(";").joinToString(";") {
        if (it.startsWith(key.name + ","))
            key.name + ",false"
        else it
    }
    prefs.edit { putString(Settings.PREF_PINNED_TOOLBAR_KEYS, result) }
}

private fun getEnabledToolbarKeys(prefs: SharedPreferences, pref: String, default: String): List<ToolbarKey> {
    val string = prefs.getString(pref, default)!!
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
