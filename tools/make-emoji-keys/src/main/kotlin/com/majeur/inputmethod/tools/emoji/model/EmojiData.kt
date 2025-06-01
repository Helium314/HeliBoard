// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji.model

import kotlin.collections.mutableSetOf

class EmojiData {

    var unicodeVersion = ""
    var dataDate = ""

    private var emojiGroups = mutableMapOf<EmojiGroup, MutableList<EmojiSpec>>()

    operator fun get(group: EmojiGroup) = emojiGroups.getValue(group)

    fun emojiCount(group: EmojiGroup): Int {
        var acc = 0
        emojiGroups.values.forEach { acc += it.size }
        return acc
    }

    fun emojiGroupCount(group: EmojiGroup) = emojiGroups[group]?.size ?: 0

    fun insertEmoji(group: EmojiGroup, codes: IntArray, unicodeVer: Float, name: String): EmojiSpec {
        return EmojiSpec(codes, unicodeVer, name).also { emoji ->
            val baseEmoji = findBaseEmoji(group, emoji)
            if (baseEmoji != null && onEmojiVariantInserted(group, baseEmoji, emoji)) {
                baseEmoji.variants.add(emoji)
            } else if (onEmojiInserted(group, emoji)) {
                emojiGroups.getOrPut(group) { mutableListOf() }.add(emoji)
            }
        }
    }

