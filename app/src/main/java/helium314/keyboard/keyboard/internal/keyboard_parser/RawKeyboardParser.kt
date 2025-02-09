// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import android.content.res.Configuration
import helium314.keyboard.keyboard.KeyboardId
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.AbstractKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.AutoTextKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.CaseSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.CharWidthSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KanaSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyboardStateSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.LayoutDirectionSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.MultiTextKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.ShiftStateSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.TextKeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.VariationSelector
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.toTextKey
import helium314.keyboard.latin.common.splitOnWhitespace
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.utils.CUSTOM_LAYOUT_PREFIX
import helium314.keyboard.latin.utils.Log
import helium314.keyboard.latin.utils.ScriptUtils
import helium314.keyboard.latin.utils.ScriptUtils.script
import helium314.keyboard.latin.utils.getCustomFunctionalLayoutName
import helium314.keyboard.latin.utils.getCustomLayoutFile
import helium314.keyboard.latin.utils.getCustomLayoutFiles
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.io.File

object RawKeyboardParser {
    private const val TAG = "RawKeyboardParser"
    private val rawLayoutCache = hashMapOf<String, (KeyboardParams) -> MutableList<MutableList<KeyData>>>()

    val symbolAndNumberLayouts = listOf(LAYOUT_SYMBOLS, LAYOUT_SYMBOLS_SHIFTED, LAYOUT_SYMBOLS_ARABIC,
        LAYOUT_NUMBER, LAYOUT_NUMPAD, LAYOUT_NUMPAD_LANDSCAPE, LAYOUT_PHONE, LAYOUT_PHONE_SYMBOLS,
        LAYOUT_NUMBER_ROW, LAYOUT_EMOJI_BOTTOM_ROW, LAYOUT_CLIPBOARD_BOTTOM_ROW)

    fun clearCache() = rawLayoutCache.clear()

    fun parseLayout(params: KeyboardParams, context: Context, isFunctional: Boolean = false): MutableList<MutableList<KeyData>> {
        val layoutName = if (isFunctional) {
            if (!params.mId.isAlphaOrSymbolKeyboard) return mutableListOf(mutableListOf())
            else getFunctionalLayoutName(params, context)
        } else {
            getLayoutName(params, context)
        }
        return rawLayoutCache.getOrPut(layoutName) {
            createCacheLambda(layoutName, context)
        }(params)
    }

    fun parseLayout(layoutName: String, params: KeyboardParams, context: Context): MutableList<MutableList<KeyData>> {
        return rawLayoutCache.getOrPut(layoutName) {
            createCacheLambda(layoutName, context)
        }(params)
    }

    /**
     *  Parse for json layout files as used in FlorisBoard, see floris directory for classes taken from FlorisBoard.
     *  Some differences to the FlorisBoard keys:
     *   (currently) only normal keys supported
     *   if label or code are missing one is created from the other
     *   auto_text_key ignored (i.e. interpreted like the default TextKey)
     *   codes of multi_text_key not used, only the label
     *   (currently) popups is always read to [number, main, relevant] layoutPopupKeys, no choice of which to use or which hint is provided
     */
    fun parseJsonString(layoutText: String, strict: Boolean = true): List<List<AbstractKeyData>> =
        if (strict) checkJsonConfig.decodeFromString(layoutText.stripCommentLines())
        else florisJsonConfig.decodeFromString(layoutText.stripCommentLines())

    /** Parse simple layouts, defined only as rows of (normal) keys with popup keys. */
    fun parseSimpleString(layoutText: String): List<List<KeyData>> {
        val rowStrings = layoutText.replace("\r\n", "\n").split("\\n\\s*\\n".toRegex()).filter { it.isNotBlank() }
        return rowStrings.map { row ->
            row.split("\n").mapNotNull { parseKey(it) }
        }
    }

    private fun parseKey(key: String): KeyData? {
        if (key.isBlank()) return null
        val split = key.splitOnWhitespace()
        return if (split.size == 1) split.first().toTextKey()
        else split.first().toTextKey(split.drop(1))
    }

