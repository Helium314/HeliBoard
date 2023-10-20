// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji

import java.util.*
import kotlin.system.exitProcess

class MakeEmojiKeys {

    class Options(argsArray: Array<String>) {

        private val OPTION_RES = "-res"

        var resPath: String? = null

        init {
            val args = listOf(*argsArray).toMutableList()
            var arg: String? = null
            try {
                while (args.isNotEmpty()) {
                    arg = args.removeFirst()
                    if (arg == OPTION_RES) {
                        resPath = args.removeFirst()
                    } else {
                        usage("Unknown option: $arg")
                    }
                }
            } catch (e: NoSuchElementException) {
                usage("Option $arg needs argument")
            }
        }

        fun usage(message: String?) {
            message?.let { System.err.println(it) }
            System.err.println("usage: make-emoji-keys $OPTION_RES <res_output_dir>")
            exitProcess(1)
        }
    }

    companion object {

        @JvmStatic fun main(args: Array<String>) {
            val options = Options(args)
            val jar = JarUtils.getJarFile(Companion::class.java)

            val parser = EmojiUCDTestFileParser()
            parser.parse(JarUtils.getLatestEmojiTestResource(jar))
            val emojis = parser.getParsedData()

            val parser2 = AndroidEmojiSupportFileParser()
            parser2.parse(JarUtils.getEmojiSupportResource(jar))
            val supportData = parser2.getParsedData()

            EmojiCategoriesResource(jar).writeToAndroidRes(options.resPath, emojis, supportData)
        }

    }
}