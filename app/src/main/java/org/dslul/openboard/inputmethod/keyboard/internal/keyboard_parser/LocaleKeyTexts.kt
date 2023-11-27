// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.KeyData
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.toTextKey
import org.dslul.openboard.inputmethod.latin.common.splitOnWhitespace
import java.io.InputStream
import java.util.Locale
import kotlin.math.round

class LocaleKeyTexts(dataStream: InputStream?, locale: Locale) {
    private val moreKeys = hashMapOf<String, Array<String>>()
    private val extraKeys = Array<MutableList<KeyData>?>(5) { null }
    var labelSymbol = "\\?123"
        private set
    var labelAlphabet = "ABC"
        private set
    var labelShiftSymbol = "= \\\\ <"
        private set
    var labelComma = ","
        private set
    var labelPeriod = "."
        private set
    val currencyKey = getCurrencyKey(locale)
    private var numberKeys = ((1..9) + 0).map { it.toString() }
    private val numbersMoreKeys = arrayOf(
        mutableListOf("¹", "½", "⅓","¼", "⅛"),
        mutableListOf("²", "⅔"),
        mutableListOf("³", "¾", "⅜"),
        mutableListOf("⁴"),
        mutableListOf("⅝"),
        mutableListOf(),
        mutableListOf("⅞"),
        mutableListOf(),
        mutableListOf(),
        mutableListOf("ⁿ", "∅"),
    )

    init {
        readStream(dataStream, false)
        // set default quote moreKeys if necessary
        // should this also be done with punctuation moreKeys?
        if ("\'" !in moreKeys)
            moreKeys["\'"] = arrayOf("‚", "‘", "’", "‹", "›")
        if ("\"" !in moreKeys)
            moreKeys["\""] = arrayOf("„", "“", "”", "«", "»")
        if ("!" !in moreKeys)
            moreKeys["!"] = arrayOf("¡")
        if ("?" !in moreKeys)
            moreKeys["?"] = arrayOf("¿")
    }

    private fun readStream(stream: InputStream?, onlyMoreKeys: Boolean) {
        if (stream == null) return
        stream.reader().use { reader ->
            var mode = READER_MODE_NONE
            val colonSpaceRegex = ":\\s+".toRegex()
            reader.forEachLine { l ->
                val line = l.trim()
                when (line) {
                    "[morekeys]" -> { mode = READER_MODE_MORE_KEYS; return@forEachLine }
                    "[extra_keys]" -> { mode = READER_MODE_EXTRA_KEYS; return@forEachLine }
                    "[labels]" -> { mode = READER_MODE_LABELS; return@forEachLine }
                    "[number_row]" -> { mode = READER_MODE_NUMBER_ROW; return@forEachLine }
                }
                when (mode) {
                    READER_MODE_MORE_KEYS -> addMoreKeys(line.splitOnWhitespace())
                    READER_MODE_EXTRA_KEYS -> if (!onlyMoreKeys) addExtraKey(line.split(colonSpaceRegex, 2))
                    READER_MODE_LABELS -> if (!onlyMoreKeys) addLabel(line.split(colonSpaceRegex, 2))
                    READER_MODE_NUMBER_ROW -> if (!onlyMoreKeys) setNumberRow(line.splitOnWhitespace())
                }
            }
        }
    }

    // need tp provide a copy because some functions like MoreKeySpec.insertAdditionalMoreKeys may modify the array
    fun getMoreKeys(label: String): Array<String>? = moreKeys[label]?.copyOf()

    // used by simple parser only, but could be possible for json as well (if necessary)
    fun getExtraKeys(row: Int): List<KeyData>? =
        if (row > extraKeys.size) null
            else extraKeys[row]

    fun addFile(dataStream: InputStream?) {
        readStream(dataStream, true)
    }

    private fun addMoreKeys(split: List<String>) {
        if (split.size == 1) return
        val existingMoreKeys = moreKeys[split.first()]
        if (existingMoreKeys == null)
            moreKeys[split.first()] = Array(split.size - 1) { split[it + 1] }
        else
            moreKeys[split.first()] = mergeMoreKeys(existingMoreKeys, split.drop(1))
    }

    private fun addExtraKey(split: List<String>) {
        if (split.size < 2) return
        val row = split.first().toIntOrNull() ?: return
        val keys = split.last().splitOnWhitespace()
        if (extraKeys[row] == null)
            extraKeys[row] = mutableListOf()
        extraKeys[row]?.add(keys.first().toTextKey(keys.drop(1)))
    }

