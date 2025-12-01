// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import android.content.Context
import org.json.JSONObject

/** Loads Khipro mappings from assets/khipro-mappings.json */
object KhiproMappingLoader {
    
    private const val MAPPING_FILE = "khipro-mappings.json"
    
    fun loadMappings(context: Context): Map<String, Map<String, String>> {
        try {
            val jsonString = context.assets.open(MAPPING_FILE)
                .bufferedReader()
                .use { it.readText() }
            
            val rootJson = JSONObject(jsonString)
            val groups = mutableMapOf<String, Map<String, String>>()
            
            rootJson.keys().forEach { groupName ->
                val groupJson = rootJson.getJSONObject(groupName)
                groups[groupName.lowercase()] = groupJson.toMap()
            }
            
            return groups
        } catch (e: Exception) {
            android.util.Log.e("KhiproLoader", "Failed to load mappings", e)
            return emptyMap()
        }
    }
    
    private fun JSONObject.toMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        keys().forEach { key ->
            map[key] = getString(key)
        }
        return map
    }
}
