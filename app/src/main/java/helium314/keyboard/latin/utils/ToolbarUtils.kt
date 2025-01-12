// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.annotation.SuppressLint
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Configuration
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.core.graphics.BlendModeColorFilterCompat
import androidx.core.graphics.BlendModeCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import helium314.keyboard.keyboard.internal.KeyboardIconsSet
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyCode.checkAndConvertCode
import helium314.keyboard.latin.R
import helium314.keyboard.latin.databinding.ReorderDialogItemBinding
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.ToolbarKey.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.EnumMap
import java.util.Locale

fun createToolbarKey(context: Context, iconsSet: KeyboardIconsSet, key: ToolbarKey): ImageButton {
    val button = ImageButton(context, null, R.attr.suggestionWordStyle)
    button.scaleType = ImageView.ScaleType.CENTER
    button.tag = key
    val contentDescriptionId = context.resources.getIdentifier(key.name.lowercase(), "string", context.packageName)
    if (contentDescriptionId != 0)
        button.contentDescription = context.getString(contentDescriptionId)
    button.isActivated = !when (key) {
        INCOGNITO -> Settings.readAlwaysIncognitoMode(DeviceProtectedUtils.getSharedPreferences(context))
        ONE_HANDED -> Settings.getInstance().current.mOneHandedModeEnabled
        SPLIT -> if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                     Settings.getInstance().current.mIsSplitKeyboardLandscapeEnabled
                 } else {
                     Settings.getInstance().current.mIsSplitKeyboardPortraitEnabled
                 }
        AUTOCORRECT -> Settings.getInstance().current.mAutoCorrectionEnabledPerUserSettings
        else -> true
    }
    button.setImageDrawable(iconsSet.getNewDrawable(key.name, context))
    return button
}

fun getCodeForToolbarKey(key: ToolbarKey) = Settings.getInstance().getCustomToolbarKeyCode(key) ?: when (key) {
    VOICE -> KeyCode.VOICE_INPUT
    CLIPBOARD -> KeyCode.CLIPBOARD
    NUMPAD -> KeyCode.NUMPAD
    UNDO -> KeyCode.UNDO
    REDO -> KeyCode.REDO
    SETTINGS -> KeyCode.SETTINGS
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_ALL
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_WORD
    COPY -> KeyCode.CLIPBOARD_COPY
    CUT -> KeyCode.CLIPBOARD_CUT
    PASTE -> KeyCode.CLIPBOARD_PASTE
    ONE_HANDED -> if (Settings.getInstance().current.mOneHandedModeEnabled) KeyCode.STOP_ONE_HANDED_MODE else KeyCode.START_ONE_HANDED_MODE
    INCOGNITO -> KeyCode.TOGGLE_INCOGNITO_MODE
    AUTOCORRECT -> KeyCode.TOGGLE_AUTOCORRECT
    CLEAR_CLIPBOARD -> KeyCode.CLIPBOARD_CLEAR_HISTORY
    CLOSE_HISTORY -> KeyCode.ALPHA
    EMOJI -> KeyCode.EMOJI
    LEFT -> KeyCode.ARROW_LEFT
    RIGHT -> KeyCode.ARROW_RIGHT
    UP -> KeyCode.ARROW_UP
    DOWN -> KeyCode.ARROW_DOWN
    WORD_LEFT -> KeyCode.WORD_LEFT
    WORD_RIGHT -> KeyCode.WORD_RIGHT
    PAGE_UP -> KeyCode.PAGE_UP
    PAGE_DOWN -> KeyCode.PAGE_DOWN
    FULL_LEFT -> KeyCode.MOVE_START_OF_LINE
    FULL_RIGHT -> KeyCode.MOVE_END_OF_LINE
    PAGE_START -> KeyCode.MOVE_START_OF_PAGE
    PAGE_END -> KeyCode.MOVE_END_OF_PAGE
    SPLIT -> KeyCode.SPLIT_LAYOUT
}