    private fun addLabel(split: List<String>) {
        if (split.size < 2) return
        when (split.first()) {
            "symbol" -> labelSymbol = split.last()
            "alphabet" -> labelAlphabet = split.last()
            "shift_symbol" -> labelShiftSymbol = split.last() // never used, but could be...
            "comma" -> labelComma = split.last()
            "period" -> labelPeriod = split.last()
        }
    }

    // set number row only, does not affect moreKeys
    // setting more than 10 number keys will cause crashes, but could actually be implemented at some point
    private fun setNumberRow(split: List<String>) {
        if (numberKeys == split) return
        numberKeys.forEachIndexed { i, n -> numbersMoreKeys[i].add(0, n) }
        numberKeys = split
    }

    // get number row including moreKeys
    fun getNumberRow(): List<KeyData> =
        numberKeys.mapIndexed { i, label ->
            label.toTextKey(numbersMoreKeys[i])
        }

    // get moreKeys with the number itself (as used on alphabet keyboards)
    fun getNumberMoreKeys(numberIndex: Int?): List<String> {
        if (numberIndex == null) return emptyList()
        return listOf(numberKeys[numberIndex]) + numbersMoreKeys[numberIndex]
    }
}

private fun mergeMoreKeys(original: Array<String>, added: List<String>): Array<String> {
    val markerIndexInOriginal = original.indexOf("%")
    val markerIndexInAddedIndex = added.indexOf("%")
    val moreKeys = mutableSetOf<String>()
    if (markerIndexInOriginal != -1 && markerIndexInAddedIndex != -1) {
        // add original and then added until %
        original.forEachIndexed { index, s -> if (index < markerIndexInOriginal) moreKeys.add(s) }
        added.forEachIndexed { index, s -> if (index < markerIndexInAddedIndex) moreKeys.add(s) }
        // add % and remaining moreKeys
        original.forEachIndexed { index, s -> if (index >= markerIndexInOriginal) moreKeys.add(s) }
        added.forEachIndexed { index, s -> if (index > markerIndexInAddedIndex) moreKeys.add(s) }
    } else if (markerIndexInOriginal != -1) {
        // add original until %, then added, then remaining original
        original.forEachIndexed { index, s -> if (index <= markerIndexInOriginal) moreKeys.add(s) }
        moreKeys.addAll(added)
        original.forEachIndexed { index, s -> if (index > markerIndexInOriginal) moreKeys.add(s) }
    } else if (markerIndexInAddedIndex != -1) {
        // add added until %, then original, then remaining added
        added.forEachIndexed { index, s -> if (index <= markerIndexInAddedIndex) moreKeys.add(s) }
        moreKeys.addAll(original)
        added.forEachIndexed { index, s -> if (index > markerIndexInAddedIndex) moreKeys.add(s) }
    } else {
        // use original, then added
        moreKeys.addAll(original)
        moreKeys.addAll(added)
    }
    // in fact this is only special treatment for the punctuation moreKeys
    if (moreKeys.any { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }) {
        val originalColumnCount = original.firstOrNull { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }
            ?.substringAfter(Key.MORE_KEYS_AUTO_COLUMN_ORDER)?.toIntOrNull()
        val l = moreKeys.filterNot { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }
        if (originalColumnCount != null && moreKeys.size <= 20 // not for too wide layout
            && originalColumnCount == round((original.size - 1 + 0.1f) / 2f).toInt()) { // +0.1 f against rounding issues
            // we had 2 rows, and want it again
            return (l + "${Key.MORE_KEYS_AUTO_COLUMN_ORDER}${round(l.size / 2f).toInt()}").toTypedArray()
        }
        // just drop autoColumnOrder otherwise
        return l.toTypedArray()
    }
    return moreKeys.toTypedArray()
}

fun addLocaleKeyTextsToParams(context: Context, params: KeyboardParams, moreKeysSetting: Int) {
    val locales = params.mSecondaryLocales + params.mId.locale
    params.mLocaleKeyTexts = moreKeysAndLabels.getOrPut(locales.joinToString { it.toString() }) {
        createLocaleKeyTexts(context, params, moreKeysSetting)
    }
}

private fun createLocaleKeyTexts(context: Context, params: KeyboardParams, moreKeysSetting: Int): LocaleKeyTexts {
    val lkt = LocaleKeyTexts(getStreamForLocale(params.mId.locale, context), params.mId.locale)
    if (moreKeysSetting == MORE_KEYS_MORE)
        lkt.addFile(context.assets.open("$LANGUAGE_TEXTS_FOLDER/all_more_keys.txt"))
    else if (moreKeysSetting == MORE_KEYS_ALL)
        lkt.addFile(context.assets.open("$LANGUAGE_TEXTS_FOLDER/more_more_keys.txt"))
    params.mSecondaryLocales.forEach { locale ->
        if (locale == params.mId.locale) return@forEach
        lkt.addFile(getStreamForLocale(locale, context))
    }
    return lkt
}

