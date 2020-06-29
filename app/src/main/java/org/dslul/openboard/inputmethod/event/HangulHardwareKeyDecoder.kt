package org.dslul.openboard.inputmethod.event

import android.view.KeyEvent
import org.dslul.openboard.inputmethod.latin.RichInputMethodSubtype

object HangulHardwareKeyDecoder {
    @JvmStatic
    fun decode(subtype: RichInputMethodSubtype, event: KeyEvent, defaultEvent: Event): Event {
        val layout = LAYOUTS[subtype.keyboardLayoutSetName] ?: return defaultEvent
        val codePoint = layout[event.keyCode]?.let { if(event.isShiftPressed) it.second else it.first } ?: return defaultEvent

        return Event.createHardwareKeypressEvent(codePoint, event.keyCode, null, event.repeatCount != 0)
    }

    val LAYOUT_DUBEOLSIK_STANDARD = mapOf<Int, Pair<Int, Int>>(
    )

    val LAYOUT_SEBEOLSIK_390 = mapOf<Int, Pair<Int, Int>>(
    )

    val LAYOUT_SEBEOLSIK_FINAL = mapOf<Int, Pair<Int, Int>>(
    )

    val LAYOUTS = mapOf<String, Map<Int, Pair<Int, Int>>>(
            "korean" to LAYOUT_DUBEOLSIK_STANDARD,
            "korean_sebeolsik_390" to LAYOUT_SEBEOLSIK_390,
            "korean_sebeolsik_final" to LAYOUT_SEBEOLSIK_FINAL
    )

}
