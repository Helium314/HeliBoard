// SPDX-License-Identifier: GPL-3.0-only

package com.majeur.inputmethod.tools.emoji

import java.io.InputStreamReader
import java.io.LineNumberReader
import java.lang.IllegalStateException

abstract class TextFileParser<T> {

    private var parsed = false

    fun parse(resource: String) {
        if (parsed) throw IllegalStateException("parse() has already been called")
        LineNumberReader(InputStreamReader(JarUtils.openResource(resource))).use { reader ->
            reader.lines().forEach { content ->
                parseLine(content)
            }
        }
        parsed = true
    }

    fun getParsedData(): T {
        if (!parsed) throw IllegalStateException("parse() must be called before calling getParsedData()")
        return getParseResult()
    }

    protected fun ifStartsWith(content: String, vararg pairs: Pair<String, (String) -> Unit>) : Boolean {
        pairs.forEach { pair ->
            if (ifStartsWith(content, pair.first, pair.second)) return true
        }
        return false
    }

    protected fun ifStartsWith(content: String, prefix: String, call: (String) -> Unit) : Boolean {
        if (content.startsWith(prefix)) {
            call.invoke(content.removePrefix(prefix).trim())
            return true
        }
        return false
    }

    protected abstract fun getParseResult(): T

    protected abstract fun parseLine(content: String)

}