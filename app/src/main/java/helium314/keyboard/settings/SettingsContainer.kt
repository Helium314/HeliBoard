// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.settings

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import helium314.keyboard.settings.screens.createAboutSettings
import helium314.keyboard.settings.screens.createAdvancedSettings
import helium314.keyboard.settings.screens.createAppearanceSettings
import helium314.keyboard.settings.screens.createCorrectionSettings
import helium314.keyboard.settings.screens.createDebugSettings
import helium314.keyboard.settings.screens.createGestureTypingSettings
import helium314.keyboard.settings.screens.createPreferencesSettings
import helium314.keyboard.settings.screens.createToolbarSettingss

class SettingsContainer(context: Context) {
    private val list = createSettings(context)
    private val map: Map<String, Setting> = HashMap<String, Setting>(list.size).apply {
        list.forEach {
            if (put(it.key, it) != null)
                throw IllegalArgumentException("key $it added twice")
        }
    }

    operator fun get(key: Any): Setting? = map[key]

    // could be more elaborate, but should be good enough for a start
    fun filter(searchTerm: String): List<Setting> {
        val term = searchTerm.lowercase()
        val results = mutableSetOf<Setting>()
        list.forEach { setting -> if (setting.title.lowercase().startsWith(term)) results.add(setting) }
        list.forEach { setting -> if (setting.title.lowercase().split(' ').any { it.startsWith(term) }) results.add(setting) }
        list.forEach { setting ->
            if (setting.description?.lowercase()?.split(' ')?.any { it.startsWith(term) } == true)
                results.add(setting)
        }
        return results.toList()
    }
}

class Setting(
    context: Context,
    val key: String,
    @StringRes titleId: Int,
    @StringRes descriptionId: Int? = null,
    private val content: @Composable (Setting) -> Unit
) {
    val title = context.getString(titleId)
    val description = descriptionId?.let { context.getString(it) }

    @Composable
    fun Preference() {
        content(this)
    }
}

private fun createSettings(context: Context) = createAboutSettings(context) +
            createCorrectionSettings(context) + createPreferencesSettings(context) + createToolbarSettingss(context) +
            createGestureTypingSettings(context) + createAdvancedSettings(context) + createDebugSettings(context) +
            createAppearanceSettings(context)

object SettingsWithoutKey {
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
    const val ADJUST_COLORS = "adjust_colors"
    const val ADJUST_COLORS_NIGHT = "adjust_colors_night"
    const val BACKGROUND_IMAGE = "background_image"
    const val BACKGROUND_IMAGE_LANDSCAPE = "background_image_landscape"
    const val CUSTOM_FONT = "custom_font"
}
