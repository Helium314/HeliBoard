// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import helium314.keyboard.keyboard.Key
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.KeyboardLayoutSet
import helium314.keyboard.keyboard.KeyboardSwitcher
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.JsonKeyboardParser
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.SimpleKeyboardParser
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.settings.Settings
import java.io.File
import java.io.IOException
import java.math.BigInteger

fun loadCustomLayout(uri: Uri?, languageTag: String, context: Context, onAdded: (String) -> Unit) {
    if (uri == null)
        return infoDialog(context, context.getString(R.string.layout_error, "layout file not found"))
    val layoutContent: String
    try {
        val tmpFile = File(context.filesDir.absolutePath + File.separator + "tmpfile")
        FileUtils.copyContentUriToNewFile(uri, context, tmpFile)
        layoutContent = tmpFile.readText()
        tmpFile.delete()
    } catch (e: IOException) {
        return infoDialog(context, context.getString(R.string.layout_error, "cannot read layout file"))
    }

    var name = ""
    context.contentResolver.query(uri, null, null, null, null).use {
        if (it != null && it.moveToFirst()) {
            val idx = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx >= 0)
                name = it.getString(idx).substringBeforeLast(".")
        }
    }
    loadCustomLayout(layoutContent, name, languageTag, context, onAdded)
}

fun loadCustomLayout(layoutContent: String, layoutName: String, languageTag: String, context: Context, onAdded: (String) -> Unit) {
    var name = layoutName
    val isJson = checkLayout(layoutContent, context)
        ?: return infoDialog(context, context.getString(R.string.layout_error, "invalid layout file, ${Log.getLog(10).lastOrNull { it.tag == TAG }?.message}"))

    AlertDialog.Builder(context)
        .setTitle(R.string.title_layout_name_select)
        .setView(EditText(context).apply {
            setText(name)
            doAfterTextChanged { name = it.toString() }
            val padding = ResourceUtils.toPx(8, context.resources)
            setPadding(3 * padding, padding, 3 * padding, padding)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_NORMAL
        })
        .setPositiveButton(android.R.string.ok) { _, _ ->
            // name must be encoded to avoid issues with validity of subtype extra string or file name
            name = "$CUSTOM_LAYOUT_PREFIX${languageTag}.${encodeBase36(name)}.${if (isJson) "json" else "txt"}"
            val file = getLayoutFile(name, context)
            if (file.exists())
                file.delete()
            file.parentFile?.mkdir()
            file.writeText(layoutContent)
            onAdded(name)
        }
        .show()
}

private fun checkLayout(layoutContent: String, context: Context): Boolean? {
    val params = KeyboardParams()
    params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
    params.mPopupKeyTypes.add(POPUP_KEYS_LAYOUT)
    addLocaleKeyTextsToParams(context, params, POPUP_KEYS_NORMAL)
    try {
        val keys = JsonKeyboardParser(params, context).parseLayoutString(layoutContent)
        if (!checkKeys(keys))
            return null
        return true
    } catch (e: Exception) { Log.w(TAG, "error parsing custom json layout", e) }
    try {
        val keys = SimpleKeyboardParser(params, context).parseLayoutString(layoutContent)
        if (!checkKeys(keys))
            return null
        return false
    } catch (e: Exception) { Log.w(TAG, "error parsing custom simple layout", e) }
    return null
}

private fun checkKeys(keys: List<List<Key.KeyParams>>): Boolean {
    if (keys.isEmpty() || keys.any { it.isEmpty() }) {
        Log.w(TAG, "empty rows")
        return false
    }
    if (keys.size > 8) {
        Log.w(TAG, "too many rows")
        return false
    }
    if (keys.any { it.size > 20 }) {
        Log.w(TAG, "too many keys in one row")
        return false
    }
    if (keys.any { it.any { ((it.mLabel?.length ?: 0) > 6) } }) {
        Log.w(TAG, "too long text on key")
        return false
    }
    if (keys.any { it.any { (it.mPopupKeys?.size ?: 0) > 20 } }) {
        Log.w(TAG, "too many popup keys on a key")
        return false
    }
    if (keys.any { it.any { it.mPopupKeys?.any { (it.mLabel?.length ?: 0) > 10 } == true } }) {
        Log.w(TAG, "too long text on popup key")
        return false
    }
    return true
}

fun getLayoutFile(layoutName: String, context: Context) =
    File(Settings.getLayoutsDir(context), layoutName)

// undo the name changes in loadCustomLayout when clicking ok
fun getLayoutDisplayName(layoutName: String) =
    try {
        decodeBase36(layoutName.substringAfter(CUSTOM_LAYOUT_PREFIX).substringAfter(".").substringBeforeLast("."))
    } catch (_: NumberFormatException) {
        layoutName
    }

fun removeCustomLayoutFile(layoutName: String, context: Context) {
    getLayoutFile(layoutName, context).delete()
}

fun editCustomLayout(layoutName: String, context: Context, startContent: String? = null, displayName: CharSequence? = null) {
    val file = getLayoutFile(layoutName, context)
    val editText = EditText(context).apply {
        setText(startContent ?: file.readText())
    }
    val builder = AlertDialog.Builder(context)
        .setTitle(getLayoutDisplayName(layoutName))
        .setView(editText)
        .setPositiveButton(R.string.save) { _, _ ->
            val content = editText.text.toString()
            val isJson = checkLayout(content, context)
            if (isJson == null) {
                editCustomLayout(layoutName, context, content)
                // todo: this actually always returns the "simple layout" error, even on a json layout with a single small error
                //  -> check in inverse expected order (set depending in current file name)
                infoDialog(context, context.getString(R.string.layout_error, Log.getLog(10).lastOrNull { it.tag == TAG }?.message))
            } else {
                val wasJson = file.name.substringAfterLast(".") == "json"
                file.parentFile?.mkdir()
                file.writeText(content)
                if (isJson != wasJson) // unlikely to be needed, but better be safe
                    file.renameTo(File(file.absolutePath.substringBeforeLast(".") + if (isJson) "json" else "txt"))
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(context)
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
    if (displayName != null) {
        if (file.exists()) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                confirmDialog(context, context.getString(R.string.delete_layout, displayName), context.getString(R.string.delete)) {
                    file.delete()
                    KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(context)
                }
            }
        }
        builder.setTitle(displayName)
    }
    builder.show()
}

private fun encodeBase36(string: String): String = BigInteger(string.toByteArray()).toString(36)

private fun decodeBase36(string: String) = BigInteger(string, 36).toByteArray().decodeToString()

// this goes into prefs and file names, so do not change!
const val CUSTOM_LAYOUT_PREFIX = "custom."
private const val TAG = "CustomLayoutUtils"
