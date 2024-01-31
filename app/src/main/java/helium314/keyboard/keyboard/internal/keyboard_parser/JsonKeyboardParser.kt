// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.AbstractKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.AutoTextKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.CaseSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.CharWidthSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KanaSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.LayoutDirectionSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.MultiTextKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.ShiftStateSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.TextKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.VariationSelector

/**
 *  Parser for json layout files as used in FlorisBoard, see floris directory for classes taken from FlorisBoard.
 *  Some differences to the FlorisBoard keys:
 *   (currently) only normal keys supported
 *   if label or code are missing one is created from the other
 *   auto_text_key ignored (i.e. interpreted like the default TextKey)
 *   codes of multi_text_key not used, only the label
 *   (currently) popups is always read to [number, main, relevant] layoutMoreKeys, no choice of which to use or which hint is provided
 */
class JsonKeyboardParser(private val params: KeyboardParams, private val context: Context) : KeyboardParser(params, context) {

    override fun parseCoreLayout(layoutContent: String): MutableList<List<KeyData>> {
        val florisKeyData: List<List<AbstractKeyData>> = florisJsonConfig.decodeFromString(layoutContent)
        // initially 200 ms parse (debug build on S4 mini)
        // after a few parses it's optimized and 20-30 ms
        // whole load is 50-70 ms vs 30-55 with simple parser -> it's ok
        return florisKeyData.mapTo(mutableListOf()) { it.mapNotNull { it.compute(params) } }
    }

}

/*
 * Copyright (C) 2021 Patrick Goldinger
 * modified
 * SPDX-License-Identifier: Apache-2.0
 */
private val florisJsonConfig = Json {
    classDiscriminator = "$"
    encodeDefaults = true
    ignoreUnknownKeys = true
    isLenient = true
    serializersModule = SerializersModule {
        polymorphic(AbstractKeyData::class) {
            subclass(TextKeyData::class, TextKeyData.serializer())
            subclass(AutoTextKeyData::class, AutoTextKeyData.serializer())
            subclass(MultiTextKeyData::class, MultiTextKeyData.serializer())
            subclass(CaseSelector::class, CaseSelector.serializer())
            subclass(ShiftStateSelector::class, ShiftStateSelector.serializer())
            subclass(VariationSelector::class, VariationSelector.serializer())
            subclass(LayoutDirectionSelector::class, LayoutDirectionSelector.serializer())
            subclass(CharWidthSelector::class, CharWidthSelector.serializer())
            subclass(KanaSelector::class, KanaSelector.serializer())
            defaultDeserializer { TextKeyData.serializer() }
        }
        polymorphic(KeyData::class) {
            subclass(TextKeyData::class, TextKeyData.serializer())
            subclass(AutoTextKeyData::class, AutoTextKeyData.serializer())
            subclass(MultiTextKeyData::class, MultiTextKeyData.serializer())
            defaultDeserializer { TextKeyData.serializer() }
        }
    }
}
