// SPDX-License-Identifier: GPL-3.0-only
package com.oscar.aikeyboard.latin.utils

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.text.InputType
import android.view.inputmethod.InputMethodSubtype
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import com.oscar.aikeyboard.R
import com.oscar.aikeyboard.keyboard.Key
import com.oscar.aikeyboard.keyboard.KeyboardId
import com.oscar.aikeyboard.keyboard.KeyboardLayoutSet
import com.oscar.aikeyboard.keyboard.KeyboardSwitcher
import com.oscar.aikeyboard.keyboard.internal.KeyboardParams
import com.oscar.aikeyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import com.oscar.aikeyboard.keyboard.internal.keyboard_parser.RawKeyboardParser
import com.oscar.aikeyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import com.oscar.aikeyboard.latin.common.Constants
import com.oscar.aikeyboard.latin.common.FileUtils
import kotlinx.serialization.SerializationException
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
            val file = getCustomLayoutFile(name, context)
            if (file.exists())
                file.delete()
            file.parentFile?.mkdir()
            file.writeText(layoutContent)
            onAdded(name)
        }
        .show()
}

/** @return true if json, false if simple, null if invalid */
private fun checkLayout(layoutContent: String, context: Context): Boolean? {
    val params = KeyboardParams()
    params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
    params.mPopupKeyTypes.add(POPUP_KEYS_LAYOUT)
    addLocaleKeyTextsToParams(context, params, POPUP_KEYS_NORMAL)
    try {
        val keys = RawKeyboardParser.parseJsonString(layoutContent).map { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        if (!checkKeys(keys))
            return null
        return true
    } catch (e: SerializationException) {
        Log.w(TAG, "json parsing error", e)
    } catch (e: Exception) {
        Log.w(TAG, "json layout parsed, but considered invalid", e)
        return null
    }
    try {
        val keys = RawKeyboardParser.parseSimpleString(layoutContent).map { row -> row.map { it.toKeyParams(params) } }
        if (!checkKeys(keys))
            return null
        return false
    } catch (e: Exception) { Log.w(TAG, "error parsing custom simple layout", e) }
    if (layoutContent.trimStart().startsWith("[") && layoutContent.trimEnd().endsWith("]")) {
        // layout can't be loaded, assume it's json -> load json layout again because the error message shown to the user is from the most recent error
        try {
            RawKeyboardParser.parseJsonString(layoutContent).map { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
        } catch (e: Exception) { Log.w(TAG, "json parsing error", e) }
    }
    return null
}

fun checkKeys(keys: List<List<Key.KeyParams>>): Boolean {
    if (keys.isEmpty() || keys.any { it.isEmpty() }) {
        Log.w(TAG, "empty rows")
        return false
    }
    if (keys.size > 8) {
        Log.w(TAG, "too many rows")
        return false
    }
    if (keys.any { row -> row.size > 20 }) {
        Log.w(TAG, "too many keys in one row")
        return false
    }
    if (keys.any { row -> row.any {
            if ((it.mLabel?.length ?: 0) > 20) {
                Log.w(TAG, "too long text on key: ${it.mLabel}")
                true
            } else false
    } }) {
        return false
    }
    if (keys.any { row -> row.any {
        if ((it.mPopupKeys?.size ?: 0) > 20) {
            Log.w(TAG, "too many popup keys on key ${it.mLabel}")
            true
        } else false
    } }) {
        return false
    }
    if (keys.any { row -> row.any { true == it.mPopupKeys?.any { popupKey ->
        if ((popupKey.mLabel?.length ?: 0) > 10) {
            Log.w(TAG, "too long text on popup key: ${popupKey.mLabel}")
            true
        } else false
    } } }) {
        return false
    }
    return true
}

/** don't rename or delete the file without calling [onCustomLayoutFileListChanged] */
fun getCustomLayoutFile(layoutName: String, context: Context) =
    File(getCustomLayoutsDir(context), layoutName)

// cache to avoid frequently listing files
/** don't rename or delete files without calling [onCustomLayoutFileListChanged] */
fun getCustomLayoutFiles(context: Context): List<File> {
    customLayouts?.let { return it }
    val layouts = getCustomLayoutsDir(context).listFiles()?.toList() ?: emptyList()
    customLayouts = layouts
    return layouts
}

fun onCustomLayoutFileListChanged() {
    customLayouts = null
}

private fun getCustomLayoutsDir(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), "layouts")

// undo the name changes in loadCustomLayout when clicking ok
fun getLayoutDisplayName(layoutName: String) =
    try {
        decodeBase36(layoutName.substringAfter(CUSTOM_LAYOUT_PREFIX).substringAfter(".").substringBeforeLast("."))
    } catch (_: NumberFormatException) {
        layoutName
    }

fun removeCustomLayoutFile(layoutName: String, context: Context) {
    getCustomLayoutFile(layoutName, context).delete()
}

fun editCustomLayout(layoutName: String, context: Context, startContent: String? = null, displayName: CharSequence? = null) {
    val file = getCustomLayoutFile(layoutName, context)
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
                infoDialog(context, context.getString(R.string.layout_error, Log.getLog(10).lastOrNull { it.tag == TAG }?.message))
            } else {
                val wasJson = file.name.substringAfterLast(".") == "json"
                file.parentFile?.mkdir()
                file.writeText(content)
                if (isJson != wasJson) // unlikely to be needed, but better be safe
                    file.renameTo(File(file.absolutePath.substringBeforeLast(".") + "." + if (isJson) "json" else "txt"))
                onCustomLayoutFileListChanged()
                KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(context)
            }
        }
        .setNegativeButton(android.R.string.cancel, null)
    if (displayName != null) {
        if (file.exists()) {
            builder.setNeutralButton(R.string.delete) { _, _ ->
                confirmDialog(context, context.getString(R.string.delete_layout, displayName), context.getString(
                    R.string.delete)) {
                    file.delete()
                    onCustomLayoutFileListChanged()
                    KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(context)
                }
            }
        }
        builder.setTitle(displayName)
    }
    builder.show()
}

