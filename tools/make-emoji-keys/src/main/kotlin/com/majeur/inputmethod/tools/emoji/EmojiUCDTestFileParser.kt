// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji

import com.majeur.inputmethod.tools.emoji.model.EmojiData
import com.majeur.inputmethod.tools.emoji.model.EmojiGroup

class EmojiUCDTestFileParser: TextFileParser<EmojiData>() {

    private var count = 0
    private var emojiData = EmojiData()

    private var currentGroup = EmojiGroup.SMILEYS_AND_EMOTION

    override fun getParseResult() = emojiData

    override fun parseLine(content: String) {
        ifStartsWith(content,
            "#" to ::parseComment,
            "" to ::parseEmojiSpec
        )
    }

    private fun parseComment(content: String) {
        ifStartsWith(content,
            PROP_DATE to { emojiData.dataDate = it},
            PROP_UNICODE_VER to {
                emojiData.unicodeVersion = it
                println("Parsing emoji table from Unicode $it")
            },
            PROP_GROUP to ::parseGroup,
            PROP_SUBGROUP to { },
            "${currentGroup.rawName} subtotal:" to ::parseGroupSubtotal,
            EOF to { println("Parsed a total of $count emojis") }
        )
    }

    private fun parseGroup(content: String) {
        currentGroup = EmojiGroup.get(content)
    }

    private fun parseGroupSubtotal(content: String) {
        if (content.contains("w/o modifiers")) return
        val expected = content.toInt()
        val count = emojiData.emojiGroupCount(currentGroup)
        println(" - $count/$expected emojis for group ${currentGroup.rawName}")
    }

    private fun parseEmojiSpec(content: String) {
        if (content.isEmpty()) return

        val codePoints = content
                .substringBefore(';')
                .trim()
        val status = content
                .substringAfter(';')
                .substringBefore('#')
                .trim()
        val extras = content.substringAfter('#')

        if (status != "fully-qualified") return

        val rawVersion = EMOJI_VERSION_REGEX.find(extras)?.value ?: "O.0"
        val version = rawVersion.toFloat()
        val name = extras
                .substringAfter(rawVersion)
                .trim()

        val cps = codePoints
                .split(" ")
                .map { it.toInt(radix = 16) }
                .toIntArray()

        emojiData.insertEmoji(currentGroup, cps, version, name)
        count++
    }

    companion object {

        private const val PROP_UNICODE_VER = "Version:"
        private const val PROP_DATE = "Date:"
        private const val PROP_GROUP = "group:"
        private const val PROP_SUBGROUP = "subgroup:"
        private const val EOF = "EOF"

        private val EMOJI_VERSION_REGEX = "[0-9]*[.]?[0-9]+".toRegex()
    }


}