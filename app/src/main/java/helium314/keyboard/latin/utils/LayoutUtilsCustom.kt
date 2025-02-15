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
import helium314.keyboard.keyboard.internal.keyboard_parser.LayoutParser
import helium314.keyboard.keyboard.internal.keyboard_parser.POPUP_KEYS_NORMAL
import helium314.keyboard.keyboard.internal.keyboard_parser.addLocaleKeyTextsToParams
import helium314.keyboard.latin.R
import helium314.keyboard.latin.common.FileUtils
import helium314.keyboard.latin.common.decodeBase36
import helium314.keyboard.latin.common.encodeBase36
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.util.EnumMap

object LayoutUtilsCustom {
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
        if (!checkLayout(layoutContent, context))
            return infoDialog(context, context.getString(R.string.layout_error, "invalid layout file, ${Log.getLog(10).lastOrNull { it.tag == TAG }?.message}"))
//    val isJson = checkLayout(layoutContent, context)
//        ?: return infoDialog(context, context.getString(R.string.layout_error, "invalid layout file, ${Log.getLog(10).lastOrNull { it.tag == TAG }?.message}"))

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
                name = "$CUSTOM_LAYOUT_PREFIX${languageTag}.${encodeBase36(name)}."
                val file = getCustomLayoutFile(name, context)
                if (file.exists())
                    file.delete()
                file.parentFile?.mkdir()
                file.writeText(layoutContent)
                onAdded(name)
            }
            .show()
    }

    fun checkLayout(layoutContent: String, context: Context): Boolean {
        val params = KeyboardParams()
        params.mId = KeyboardLayoutSet.getFakeKeyboardId(KeyboardId.ELEMENT_ALPHABET)
        params.mPopupKeyTypes.add(POPUP_KEYS_LAYOUT)
        addLocaleKeyTextsToParams(context, params, POPUP_KEYS_NORMAL)
        try {
            val keys = LayoutParser.parseJsonString(layoutContent).map { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
            return checkKeys(keys)
        } catch (e: SerializationException) {
            Log.w(TAG, "json parsing error", e)
            if (layoutContent.trimStart().startsWith("[") && layoutContent.trimEnd().endsWith("]") && layoutContent.contains("},"))
                return false // we're sure enough it's a json
        } catch (e: Exception) {
            Log.w(TAG, "json layout parsed, but considered invalid", e)
            return false
        }
        try {
            val keys = LayoutParser.parseSimpleString(layoutContent).map { row -> row.map { it.toKeyParams(params) } }
            return checkKeys(keys)
        } catch (e: Exception) { Log.w(TAG, "error parsing custom simple layout", e) }
        if (layoutContent.trimStart().startsWith("[") && layoutContent.trimEnd().endsWith("]")) {
            // layout can't be loaded, assume it's json -> load json layout again because the error message shown to the user is from the most recent error
            try {
                LayoutParser.parseJsonString(layoutContent).map { row -> row.mapNotNull { it.compute(params)?.toKeyParams(params) } }
            } catch (e: Exception) { Log.w(TAG, "json parsing error", e) }
        }
        return false
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
    fun getCustomLayoutFile(layoutName: String, context: Context) = // todo: remove
        File(getCustomLayoutsDir(context), layoutName)

    // cache to avoid frequently listing files
    /** don't rename or delete files without calling [onCustomLayoutFileListChanged] */
    fun getCustomLayoutFiles(context: Context): List<File> { // todo: remove, AND USE THE NEW THING FOR SUBTYPE SETTINGS
        customLayouts?.let { return it }
        val layouts = getCustomLayoutsDir(context).listFiles()?.toList() ?: emptyList()
        customLayouts = layouts
        return layouts
    }

    fun getCustomLayoutFiles(layoutType: LayoutType, context: Context): List<File> =
        customLayoutMap.getOrPut(layoutType) {
            File(DeviceProtectedUtils.getFilesDir(context), layoutType.folder).listFiles()?.toList() ?: emptyList()
        }

    private val customLayoutMap = EnumMap<LayoutType, List<File>>(LayoutType::class.java)

    fun onCustomLayoutFileListChanged() {
        customLayouts = null
        customLayoutMap.clear()
    }

    private fun getCustomLayoutsDir(context: Context) = File(DeviceProtectedUtils.getFilesDir(context), "layouts")

    fun getCustomLayoutDisplayName(layoutName: String) =
        try {
            decodeBase36(layoutName.substringAfter(CUSTOM_LAYOUT_PREFIX).substringBeforeLast("."))
        } catch (_: NumberFormatException) {
            layoutName
        }

    fun getCustomLayoutName(displayName: String) = CUSTOM_LAYOUT_PREFIX + encodeBase36(displayName) + "."

    fun isCustomLayout(layoutName: String) = layoutName.startsWith(CUSTOM_LAYOUT_PREFIX)

    fun getCustomLayoutFile(layoutName: String, layoutType: LayoutType, context: Context): File {
        val file = File(DeviceProtectedUtils.getFilesDir(context), layoutType.folder + layoutName)
        file.parentFile?.mkdirs()
        return file
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
            .setTitle(getCustomLayoutDisplayName(layoutName))
            .setView(editText)
            .setPositiveButton(R.string.save) { _, _ ->
                val content = editText.text.toString()
                if (!checkLayout(content, context)) {
                    editCustomLayout(layoutName, context, content)
                    infoDialog(context, context.getString(R.string.layout_error, Log.getLog(10).lastOrNull { it.tag == TAG }?.message))
                } else {
                    file.parentFile?.mkdir()
                    file.writeText(content)
                    onCustomLayoutFileListChanged()
                    KeyboardSwitcher.getInstance().forceUpdateKeyboardTheme(context)
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
        if (displayName != null) {
            if (file.exists()) {
                builder.setNeutralButton(R.string.delete) { _, _ ->
                    confirmDialog(context, context.getString(R.string.delete_layout, displayName), context.getString(R.string.delete)) {
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

    // this goes into prefs and file names, so do not change!
    const val CUSTOM_LAYOUT_PREFIX = "custom."
    private const val TAG = "LayoutUtilsCustom"
    private var customLayouts: List<File>? = null
}