    private fun onEmojiInserted(group: EmojiGroup, emoji: EmojiSpec): Boolean {
        // Unicode RGI does not include letter symbols but Android supports them, so we inject them manually.
        if (emoji.codes contentEquals RAW_CPS_KEYCAP_HASH) {
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_A), 2.0f, "regional indicator symbol letter a")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_B), 2.0f, "regional indicator symbol letter b")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_C), 2.0f, "regional indicator symbol letter c")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_D), 2.0f, "regional indicator symbol letter d")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_E), 2.0f, "regional indicator symbol letter e")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_F), 2.0f, "regional indicator symbol letter f")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_G), 2.0f, "regional indicator symbol letter g")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_H), 2.0f, "regional indicator symbol letter h")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_I), 2.0f, "regional indicator symbol letter i")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_J), 2.0f, "regional indicator symbol letter j")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_K), 2.0f, "regional indicator symbol letter k")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_L), 2.0f, "regional indicator symbol letter l")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_M), 2.0f, "regional indicator symbol letter m")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_N), 2.0f, "regional indicator symbol letter n")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_O), 2.0f, "regional indicator symbol letter o")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_P), 2.0f, "regional indicator symbol letter p")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_Q), 2.0f, "regional indicator symbol letter q")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_R), 2.0f, "regional indicator symbol letter r")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_S), 2.0f, "regional indicator symbol letter s")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_T), 2.0f, "regional indicator symbol letter t")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_U), 2.0f, "regional indicator symbol letter u")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_V), 2.0f, "regional indicator symbol letter v")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_W), 2.0f, "regional indicator symbol letter w")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_X), 2.0f, "regional indicator symbol letter x")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_Y), 2.0f, "regional indicator symbol letter y")
            insertEmoji(group, intArrayOf(CP_REGIONAL_INDICATOR_SYMBOL_LETTER_Z), 2.0f, "regional indicator symbol letter z")
        }

        // Some multi-skin-tone variants use a different base code than their non-multi-skin-tone counterparts,
        // so they don't get grouped. We drop them here, to prevent each variant from being displayed separately.
        return ! hasMultipleSkinModifiers(emoji.codes)
    }

    private fun hasMultipleSkinModifiers(codes: IntArray): Boolean {
        val tones = mutableSetOf<Int>()
        codes.forEach {
            when (it) {
                CP_LIGHT_SKIN_TONE, CP_MEDIUM_LIGHT_SKIN_TONE, CP_MEDIUM_SKIN_TONE,
                CP_MEDIUM_DARK_SKIN_TONE, CP_DARK_SKIN_TONE ->
                    tones.add(it)
            }
        }
        return tones.size > 1
    }

    private fun onEmojiVariantInserted(group: EmojiGroup, baseSpec: EmojiSpec, emojiSpec: EmojiSpec): Boolean {
        return true
    }

    private fun findBaseEmoji(group: EmojiGroup, emoji: EmojiSpec): EmojiSpec? {
        val (baseCodePoints, componentCode) = withoutComponentCodes(emoji.codes)

        // No component codes found, this emoji is a standalone one
        if (componentCode == CP_NUL) return null

        // Second try for emojis with U+FE0F suffix
        val baseCodePoints2 = baseCodePoints + CP_VARIANT_SELECTOR

        // Third try for emojis with U+FE0F prefix before an eventual ZWJ
        val baseCodePoints3 = emoji.codes.toMutableList()
                .apply { set(emoji.codes.indexOf(componentCode), CP_VARIANT_SELECTOR) }.toIntArray()

        return emojiGroups[group]?.firstOrNull { it.codes contentEquals baseCodePoints }
            ?: emojiGroups[group]?.firstOrNull { it.codes contentEquals baseCodePoints2 }
            ?: emojiGroups[group]?.firstOrNull { it.codes contentEquals baseCodePoints3 }
    }

    private fun withoutComponentCodes(codes: IntArray) : Pair<IntArray, Int> {
        var res = codes
        var tone = CP_NUL
        codes.forEach { code ->
            when (code) {
                CP_LIGHT_SKIN_TONE, CP_MEDIUM_LIGHT_SKIN_TONE, CP_MEDIUM_SKIN_TONE,
                CP_MEDIUM_DARK_SKIN_TONE, CP_DARK_SKIN_TONE -> {
                    res = res.asList().minus(code).toIntArray()
                    tone = code
                }
            }
        }
        return res to tone
    }

    companion object {

        private val RAW_CPS_KEYCAP_HASH = intArrayOf(0x0023, 0xFE0F, 0x20E3)

        const val CP_NUL = 0x0000

        private const val CP_ZWJ = 0x200D
        private const val CP_FEMALE_SIGN = 0x2640
        private const val CP_MALE_SIGN = 0x2642
        private const val CP_LIGHT_SKIN_TONE = 0x1F3FB
        private const val CP_MEDIUM_LIGHT_SKIN_TONE = 0x1F3FC
        private const val CP_MEDIUM_SKIN_TONE = 0x1F3FD
        private const val CP_MEDIUM_DARK_SKIN_TONE = 0x1F3FE
        private const val CP_DARK_SKIN_TONE = 0x1F3FF
        private const val CP_RED_HAIR = 0x1F9B0
        private const val CP_CURLY_HAIR = 0x1F9B1
        private const val CP_WHITE_HAIR = 0x1F9B3
        private const val CP_BARLD = 0x1F9B2
        private const val CP_VARIANT_SELECTOR = 0xFE0F

        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_A = 0x1F1E6
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_B = 0x1F1E7
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_C = 0x1F1E8
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_D = 0x1F1E9
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_E = 0x1F1EA
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_F = 0x1F1EB
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_G = 0x1F1EC
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_H = 0x1F1ED
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_I = 0x1F1EE
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_J = 0x1F1EF
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_K = 0x1F1F0
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_L = 0x1F1F1
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_M = 0x1F1F2
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_N = 0x1F1F3
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_O = 0x1F1F4
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_P = 0x1F1F5
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_Q = 0x1F1F6
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_R = 0x1F1F7
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_S = 0x1F1F8
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_T = 0x1F1F9
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_U = 0x1F1FA
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_V = 0x1F1FB
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_W = 0x1F1FC
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_X = 0x1F1FD
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_Y = 0x1F1FE
        private const val CP_REGIONAL_INDICATOR_SYMBOL_LETTER_Z = 0x1F1FF
    }


}
