// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.activity.ComponentActivity
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import helium314.keyboard.latin.utils.DeviceProtectedUtils
import helium314.keyboard.settings.screens.createAboutPrefs
import helium314.keyboard.settings.screens.createAdvancedPrefs
import helium314.keyboard.settings.screens.createAppearancePrefs
import helium314.keyboard.settings.screens.createCorrectionPrefs
import helium314.keyboard.settings.screens.createDebugPrefs
import helium314.keyboard.settings.screens.createGestureTypingPrefs
import helium314.keyboard.settings.screens.createPreferencesPrefs
import helium314.keyboard.settings.screens.createToolbarPrefs

class AllPrefs(context: Context) {
    private val list = createPrefDefs(context)

    val map: Map<String, PrefDef> = HashMap<String, PrefDef>(list.size).apply {
        list.forEach {
            if (put(it.key, it) != null)
                throw IllegalArgumentException("key $it added twice")
        }
    }

    // could be more elaborate, but should be good enough for a start
    fun filter(searchTerm: String): List<PrefDef> {
        val term = searchTerm.lowercase()
        val results = mutableSetOf<PrefDef>()
        list.forEach { if (it.title.lowercase().startsWith(term)) results.add(it) }
        list.forEach { if (it.title.lowercase().split(' ').any { it.startsWith(term) }) results.add(it) }
        list.forEach {
            if (it.description?.lowercase()?.split(' ')?.any { it.startsWith(term) } == true)
                results.add(it)
        }
        return results.toList()
    }
}

class PrefDef(
    context: Context,
    val key: String,
    @StringRes titleId: Int,
    @StringRes descriptionId: Int? = null,
    private val compose: @Composable (PrefDef) -> Unit
) {
    val title = context.getString(titleId)
    val description = descriptionId?.let { context.getString(it) }

    @Composable
    fun Preference() {
        compose(this)
    }
}

private fun createPrefDefs(context: Context) = createAboutPrefs(context) +
            createCorrectionPrefs(context) + createPreferencesPrefs(context) + createToolbarPrefs(context) +
            createGestureTypingPrefs(context) + createAdvancedPrefs(context) + createDebugPrefs(context) +
            createAppearancePrefs(context)

// todo: move somewhere else
fun Context.getActivity(): ComponentActivity? {
    val componentActivity = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.getActivity()
        else -> null
    }
    return componentActivity
}

fun Context.prefs(): SharedPreferences = DeviceProtectedUtils.getSharedPreferences(this)

object NonSettingsPrefs {
    const val EDIT_PERSONAL_DICTIONARY = "edit_personal_dictionary"
    const val APP = "app"
    const val VERSION = "version"
    const val LICENSE = "license"
    const val HIDDEN_FEATURES = "hidden_features"
    const val GITHUB = "github"
    const val SAVE_LOG = "save_log"
    const val CUSTOM_KEY_CODES = "customize_key_codes"
    const val CUSTOM_SYMBOLS_NUMBER_LAYOUTS = "custom_symbols_number_layouts"
    const val CUSTOM_FUNCTIONAL_LAYOUTS = "custom_functional_key_layouts"
    const val BACKUP_RESTORE = "backup_restore"
    const val DEBUG_SETTINGS = "screen_debug"
    const val LOAD_GESTURE_LIB = "load_gesture_library"
}

@JvmField
// todo: maybe better name it "reloadKeyboard"?
var themeChanged = false
