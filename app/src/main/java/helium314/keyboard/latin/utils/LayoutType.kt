package helium314.keyboard.latin.utils

import java.io.File
import java.util.EnumMap

enum class LayoutType {
    MAIN, SYMBOLS, MORE_SYMBOLS, FUNCTIONAL, NUMBER, NUMBER_ROW, NUMPAD,
    NUMPAD_LANDSCAPE, PHONE, PHONE_SYMBOLS, EMOJI_BOTTOM, CLIPBOARD_BOTTOM;

    companion object {
        fun EnumMap<LayoutType, String>.toExtraValue() = map { "${it.key.name}:${it.value}" }.joinToString("|")

        fun getLayoutMap(extraValue: String): EnumMap<LayoutType, String> {
            val map = EnumMap<LayoutType, String>(LayoutType::class.java)
            extraValue.split("|").forEach {
                val s = it.split(":")
                runCatching { map[LayoutType.valueOf(s[0])] = s[1] }
            }
            return map
        }

        val LayoutType.folder get() = "layouts${File.separator}${name.lowercase()}${File.separator}"
    }
}