fun getCodeForToolbarKeyLongClick(key: ToolbarKey) = Settings.getInstance().getCustomToolbarLongpressCode(key) ?: when (key) {
    CLIPBOARD -> KeyCode.CLIPBOARD_PASTE
    UNDO -> KeyCode.REDO
    REDO -> KeyCode.UNDO
    SELECT_ALL -> KeyCode.CLIPBOARD_SELECT_WORD
    SELECT_WORD -> KeyCode.CLIPBOARD_SELECT_ALL
    COPY -> KeyCode.CLIPBOARD_CUT
    PASTE -> KeyCode.CLIPBOARD
    LEFT -> KeyCode.WORD_LEFT
    RIGHT -> KeyCode.WORD_RIGHT
    UP -> KeyCode.PAGE_UP
    DOWN -> KeyCode.PAGE_DOWN
    WORD_LEFT -> KeyCode.MOVE_START_OF_LINE
    WORD_RIGHT -> KeyCode.MOVE_END_OF_LINE
    PAGE_UP -> KeyCode.MOVE_START_OF_PAGE
    PAGE_DOWN -> KeyCode.MOVE_END_OF_PAGE
    else -> KeyCode.UNSPECIFIED
}

// names need to be aligned with resources strings (using lowercase of key.name)
enum class ToolbarKey {
    VOICE, CLIPBOARD, NUMPAD, UNDO, REDO, SETTINGS, SELECT_ALL, SELECT_WORD, COPY, CUT, PASTE, ONE_HANDED, SPLIT,
    INCOGNITO, AUTOCORRECT, CLEAR_CLIPBOARD, CLOSE_HISTORY, EMOJI, LEFT, RIGHT, UP, DOWN, WORD_LEFT, WORD_RIGHT,
    PAGE_UP, PAGE_DOWN, FULL_LEFT, FULL_RIGHT, PAGE_START, PAGE_END
}

val toolbarKeyStrings = entries.associateWithTo(EnumMap(ToolbarKey::class.java)) { it.toString().lowercase(Locale.US) }

val defaultToolbarPref by lazy {
    val default = listOf(SETTINGS, VOICE, CLIPBOARD, UNDO, REDO, SELECT_WORD, COPY, PASTE, LEFT, RIGHT)
    val others = entries.filterNot { it in default || it == CLOSE_HISTORY }
    default.joinToString(";") { "${it.name},true" } + ";" + others.joinToString(";") { "${it.name},false" }
}

val defaultPinnedToolbarPref = entries.filterNot { it == CLOSE_HISTORY }.joinToString(";") {
    "${it.name},false"
}

val defaultClipboardToolbarPref by lazy {
    val default = listOf(CLEAR_CLIPBOARD, UP, DOWN, LEFT, RIGHT, UNDO, CUT, COPY, PASTE, SELECT_WORD, CLOSE_HISTORY)
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

fun toolbarKeysCustomizer(context: Context) {
    val padding = ResourceUtils.toPx(8, context.resources)
    val ll = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(3 * padding, padding, padding, padding)
    }
    val dialog = AlertDialog.Builder(context)
        .setTitle(R.string.customize_toolbar_key_codes)
        .setView(ScrollView(context).apply { addView(ll) })
        .setPositiveButton(R.string.dialog_close, null)
        .create()
    val cf = BlendModeColorFilterCompat.createBlendModeColorFilterCompat(ContextCompat.getColor(context, R.color.foreground), BlendModeCompat.SRC_IN)
    ToolbarKey.entries.forEach { key ->
        val binding = ReorderDialogItemBinding.inflate(LayoutInflater.from(context), ll, true)
        binding.reorderItemIcon.setImageDrawable(KeyboardIconsSet.instance.getNewDrawable(key.name, context))
        binding.reorderItemIcon.colorFilter = cf
        binding.reorderItemIcon.isVisible = true
        binding.reorderItemName.text = key.name.lowercase().getStringResourceOrName("", context)
        binding.root.setOnClickListener {
            toolbarKeyCustomizer(context, key)
            dialog.dismiss()
        }
        binding.reorderItemSwitch.isGone = true
        binding.reorderItemDragIndicator.isGone = true
    }
    dialog.show()
}

