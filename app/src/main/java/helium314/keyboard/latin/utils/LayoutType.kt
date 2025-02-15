package helium314.keyboard.latin.utils

import helium314.keyboard.latin.R
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

        val LayoutType.displayNameId get() = when (this) {
            MAIN -> TODO()
            SYMBOLS -> R.string.layout_symbols
            MORE_SYMBOLS -> R.string.layout_symbols_shifted
            FUNCTIONAL -> R.string.layout_functional_keys
            NUMBER -> R.string.layout_number
            NUMBER_ROW -> R.string.layout_number_row
            NUMPAD -> R.string.layout_numpad
            NUMPAD_LANDSCAPE -> R.string.layout_numpad_landscape
            PHONE -> R.string.layout_phone
            PHONE_SYMBOLS -> R.string.layout_phone_symbols
            EMOJI_BOTTOM -> R.string.layout_emoji_bottom_row
            CLIPBOARD_BOTTOM -> R.string.layout_clip_bottom_row
        }
    }
}
