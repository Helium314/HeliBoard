// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji

import com.majeur.inputmethod.tools.emoji.model.EmojiData
import com.majeur.inputmethod.tools.emoji.model.EmojiGroup
import java.io.*
import java.nio.charset.Charset
import java.util.jar.JarFile

class EmojiCategoriesResource(private val jarFile: JarFile) {

    fun writeToAndroidRes(outDir: String?, emojiData: EmojiData, supportData: Map<Int, Int>) {
        val template = JarUtils.getAndroidResTemplateResource(jarFile)
        val resourceDir = template.substring(0, template.lastIndexOf('/'))
        var ps: PrintStream? = null
        var lnr: LineNumberReader? = null
        try {
            ps = if (outDir == null) {
                System.out
            } else {
                val outDir = File(outDir, resourceDir)
                val outputFile = File(outDir,
                        ANDROID_RES_TEMPLATE.replace(".tmpl", ".xml"))
                outDir.mkdirs()
                println("Building android resource file into ${outputFile.absoluteFile}")
                PrintStream(outputFile, Charset.forName("UTF-8"))
            }
            lnr = LineNumberReader(InputStreamReader(JarUtils.openResource(template), Charset.forName("UTF-8")))
            inflateTemplate(lnr, ps!!, emojiData, supportData)
        } catch (e: IOException) {
            throw RuntimeException(e)
        } finally {
            JarUtils.close(lnr)
            JarUtils.close(ps)
        }
    }

    @Throws(IOException::class)
    private fun inflateTemplate(reader: LineNumberReader, out: PrintStream,
                                emojis: EmojiData, supportData: Map<Int, Int>) {
        reader.lines().forEach {
            when {
                it.contains(MARK_UNICODE_VER) ->
                    out.println(it.replace(MARK_UNICODE_VER, emojis.unicodeVersion))
                it.contains(MARK_API_LEVEL) ->
                    out.println(it.replace(MARK_API_LEVEL, supportData.values.maxOrNull().toString()))
                it.contains(MARK_SMILEYS_AND_EMOTION) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.SMILEYS_AND_EMOTION)
                it.contains(MARK_PEOPLE_AND_BODY) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.PEOPLE_AND_BODY)
                it.contains(MARK_ANIMALS_AND_NATURE) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.ANIMALS_AND_NATURE)
                it.contains(MARK_FOOD_AND_DRINK) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.FOOD_AND_DRINK)
                it.contains(MARK_TRAVEL_AND_PLACES) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.TRAVEL_AND_PLACES)
                it.contains(MARK_ACTIVITIES) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.ACTIVITIES)
                it.contains(MARK_OBJECTS) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.OBJECTS)
                it.contains(MARK_SYMBOLS) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.SYMBOLS)
                it.contains(MARK_FLAGS) ->
                    dumpEmojiSpecs(out, emojis, supportData,EmojiGroup.FLAGS)
                it.contains(MARK_PEOPLE_AND_BODY_MORE) ->
                    dumpEmojiSpecsVariant(out, emojis, supportData,EmojiGroup.PEOPLE_AND_BODY)
                else -> out.println(it)
            }
        }
    }

    private fun dumpEmojiSpecs(out: PrintStream, emojiData: EmojiData, supportData: Map<Int, Int>,
                               group: EmojiGroup) {
        emojiData[group].forEach { emoji ->
            val minApi = getMinApi(emoji.codes, supportData)
            if (minApi < 0) {
                // We have no clue of which android version supports this emoji,
                // so we ignore it.
                printCompatNotFound(emoji.codes)
                return@forEach
            }
            val text = makeEmojiKey(emoji.codes, minApi)
            out.println("        <item>$text</item>")
        }
    }

    private fun dumpEmojiSpecsVariant(out: PrintStream, emojiData: EmojiData, supportData: Map<Int, Int>,
                               group: EmojiGroup) {
        emojiData[group].forEach { baseEmoji ->
            val minApi = getMinApi(baseEmoji.codes, supportData)
            if (minApi < 0) {
                // Same thing, we already encountered it when dumping base emoji,
                // ignoring this one silently.
                return@forEach
            }

            val text = baseEmoji.variants.filter { emoji ->
                if (getMinApi(emoji.codes, supportData) < 0) {
                    // Again
                    printCompatNotFound(emoji.codes)
                    return@filter false
                }
                true
            }.map { emoji ->
                // Not very efficient, minApi is accessed twice,
                // but hey, we are making tooling here
                makeEmojiKey(emoji.codes, getMinApi(emoji.codes, supportData))
            }.filter { key ->
                key.isNotBlank()
            }.joinToString(separator = ";")

            if (text.isNotBlank()) out.println("        <item>$text</item>")
            else out.println("        <item/>")
        }
    }

    private fun makeEmojiKey(codes: IntArray, minApi: Int): String {
        val cps = codes
                .joinToString(separator = ",") {
                    it.toString(radix = 16)
                            .uppercase()
                }
        return if (minApi > 19) "$cps||$minApi" else cps
    }

    private fun getMinApi(codes: IntArray, supportData: Map<Int, Int>): Int {
        val hash = codes
                .joinToString(separator = "")
                .hashCode()
        return supportData[hash] ?: -1
    }

    private fun printCompatNotFound(codes: IntArray) {
        val formattedCps = codes.joinToString(" ") { "U+" + it.toString(radix = 16).uppercase() }
        println(" - No android compatibility found for emoji $formattedCps, ignoring...")
    }

    companion object {
        private const val ANDROID_RES_TEMPLATE = "emoji-categories.tmpl"
        private const val MARK_UNICODE_VER = "@UNICODE_VERSION@"
        private const val MARK_API_LEVEL = "@ANDROID_API_LEVEL@"
        private const val MARK_SMILEYS_AND_EMOTION = "@SMILEYS_AND_EMOTION@"
        private const val MARK_PEOPLE_AND_BODY = "@PEOPLE_AND_BODY@"
        private const val MARK_PEOPLE_AND_BODY_MORE = "@PEOPLE_AND_BODY MORE@"
        private const val MARK_ANIMALS_AND_NATURE = "@ANIMALS_AND_NATURE@"
        private const val MARK_FOOD_AND_DRINK = "@FOOD_AND_DRINKS@"
        private const val MARK_TRAVEL_AND_PLACES = "@TRAVEL_AND_PLACES@"
        private const val MARK_ACTIVITIES = "@ACTIVITIES@"
        private const val MARK_OBJECTS = "@OBJECTS@"
        private const val MARK_SYMBOLS = "@SYMBOLS@"
        private const val MARK_FLAGS = "@FLAGS@"
    }

}