@SuppressLint("SetTextI18n")
private fun toolbarKeyCustomizer(context: Context, key: ToolbarKey) {
    val layout = LayoutInflater.from(context).inflate(R.layout.toolbar_key_customizer, null)
    val prefs = DeviceProtectedUtils.getSharedPreferences(context)
    var keyCode: String? = null
    var longpressCode: String? = null
    val builder = AlertDialog.Builder(context)
        .setTitle(key.name.lowercase().getStringResourceOrName("", context))
        .setView(ScrollView(context).apply { addView(layout) })
        .setPositiveButton(android.R.string.ok) { _, _ ->
            val newKeyCode = runCatching { keyCode?.toIntOrNull()?.checkAndConvertCode() }.getOrNull()?.takeIf { it < Char.MAX_VALUE.code }
            val newLongpressCode = runCatching { longpressCode?.toIntOrNull()?.checkAndConvertCode() }.getOrNull()?.takeIf { it < Char.MAX_VALUE.code }
            if (newKeyCode != null)
                writeCustomKeyCodes(prefs, readCustomKeyCodes(prefs) + (key.name to newKeyCode))
            if (newLongpressCode != null)
                writeCustomLongpressCodes(prefs, readCustomLongpressCodes(prefs) + (key.name to newLongpressCode))
            toolbarKeysCustomizer(context)
        }
        .setNegativeButton(android.R.string.cancel) { _, _ -> toolbarKeysCustomizer(context) }
    if (readCustomKeyCodes(prefs).containsKey(key.name) || readCustomLongpressCodes(prefs).containsKey(key.name))
        builder.setNeutralButton(R.string.button_default) { _, _ ->
            val keys = readCustomKeyCodes(prefs).toMutableMap()
            keys.remove(key.name)
            prefs.edit().putString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, Json.encodeToString(keys)).apply()
            val longpressKeys = readCustomLongpressCodes(prefs).toMutableMap()
            longpressKeys.remove(key.name)
            prefs.edit().putString(Settings.PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES, Json.encodeToString(longpressKeys)).apply()
            toolbarKeysCustomizer(context)
        }
    val dialog = builder.create()

    fun checkOk() {
        val keyOk = keyCode == null
                || runCatching { keyCode?.toIntOrNull()?.let { it.checkAndConvertCode() <= Char.MAX_VALUE.code } }.getOrNull() ?: false
        val longPressOk = longpressCode == null
                || runCatching { longpressCode?.toIntOrNull()?.let { it.checkAndConvertCode() <= Char.MAX_VALUE.code } }.getOrNull() ?: false
        dialog.getButton(DialogInterface.BUTTON_POSITIVE)?.isEnabled = keyOk && longPressOk
    }
    layout.findViewById<EditText>(R.id.toolbar_key_code)?.apply {
        setText(getCodeForToolbarKey(key).toString())
        doAfterTextChanged {
            keyCode = it?.toString()
            checkOk()
        }
    }
    layout.findViewById<EditText>(R.id.toolbar_key_longpress_code)?.apply {
        setText(getCodeForToolbarKeyLongClick(key).toString())
        doAfterTextChanged {
            longpressCode = it?.toString()
            checkOk()
        }
    }

    dialog.show()
}

fun readCustomKeyCodes(prefs: SharedPreferences) = prefs.getString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, "")!!
        .split(";").associate {
            val code = runCatching { it.substringAfter(",").toIntOrNull()?.checkAndConvertCode() }.getOrNull()
            it.substringBefore(",") to code
        }

fun readCustomLongpressCodes(prefs: SharedPreferences) = prefs.getString(Settings.PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES, "")!!
    .split(";").associate {
        val code = runCatching { it.substringAfter(",").toIntOrNull()?.checkAndConvertCode() }.getOrNull()
        it.substringBefore(",") to code
    }

private fun writeCustomKeyCodes(prefs: SharedPreferences, codes: Map<String, Int?>) {
    val string = codes.mapNotNull { entry -> entry.value?.let { "${entry.key},$it" } }.joinToString(";")
    prefs.edit().putString(Settings.PREF_TOOLBAR_CUSTOM_KEY_CODES, string).apply()
}

private fun writeCustomLongpressCodes(prefs: SharedPreferences, codes: Map<String, Int?>) {
    val string = codes.mapNotNull { entry -> entry.value?.let { "${entry.key},$it" } }.joinToString(";")
    prefs.edit().putString(Settings.PREF_TOOLBAR_CUSTOM_LONGPRESS_CODES, string).apply()
}