fun hasCustomFunctionalLayout(subtype: InputMethodSubtype, context: Context): Boolean {
    val anyCustomFunctionalLayout = getCustomFunctionalLayoutName(KeyboardId.ELEMENT_ALPHABET, subtype, context)
        ?: getCustomFunctionalLayoutName(KeyboardId.ELEMENT_SYMBOLS, subtype, context)
        ?: getCustomFunctionalLayoutName(KeyboardId.ELEMENT_SYMBOLS_SHIFTED, subtype, context)
    return anyCustomFunctionalLayout != null
}

fun getCustomFunctionalLayoutName(elementId: Int, subtype: InputMethodSubtype, context: Context): String? {
    val customFunctionalLayoutNames = getCustomLayoutFiles(context).filter { it.name.contains("functional") }.map { it.name.substringBeforeLast(".") + "." }
    if (customFunctionalLayoutNames.isEmpty()) return null
    val languageTag = subtype.locale().toLanguageTag()
    val mainLayoutName = subtype.getExtraValueOf(Constants.Subtype.ExtraValue.KEYBOARD_LAYOUT_SET) ?: "qwerty"

    if (elementId == KeyboardId.ELEMENT_SYMBOLS_SHIFTED) {
        findMatchingLayout(customFunctionalLayoutNames.filter { it.startsWith(CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED) }, mainLayoutName, languageTag)
            ?.let { return it }
    }
    if (elementId == KeyboardId.ELEMENT_SYMBOLS) {
        findMatchingLayout(customFunctionalLayoutNames.filter { it.startsWith(CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS) }, mainLayoutName, languageTag)
            ?.let { return it }
    }
    return findMatchingLayout(customFunctionalLayoutNames.filter { it.startsWith(CUSTOM_FUNCTIONAL_LAYOUT_NORMAL) }, mainLayoutName, languageTag)
}

// todo (when adding custom layouts per locale or main layout): adjust mainLayoutName for custom layouts?
//  remove language tag and file ending (currently name is e.g. custom.en-US.abcdfdsg3.json, and we could use abcdfdsg3 only)
//  this way, custom layouts with same name could use same custom functional layouts
//  currently there is no way to set the language tag or main layout name, so changes don't break backwards compatibility
private fun findMatchingLayout(layoutNames: List<String>, mainLayoutName: String, languageTag: String): String? {
    // first find layout with matching locale and main layout
    return layoutNames.firstOrNull { it.endsWith(".$languageTag.$mainLayoutName.") }
    // then find matching main layout
        ?: layoutNames.firstOrNull { it.endsWith(".$mainLayoutName.") }
        // then find matching language
        ?: layoutNames.firstOrNull { it.endsWith(".$languageTag.") }
        // then find "normal" functional layout (make use of the '.' separator
        ?: layoutNames.firstOrNull { it.count { it == '.' } == 2 }
}

private fun encodeBase36(string: String): String = BigInteger(string.toByteArray()).toString(36)

private fun decodeBase36(string: String) = BigInteger(string, 36).toByteArray().decodeToString()

// this goes into prefs and file names, so do not change!
const val CUSTOM_LAYOUT_PREFIX = "custom."
private const val TAG = "CustomLayoutUtils"
private var customLayouts: List<File>? = null

const val CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS_SHIFTED = "${CUSTOM_LAYOUT_PREFIX}functional_keys_symbols_shifted."
const val CUSTOM_FUNCTIONAL_LAYOUT_SYMBOLS = "${CUSTOM_LAYOUT_PREFIX}functional_keys_symbols."
const val CUSTOM_FUNCTIONAL_LAYOUT_NORMAL = "${CUSTOM_LAYOUT_PREFIX}functional_keys."
