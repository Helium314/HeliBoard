// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.latin.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.serialization.json.Json

fun customIconNames(prefs: SharedPreferences) = runCatching {
    Json.decodeFromString<Map<String, String>>(prefs.getString(Settings.PREF_CUSTOM_ICON_NAMES, Defaults.PREF_CUSTOM_ICON_NAMES)!!)
}.getOrElse { emptyMap() }

fun customIconIds(context: Context, prefs: SharedPreferences) = customIconNames(prefs)
    .mapNotNull { entry ->
        val id = runCatching { context.resources.getIdentifier(entry.value, "drawable", context.packageName) }.getOrNull()
        id?.let { entry.key to it }
    }
