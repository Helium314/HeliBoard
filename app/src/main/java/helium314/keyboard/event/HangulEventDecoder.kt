// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.event

import android.view.KeyEvent
import helium314.keyboard.latin.RichInputMethodSubtype

import helium314.keyboard.event.HangulCombiner.HangulJamo

object HangulEventDecoder {

    @JvmStatic
    fun decodeHardwareKeyEvent(subtype: RichInputMethodSubtype, event: KeyEvent, defaultEvent: () -> Event): Event {
        val layout = LAYOUTS[subtype.keyboardLayoutSetName] ?: return defaultEvent()
        val codePoint = layout[event.keyCode]?.let { if (event.isShiftPressed) it.second else it.first } ?: return defaultEvent()
        val hardwareEvent = Event.createHardwareKeypressEvent(codePoint, event.keyCode, event.metaState, null, event.repeatCount != 0)
        return decodeSoftwareKeyEvent(hardwareEvent)
    }

    @JvmStatic
    fun decodeSoftwareKeyEvent(event: Event): Event {
        if (event.isCombining) return event
        return if (HangulJamo.of(event.mCodePoint) is HangulJamo.NonHangul) event
        else Event.createCombiningEvent(event)
    }

    private val LAYOUT_DUBEOLSIK_STANDARD = mapOf<Int, Pair<Int, Int>>(
            45 to (0x3142 to 0x3143),
            51 to (0x3148 to 0x3149),
            33 to (0x3137 to 0x3138),
            46 to (0x3131 to 0x3132),
            48 to (0x3145 to 0x3146),
            53 to (0x315b to 0x315b),
            49 to (0x3155 to 0x3155),
            37 to (0x3151 to 0x3151),
            43 to (0x3150 to 0x3152),
            44 to (0x3154 to 0x3156),

            29 to (0x3141 to 0x3141),
            47 to (0x3134 to 0x3134),
            32 to (0x3147 to 0x3147),
            34 to (0x3139 to 0x3139),
            35 to (0x314e to 0x314e),
            36 to (0x3157 to 0x3157),
            38 to (0x3153 to 0x3153),
            39 to (0x314f to 0x314f),
            40 to (0x3163 to 0x3163),

            54 to (0x314b to 0x314b),
            52 to (0x314c to 0x314c),
            31 to (0x314a to 0x314a),
            50 to (0x314d to 0x314d),
            30 to (0x3160 to 0x3160),
            42 to (0x315c to 0x315c),
            41 to (0x3161 to 0x3161)
    )

    private val LAYOUT_SEBEOLSIK_390 = mapOf<Int, Pair<Int, Int>>(
            8 to (0x11c2 to 0x11bd),
            9 to (0x11bb to 0x0040),
            10 to (0x11b8 to 0x0023),
            11 to (0x116d to 0x0024),
            12 to (0x1172 to 0x0025),
            13 to (0x1163 to 0x005e),
            14 to (0x1168 to 0x0026),
            15 to (0x1174 to 0x002a),
            16 to (0x116e to 0x0028),
            7 to (0x110f to 0x0029),

            45 to (0x11ba to 0x11c1),
            51 to (0x11af to 0x11c0),
            33 to (0x1167 to 0x11bf),
            46 to (0x1162 to 0x1164),
            48 to (0x1165 to 0x003b),
            53 to (0x1105 to 0x003c),
            49 to (0x1103 to 0x0037),
            37 to (0x1106 to 0x0038),
            43 to (0x110e to 0x0039),
            44 to (0x1111 to 0x003e),

            29 to (0x11bc to 0x11ae),
            47 to (0x11ab to 0x11ad),
            32 to (0x1175 to 0x11b0),
            34 to (0x1161 to 0x11a9),
            35 to (0x1173 to 0x002f),
            36 to (0x1102 to 0x0027),
            38 to (0x110b to 0x0034),
            39 to (0x1100 to 0x0035),
            40 to (0x110c to 0x0036),
            74 to (0x1107 to 0x003a),
            75 to (0x1110 to 0x0022),

            54 to (0x11b7 to 0x11be),
            52 to (0x11a8 to 0x11b9),
            31 to (0x1166 to 0x11b1),
            50 to (0x1169 to 0x11b6),
            30 to (0x116e to 0x0021),
            42 to (0x1109 to 0x0030),
            41 to (0x1112 to 0x0031),
            55 to (0x002c to 0x0032),
            56 to (0x002e to 0x0033),
            76 to (0x1169 to 0x003f)
    )

    private val LAYOUT_SEBEOLSIK_FINAL = mapOf<Int, Pair<Int, Int>>(
            68 to (0x002a to 0x203b),

            8 to (0x11c2 to 0x11a9),
            9 to (0x11bb to 0x11b0),
            10 to (0x11b8 to 0x11bd),
            11 to (0x116d to 0x11b5),
            12 to (0x1172 to 0x11b4),
            13 to (0x1163 to 0x003d),
            14 to (0x1168 to 0x201c),
            15 to (0x1174 to 0x201d),
            16 to (0x116e to 0x0027),
            7 to (0x110f to 0x007e),
            69 to (0x0029 to 0x003b),
            70 to (0x003e to 0x002b),

            45 to (0x11ba to 0x11c1),
            51 to (0x11af to 0x11c0),
            33 to (0x1167 to 0x11ac),
            46 to (0x1162 to 0x11b6),
            48 to (0x1165 to 0x11b3),
            53 to (0x1105 to 0x0035),
            49 to (0x1103 to 0x0036),
            37 to (0x1106 to 0x0037),
            43 to (0x110e to 0x0038),
            44 to (0x1111 to 0x0039),
            71 to (0x0028 to 0x0025),
            72 to (0x003c to 0x002f),
            73 to (0x003a to 0x005c),

            29 to (0x11bc to 0x11ae),
            47 to (0x11ab to 0x11ad),
            32 to (0x1175 to 0x11b2),
            34 to (0x1161 to 0x11b1),
            35 to (0x1173 to 0x1164),
            36 to (0x1102 to 0x0030),
            38 to (0x110b to 0x0031),
            39 to (0x1100 to 0x0032),
            40 to (0x110c to 0x0033),
            74 to (0x1107 to 0x0034),
            75 to (0x1110 to 0x00b7),

            54 to (0x11b7 to 0x11be),
            52 to (0x11a8 to 0x11b9),
            31 to (0x1166 to 0x11bf),
            50 to (0x1169 to 0x11aa),
            30 to (0x116e to 0x003f),
            42 to (0x1109 to 0x002d),
            41 to (0x1112 to 0x0022),
            55 to (0x002c to 0x002c),
            56 to (0x002e to 0x002e),
            76 to (0x1169 to 0x0021)
    )

    private val LAYOUTS = mapOf<String, Map<Int, Pair<Int, Int>>>(
            "korean" to LAYOUT_DUBEOLSIK_STANDARD,
            "korean_sebeolsik_390" to LAYOUT_SEBEOLSIK_390,
            "korean_sebeolsik_final" to LAYOUT_SEBEOLSIK_FINAL
    )

}
