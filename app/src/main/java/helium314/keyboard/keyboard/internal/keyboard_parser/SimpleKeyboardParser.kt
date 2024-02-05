// SPDX-License-Identifier: GPL-3.0-only
package helium314.keyboard.keyboard.internal.keyboard_parser

import android.content.Context
import helium314.keyboard.keyboard.internal.KeyboardParams
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.KeyData
import helium314.keyboard.keyboard.internal.keyboard_parser.floris.toTextKey
import helium314.keyboard.latin.common.splitOnWhitespace

/**
 *  Parser for simple layouts, defined only as rows of (normal) keys with popup keys.
 *  There may be a short "extra row" for the configurable keys in the bottom row. This is two keys
 *  for alphabet, 3 keys for symbols and 4 keys for shift symbols. Popup keys on period and comma get
 *  merged with defaults.
 */
class SimpleKeyboardParser(
    private val params: KeyboardParams,
    context: Context,
    private val addExtraKeys: Boolean = params.mId.mSubtype.keyboardLayoutSetName.endsWith("+") && params.mId.isAlphabetKeyboard
) : KeyboardParser(params, context) {
    override fun parseCoreLayout(layoutContent: String): MutableList<List<KeyData>> {
        val rowStrings = layoutContent.replace("\r\n", "\n").split("\\n\\s*\\n".toRegex())
        return rowStrings.mapIndexedNotNullTo(mutableListOf()) { i, row ->
            if (row.isBlank()) return@mapIndexedNotNullTo null
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