    private fun createCacheLambda(layoutName: String, context: Context): (KeyboardParams) -> MutableList<MutableList<KeyData>> {
        val layoutFileName = getLayoutFileName(layoutName, context)
        val layoutText = if (layoutFileName.startsWith(CUSTOM_LAYOUT_PREFIX)) {
            try {
                getCustomLayoutFile(layoutFileName, context).readText().trimStart()
            } catch (e: Exception) { // fall back to defaults if for some reason file is broken
                val name = when {
                    layoutName.contains("functional") -> "functional_keys.json"
                    layoutName.contains("number_row") -> "number_row.txt"
                    layoutName.contains("symbols") -> "symbols.txt"
                    else -> "qwerty.txt"
                }
                Log.e(TAG, "cannot open layout $layoutName, falling back to $name", e)
                context.assets.open("layouts${File.separator}$name").reader().use { it.readText() }
            }
        } else context.assets.open("layouts${File.separator}$layoutFileName").reader().use { it.readText() }
        if (layoutFileName.endsWith(".json") || (layoutFileName.startsWith(CUSTOM_LAYOUT_PREFIX) && layoutText.startsWith("["))) {
            try {
                val florisKeyData = parseJsonString(layoutText, false)
                return { params ->
                    florisKeyData.mapTo(mutableListOf()) { row ->
                        row.mapNotNullTo(mutableListOf()) { it.compute(params) }
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "could not parse json layout for $layoutName, falling back to simple layout parsing", e)
            }
        }
        // not a json, or invalid json
        val simpleKeyData = parseSimpleString(layoutText)
        return { params ->
            simpleKeyData.mapIndexedTo(mutableListOf()) { i, row ->
                val newRow = row.toMutableList()
                if (params.mId.isAlphabetKeyboard
                        && params.mId.mSubtype.keyboardLayoutSetName.endsWith("+")
                        && "$layoutName+" ==  params.mId.mSubtype.keyboardLayoutSetName
                    ) {
                    params.mLocaleKeyboardInfos.getExtraKeys(i+1)?.let { newRow.addAll(it) }
                }
                newRow
            }
        }
    }

    private fun getLayoutName(params: KeyboardParams, context: Context) = when (params.mId.mElementId) {
        KeyboardId.ELEMENT_SYMBOLS -> if (params.mId.locale.script() == ScriptUtils.SCRIPT_ARABIC) LAYOUT_SYMBOLS_ARABIC else LAYOUT_SYMBOLS
        KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> LAYOUT_SYMBOLS_SHIFTED
        KeyboardId.ELEMENT_NUMPAD -> if (context.resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE)
            LAYOUT_NUMPAD_LANDSCAPE
        else
            LAYOUT_NUMPAD
        KeyboardId.ELEMENT_NUMBER -> LAYOUT_NUMBER
        KeyboardId.ELEMENT_PHONE -> LAYOUT_PHONE
        KeyboardId.ELEMENT_PHONE_SYMBOLS -> LAYOUT_PHONE_SYMBOLS
        KeyboardId.ELEMENT_EMOJI_BOTTOM_ROW -> LAYOUT_EMOJI_BOTTOM_ROW
        KeyboardId.ELEMENT_CLIPBOARD_BOTTOM_ROW -> LAYOUT_CLIPBOARD_BOTTOM_ROW
        else -> params.mId.mSubtype.keyboardLayoutSetName.substringBeforeLast("+")
    }

    private fun getFunctionalLayoutName(params: KeyboardParams, context: Context): String {
        if (Settings.getInstance().current.mHasCustomFunctionalLayout) {
            getCustomFunctionalLayoutName(params.mId.mElementId, params.mId.mSubtype.rawSubtype, context)
                ?.let { return it }
        }
        return if (Settings.getInstance().isTablet) "functional_keys_tablet" else "functional_keys"
    }

    /** returns the file name matching the layout name, making sure the file exists (falling back to qwerty.txt) */
    private fun getLayoutFileName(layoutName: String, context: Context): String {
        val customFiles = getCustomLayoutFiles(context).map { it.name }
        if (layoutName.startsWith(CUSTOM_LAYOUT_PREFIX)) {
            return customFiles.firstOrNull { it.startsWith(layoutName)}
                ?: if (layoutName.contains("functional")) "functional_keys.json" else "qwerty.txt" // fallback to defaults
        }
        val assetsFiles by lazy { context.assets.list("layouts")!! }
        return if (layoutName in symbolAndNumberLayouts) {
            customFiles.firstOrNull { it.startsWith("$CUSTOM_LAYOUT_PREFIX$layoutName.")}
                ?: assetsFiles.first { it.startsWith(layoutName) }
        } else {
            // can't be custom layout, so it must be in assets
            val searchName = layoutName.substringBeforeLast("+") // consider there are layouts ending in "+" for adding extra keys
            assetsFiles.firstOrNull { it.startsWith(searchName) } ?: "qwerty.txt" // in case it was removed
        }
    }

    // allow commenting lines by starting them with "//"
    private fun String.stripCommentLines(): String =
        split("\n").filterNot { it.startsWith("//") }.joinToString("\n")

    /*
     * Copyright (C) 2021 Patrick Goldinger
     * modified
     * SPDX-License-Identifier: Apache-2.0
     */
    @OptIn(ExperimentalSerializationApi::class)
    private val florisJsonConfig = Json {
        allowTrailingComma = true
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
                subclass(KeyboardStateSelector::class, KeyboardStateSelector.serializer())
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

    // copy of florisJsonConfig, but with ignoreUnknownKeys = false so users get warned
    // this is not default because users may have old layouts that should not stop working on app upgrade
    @OptIn(ExperimentalSerializationApi::class)
    private val checkJsonConfig = Json {
        allowTrailingComma = true
        classDiscriminator = "$"
        encodeDefaults = true
        ignoreUnknownKeys = false
        isLenient = true
        serializersModule = SerializersModule {
            polymorphic(AbstractKeyData::class) {
                subclass(TextKeyData::class, TextKeyData.serializer())
                subclass(AutoTextKeyData::class, AutoTextKeyData.serializer())
                subclass(MultiTextKeyData::class, MultiTextKeyData.serializer())
                subclass(CaseSelector::class, CaseSelector.serializer())
                subclass(ShiftStateSelector::class, ShiftStateSelector.serializer())
                subclass(VariationSelector::class, VariationSelector.serializer())
                subclass(KeyboardStateSelector::class, KeyboardStateSelector.serializer())
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
}
