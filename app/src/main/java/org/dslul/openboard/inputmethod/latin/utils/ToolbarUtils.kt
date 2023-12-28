package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.content.res.TypedArray
import android.widget.ImageButton
import android.widget.ImageView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants.*
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.utils.ToolbarKey.*
import java.util.EnumSet

fun createToolbarKey(context: Context, keyboardAttr: TypedArray, key: ToolbarKey): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER
    button.tag = key
    val icon = keyboardAttr.getDrawable(getStyleableIconId(key))
    if (key == LEFT || key == RIGHT || key == UP || key == DOWN) {
        // arrows look a little awkward when not scaled
        button.scaleX = 1.2f
        button.scaleY = 1.2f
    }
    button.setImageDrawable(icon)
    return button
}

fun getCodeForToolbarKey(key: ToolbarKey) = when (key) {
    VOICE -> CODE_SHORTCUT
    SETTINGS -> CODE_SETTINGS
    CLIPBOARD -> CODE_CLIPBOARD
    SELECT_ALL -> CODE_SELECT_ALL
    COPY -> CODE_COPY
    ONE_HANDED -> if (Settings.getInstance().current.mOneHandedModeEnabled) CODE_START_ONE_HANDED_MODE else CODE_STOP_ONE_HANDED_MODE
    LEFT -> CODE_LEFT
    RIGHT -> CODE_RIGHT
    UP -> CODE_UP
    DOWN -> CODE_DOWN
    UNDO -> CODE_UNDO
    REDO -> CODE_REDO
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
    CLEAR_CLIPBOARD -> R.styleable.Keyboard_iconClearClipboardKey
}

enum class ToolbarKey {
    VOICE, CLIPBOARD, CLEAR_CLIPBOARD, SETTINGS, SELECT_ALL, COPY, ONE_HANDED, LEFT, RIGHT, UP, DOWN, UNDO, REDO
}

fun toToolbarKeyString(keys: Collection<ToolbarKey>) = keys.joinToString(";") { it.name }
