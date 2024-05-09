package helium314.keyboard.keyboard.internal.keyboard_parser.floris

/** labels for functional / special keys */
object KeyLabel {
    const val EMOJI = "emoji"
    const val COM = "com"
    const val LANGUAGE_SWITCH = "language_switch"
    const val ACTION = "action"
    const val DELETE = "delete"
    const val SHIFT = "shift"
    const val NUMPAD = "numpad"
    const val SYMBOL = "symbol"
    const val ALPHA = "alpha"
    const val SYMBOL_ALPHA = "symbol_alpha"
    const val PERIOD = "period"
    const val COMMA = "comma"
    const val SPACE = "space"
    const val ZWNJ = "zwnj"

    /** to make sure a FlorisBoard label works when reading a JSON layout */
    // resulting special labels should be names of FunctionalKey enum, case insensitive
    fun String.convertFlorisLabel(): String = when (this) {
        "view_characters" -> ALPHA
        "view_symbols" -> SYMBOL
        "view_numeric_advanced" -> NUMPAD
        "view_phone" -> ALPHA // phone keyboard is treated like alphabet, just with different layout
        "view_phone2" -> SYMBOL // phone symbols
        "ime_ui_mode_media" -> EMOJI
        "ime_ui_mode_clipboard" -> "clipboard" // todo: is this supported? when yes -> add to readme, and add a test
        "ime_ui_mode_text" -> ALPHA
        "currency_slot_1" -> "$$$"
        "currency_slot_2" -> "$$$1"
        "currency_slot_3" -> "$$$2"
        "currency_slot_4" -> "$$$3"
        "currency_slot_5" -> "$$$4"
        "currency_slot_6" -> "$$$5"
        "enter" -> ACTION
        "half_space" -> ZWNJ
        else -> this
    }

}
