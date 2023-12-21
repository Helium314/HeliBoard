package org.dslul.openboard.inputmethod.latin.utils

import android.content.Context
import android.content.res.TypedArray
import android.widget.ImageButton
import android.widget.ImageView
import org.dslul.openboard.inputmethod.latin.R
import org.dslul.openboard.inputmethod.latin.common.Constants.*
import org.dslul.openboard.inputmethod.latin.settings.Settings

fun createToolbarKey(context: Context, keyboardAttr: TypedArray, tag: String): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER
    button.tag = tag
    val icon = keyboardAttr.getDrawable(getStyleableIconId(tag))
    if (tag == TAG_LEFT || tag == TAG_RIGHT || tag == TAG_UP || tag == TAG_DOWN) {
        // arrows look a little awkward when not scaled
        button.scaleX = 1.2f
        button.scaleY = 1.2f
    }
    button.setImageDrawable(icon)
    return button
}

fun getCodeForTag(tag: String) = when (tag) {
    TAG_VOICE -> CODE_SHORTCUT
    TAG_SETTINGS -> CODE_SETTINGS
    TAG_CLIPBOARD -> CODE_CLIPBOARD
    TAG_SELECT_ALL -> CODE_SELECT_ALL
    TAG_COPY -> CODE_COPY
    TAG_ONE_HANDED -> if (Settings.getInstance().current.mOneHandedModeEnabled) CODE_START_ONE_HANDED_MODE else CODE_STOP_ONE_HANDED_MODE
    TAG_LEFT -> CODE_LEFT
    TAG_RIGHT -> CODE_RIGHT
    TAG_UP -> CODE_UP
    TAG_DOWN -> CODE_DOWN
    TAG_UNDO -> CODE_UNDO
    TAG_REDO -> CODE_REDO
    TAG_CLEAR_CLIPBOARD -> null // not managed via code input
    else -> null
}

private fun getStyleableIconId(tag: String) = when (tag) {
    TAG_VOICE -> R.styleable.Keyboard_iconShortcutKey
    TAG_SETTINGS -> R.styleable.Keyboard_iconSettingsKey
    TAG_CLIPBOARD -> R.styleable.Keyboard_iconClipboardNormalKey
    TAG_SELECT_ALL -> R.styleable.Keyboard_iconSelectAll
    TAG_COPY -> R.styleable.Keyboard_iconCopyKey
    TAG_ONE_HANDED -> R.styleable.Keyboard_iconStartOneHandedMode
    TAG_LEFT -> R.styleable.Keyboard_iconArrowLeft
    TAG_RIGHT -> R.styleable.Keyboard_iconArrowRight
    TAG_UP -> R.styleable.Keyboard_iconArrowUp
    TAG_DOWN -> R.styleable.Keyboard_iconArrowDown
    TAG_UNDO -> R.styleable.Keyboard_iconUndo
    TAG_REDO -> R.styleable.Keyboard_iconRedo
    TAG_CLEAR_CLIPBOARD -> R.styleable.Keyboard_iconClearClipboardKey
    else -> throw IllegalArgumentException("no styleable id for $tag")
}

const val TAG_VOICE = "voice_key"
const val TAG_CLIPBOARD = "clipboard_key"
const val TAG_CLEAR_CLIPBOARD = "clear_clipboard"
const val TAG_SETTINGS = "settings_key"
const val TAG_SELECT_ALL = "select_all_key"
const val TAG_COPY = "copy_key"
const val TAG_ONE_HANDED = "one_handed_key"
const val TAG_LEFT = "left_key"
const val TAG_RIGHT = "right_key"
const val TAG_UP = "up_key"
const val TAG_DOWN = "down_key"
const val TAG_REDO = "undo"
const val TAG_UNDO = "redo"