private fun getStreamForLocale(locale: Locale, context: Context) =
    try {
        if (locale.toString() == "zz") context.assets.open("$LANGUAGE_TEXTS_FOLDER/more_more_keys.txt")
        else context.assets.open("$LANGUAGE_TEXTS_FOLDER/${locale.toString().lowercase()}.txt")
    } catch (_: Exception) {
        try {
            context.assets.open("$LANGUAGE_TEXTS_FOLDER/${locale.language.lowercase()}.txt")
        } catch (_: Exception) {
            null
        }
    }

fun clearCache() = moreKeysAndLabels.clear()

// cache the texts, so they don't need to be read over and over
private val moreKeysAndLabels = hashMapOf<String, LocaleKeyTexts>()

private const val READER_MODE_NONE = 0
private const val READER_MODE_MORE_KEYS = 1
private const val READER_MODE_EXTRA_KEYS = 2
private const val READER_MODE_LABELS = 3
private const val READER_MODE_NUMBER_ROW = 4

// probably could be improved and extended, currently this is what's done in key_styles_currency.xml
private fun getCurrencyKey(locale: Locale): Pair<String, Array<String>> {
    if (locale.country.matches(euroCountries))
        return euro
    if (locale.toString().matches(euroLocales))
        return euro
    if (locale.language.matches("ca|eu|lb|mt".toRegex()))
        return euro
    if (locale.language.matches("fa|iw|ko|lo|mn|ne|si|th|uk|vi|km".toRegex()))
        return genericCurrencyKey(getCurrency(locale))
    if (locale.language == "hy")
        return dram
    if (locale.language == "tr")
        return lira
    if (locale.language == "ru")
        return ruble
    if (locale.country == "LK" || locale.country == "BD")
        return genericCurrencyKey(getCurrency(locale))
    if (locale.country == "IN" && locale.language == "ta")
        return genericCurrencyKey("௹")
    if (locale.country == "IN" || locale.language.matches("hi|kn|ml|mr|ta|te".toRegex()))
        return rupee
    if (locale.country == "GB")
        return pound
    return genericCurrencyKey("$")
}

private fun genericCurrencyKey(currency: String) = currency to genericCurrencyMoreKeys
private val genericCurrencyMoreKeys = arrayOf("$", "¢", "£", "€", "¥", "₱")

private fun getCurrency(locale: Locale): String {
    if (locale.country == "BD") return "৳"
    if (locale.country == "LK") return "රු"
    return when (locale.language) {
        "fa" -> "﷼"
        "iw" -> "₪"
        "ko" -> "￦"
        "lo" -> "₭"
        "mn" -> "₮"
        "ne" -> "रु."
        "si" -> "රු"
        "th" -> "฿"
        "uk" -> "₴"
        "vi" -> "₫"
        "km" -> "៛"
        else -> "$"
    }
}

// needs at least 4 moreKeys for working shift-symbol keyboard
private val euro = "€" to arrayOf("¢", "£", "$", "¥", "₱")
private val dram = "֏" to arrayOf("€", "$", "₽", "¥", "£")
private val rupee = "₹" to arrayOf("¢", "£", "€", "¥", "₱")
private val pound = "£" to arrayOf("¢", "$", "€", "¥", "₱")
private val ruble = "₽" to arrayOf("€", "$", "£", "¥")
private val lira = "₺" to arrayOf("€", "$", "£", "¥")
private val euroCountries = "AD|AT|BE|BG|HR|CY|CZ|DA|EE|FI|FR|DE|GR|HU|IE|IT|XK|LV|LT|LU|MT|MO|ME|NL|PL|PT|RO|SM|SK|SI|ES|VA".toRegex()
private val euroLocales = "bg|ca|cs|da|de|el|en|es|et|eu|fi|fr|ga|gl|hr|hu|it|lb|lt|lv|mt|nl|pl|pt|ro|sk|sl|sq|sr|sv".toRegex()

const val MORE_KEYS_ALL = 2;
const val MORE_KEYS_MORE = 1;
const val MORE_KEYS_NORMAL = 0;

const val LANGUAGE_TEXTS_FOLDER = "language_key_texts"
