// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.util.Log
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.KeyData
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.toTextKey
import org.dslul.openboard.inputmethod.latin.common.splitOnWhitespace

/**
 *  Parser for simple layouts, defined only as rows of (normal) keys with moreKeys.
 *  There may be a short "extra row" for the configurable keys in the bottom row. This is two keys
 *  for alphabet, 3 keys for symbols and 4 keys for shift symbols. MoreKeys on period and comma get
 *  merged with defaults.
 */
class SimpleKeyboardParser(private val params: KeyboardParams, private val context: Context) : KeyboardParser(params, context) {
    private val addExtraKeys =
        params.mId.locale.language != "eo"
            && params.mId.mSubtype.keyboardLayoutSetName in listOf("nordic", "spanish", "german", "swiss", "serbian_qwertz")

    override fun getLayoutFromAssets(layoutName: String) =
        context.assets.open("layouts/${getSimpleLayoutName(layoutName)}.txt").reader().readText()

    override fun parseCoreLayout(layoutContent: String): MutableList<List<KeyData>> {
        val rowStrings = layoutContent.replace("\r\n", "\n").split("\n\n")
        return rowStrings.mapIndexedTo(mutableListOf()) { i, row ->
            if (addExtraKeys)
                getExtraKeys(i)?.let { parseRow(row) + it } ?: parseRow(row)
            else
                parseRow(row)
        }
    }

    private fun parseRow(row: String): List<KeyData> =
        row.split("\n").mapNotNull {
            if (it.isBlank()) null
            else parseKey(it)
        }

    private fun getExtraKeys(rowIndex: Int) = params.mLocaleKeyTexts.getExtraKeys(rowIndex + 1)

    private fun parseKey(key: String): KeyData {
        val split = key.splitOnWhitespace()
        return if (split.size == 1)
                split.first().toTextKey()
            else
                split.first().toTextKey(split.drop(1))
    }

}
