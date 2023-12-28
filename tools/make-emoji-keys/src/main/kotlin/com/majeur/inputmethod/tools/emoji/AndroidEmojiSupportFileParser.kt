// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji


class AndroidEmojiSupportFileParser : TextFileParser<Map<Int, Int>>() {

    private val map = mutableMapOf<Int, Int>()
    private var currentApiLevel = 0

    override fun getParseResult() = map

    override fun parseLine(content: String) {
        ifStartsWith(content,
            API_LEVEL_MARK to ::parseApiLevel,
            UNICODE_MARK to ::parseCodePoints)
    }

    private fun parseApiLevel(content: String) {
        currentApiLevel = content
                .substringBefore("#")
                .trim()
                .toInt()
    }

    private fun parseCodePoints(content: String) {
        val codePointsHash = content
                .substringBefore("#")
                .trim()
                .split(" ")
                .map { it
                        .trim()
                        .removePrefix("U+")
                        .toInt(radix = 16) }
                .joinToString(separator = "")
                .hashCode()
        map[codePointsHash] = currentApiLevel
    }

    companion object {

        private const val API_LEVEL_MARK = "@"
        private const val UNICODE_MARK = "U"
    }
}