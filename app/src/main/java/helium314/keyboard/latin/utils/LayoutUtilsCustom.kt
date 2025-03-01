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
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.LayoutType.Companion.folder
import helium314.keyboard.latin.utils.ScriptUtils.script
import kotlinx.serialization.SerializationException
import java.io.File
import java.io.IOException
import java.util.EnumMap
import java.util.Locale

object LayoutUtilsCustom {

    fun checkLayout(layoutContent: String, context: Context): Boolean {
        if (Settings.getValues() == null)
            Settings.getInstance().loadSettings(context)
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

    fun getLayoutFiles(layoutType: LayoutType, context: Context, locale: Locale? = null): List<File> {
        val layouts = customLayoutMap.getOrPut(layoutType) {
            File(DeviceProtectedUtils.getFilesDir(context), layoutType.folder).listFiles()?.toList() ?: emptyList()
        }
        if (layoutType != LayoutType.MAIN || locale == null)
            return layouts
        if (locale.script() == ScriptUtils.SCRIPT_LATIN)
            return layouts.filter { it.name.startsWith(CUSTOM_LAYOUT_PREFIX + ScriptUtils.SCRIPT_LATIN + ".") }
        return layouts.filter { it.name.startsWith(CUSTOM_LAYOUT_PREFIX + locale.toLanguageTag() + ".") }
    }

    fun onLayoutFileChanged() {
        customLayoutMap.clear()
    }

    fun deleteLayout(layoutName: String, layoutType: LayoutType, context: Context) {
        getLayoutFile(layoutName, layoutType, context).delete()
        onLayoutFileChanged()
        SubtypeSettings.onRenameLayout(layoutType, layoutName, null, context)
        KeyboardSwitcher.getInstance().setThemeNeedsReload()
    }

    fun getDisplayName(layoutName: String) =
        try {
            if (layoutName.count { it == '.' } == 3) // main layout: "custom.<locale or script>.<name>.", other: custom.<name>.
                decodeBase36(layoutName.substringAfter(CUSTOM_LAYOUT_PREFIX).substringAfter(".").substringBeforeLast("."))
            else decodeBase36(layoutName.substringAfter(CUSTOM_LAYOUT_PREFIX).substringBeforeLast("."))
        } catch (_: NumberFormatException) {
            layoutName
        }


    /** @return layoutName for given [displayName]. If [layoutType ]is MAIN, non-null [locale] must be supplied */
    fun getLayoutName(displayName: String, layoutType: LayoutType, locale: Locale? = null): String {
        if (layoutType != LayoutType.MAIN)
            return CUSTOM_LAYOUT_PREFIX + encodeBase36(displayName) + "."
        if (locale == null) throw IllegalArgumentException("locale for main layout not specified")
        return if (locale.script() == ScriptUtils.SCRIPT_LATIN)
            CUSTOM_LAYOUT_PREFIX + ScriptUtils.SCRIPT_LATIN + "." + encodeBase36(displayName) + "."
        else CUSTOM_LAYOUT_PREFIX + locale.toLanguageTag() + "." + encodeBase36(displayName) + "."
    }

    fun isCustomLayout(layoutName: String) = layoutName.startsWith(CUSTOM_LAYOUT_PREFIX)

    fun getLayoutFile(layoutName: String, layoutType: LayoutType, context: Context): File {
        val file = File(DeviceProtectedUtils.getFilesDir(context), layoutType.folder + layoutName)
        file.parentFile?.mkdirs()
        return file
    }

    // this goes into prefs and file names, so do not change!
    const val CUSTOM_LAYOUT_PREFIX = "custom."
    private const val TAG = "LayoutUtilsCustom"
    private val customLayoutMap = EnumMap<LayoutType, List<File>>(LayoutType::class.java)
}
