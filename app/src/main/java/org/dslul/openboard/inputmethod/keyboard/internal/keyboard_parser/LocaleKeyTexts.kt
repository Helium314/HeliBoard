// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.KeyboardId
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.KeyData
import org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser.floris.toTextKey
import org.dslul.openboard.inputmethod.latin.common.splitOnFirstSpacesOnly
import org.dslul.openboard.inputmethod.latin.common.splitOnWhitespace
import org.dslul.openboard.inputmethod.latin.settings.Settings
import java.io.InputStream
import java.util.Locale
import kotlin.math.round

class LocaleKeyTexts(dataStream: InputStream?, locale: Locale) {
    private val moreKeys = hashMapOf<String, Array<String>>() // todo: no need for arrays any more, better use a list?
    private val priorityMoreKeys = hashMapOf<String, Array<String>>()
    private val extraKeys = Array<MutableList<KeyData>?>(5) { null }
    var labelSymbol = "\\?123"
        private set
    var labelAlphabet = "ABC"
        private set
    private var labelShiftSymbol = "= \\\\ <"
    private var labelShiftSymbolTablet = "~ [ <"
    var labelComma = ","
        private set
    var labelPeriod = "."
        private set
    private var labelQuestion = "?"
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
            moreKeys["\'"] = arrayOf("!fixedColumnOrder!5", "‚", "‘", "’", "‹", "›")
        if ("\"" !in moreKeys)
            moreKeys["\""] = arrayOf("!fixedColumnOrder!5", "„", "“", "”", "«", "»")
        if ("!" !in moreKeys)
            moreKeys["!"] = arrayOf("¡")
        if (labelQuestion !in moreKeys)
            moreKeys[labelQuestion] = if (labelQuestion == "?") arrayOf("¿") else arrayOf("?", "¿")
        if ("punctuation" !in moreKeys)
            moreKeys["punctuation"] = arrayOf("${Key.MORE_KEYS_AUTO_COLUMN_ORDER}8", "\\,", "?", "!", "#", ")", "(", "/", ";", "'", "@", ":", "-", "\"", "+", "\\%", "&")
    }

    private fun readStream(stream: InputStream?, onlyMoreKeys: Boolean) {
        if (stream == null) return
        stream.reader().use { reader ->
            var mode = READER_MODE_NONE
            val colonSpaceRegex = ":\\s+".toRegex()
            reader.forEachLine { l ->
                val line = l.trim()
                if (line.isEmpty()) return@forEachLine
                when (line) {
                    "[morekeys]" -> { mode = READER_MODE_MORE_KEYS; return@forEachLine }
                    "[extra_keys]" -> { mode = READER_MODE_EXTRA_KEYS; return@forEachLine }
                    "[labels]" -> { mode = READER_MODE_LABELS; return@forEachLine }
                    "[number_row]" -> { mode = READER_MODE_NUMBER_ROW; return@forEachLine }
                }
                when (mode) {
                    READER_MODE_MORE_KEYS -> addMoreKeys(line)
                    READER_MODE_EXTRA_KEYS -> if (!onlyMoreKeys) addExtraKey(line.split(colonSpaceRegex, 2))
                    READER_MODE_LABELS -> if (!onlyMoreKeys) addLabel(line.split(colonSpaceRegex, 2))
                    READER_MODE_NUMBER_ROW -> setNumberRow(line.splitOnWhitespace(), onlyMoreKeys)
                }
            }
        }
    }

    /** Pair(extraKeysLeft, extraKeysRight) */
    // todo: they should be optional, or will unexpectedly appear on custom layouts
    fun getTabletExtraKeys(elementId: Int): Pair<List<KeyData>, List<KeyData>> {
        val flags = Key.LABEL_FLAGS_FONT_DEFAULT
        return when (elementId) {
            KeyboardId.ELEMENT_SYMBOLS -> listOf("\\".toTextKey(labelFlags = flags), "=".toTextKey(labelFlags = flags)) to emptyList()
            KeyboardId.ELEMENT_SYMBOLS_SHIFTED -> emptyList<KeyData>() to listOf("¡".toTextKey(labelFlags = flags), "¿".toTextKey(labelFlags = flags))
            else -> emptyList<KeyData>() to listOf("!".toTextKey(labelFlags = flags), labelQuestion.toTextKey(labelFlags = flags)) // assume alphabet
        }
    }

    fun getShiftSymbolLabel(isTablet: Boolean) = if (isTablet) labelShiftSymbolTablet else labelShiftSymbol

    fun getMoreKeys(label: String): Array<String>? = moreKeys[label]
    fun getPriorityMoreKeys(label: String): Array<String>? = priorityMoreKeys[label]

    // used by simple parser only, but could be possible for json as well (if necessary)
    fun getExtraKeys(row: Int): List<KeyData>? =
        if (row > extraKeys.size) null
            else extraKeys[row]

    fun addFile(dataStream: InputStream?) {
        readStream(dataStream, true)
    }

    private fun addMoreKeys(line: String) {
        val split = if (line.contains("|"))
                // if a moreKey contains label/code separately, there are cases where space can be in there too
                // normally this should work for all moreKeys, but if we split them on whitespace there is less chance for unnecessary issues
                line.splitOnFirstSpacesOnly()
            else line.splitOnWhitespace()
        if (split.size == 1) return
        val key = split.first()
        val priorityMarkerIndex = split.indexOf("%")
        if (priorityMarkerIndex > 0) {
            val existingPriorityMoreKeys = priorityMoreKeys[key]
            priorityMoreKeys[key] = if (existingPriorityMoreKeys == null)
                    Array(priorityMarkerIndex - 1) { split[it + 1] }
                else existingPriorityMoreKeys + split.subList(1, priorityMarkerIndex)
            val existingMoreKeys = moreKeys[key]
            moreKeys[key] = if (existingMoreKeys == null)
                    Array(split.size - priorityMarkerIndex - 1) { split[it + priorityMarkerIndex + 1] }
                else existingMoreKeys + split.subList(priorityMarkerIndex, split.size)
        } else {
            // a but more special treatment, this should not occur together with priority marker (but technically could)
            val existingMoreKeys = moreKeys[key]
            val newMoreKeys = if (existingMoreKeys == null)
                    Array(split.size - 1) { split[it + 1] }
                else mergeMoreKeys(existingMoreKeys, split.drop(1))
            moreKeys[key] = when (key) {
                "'", "\"", "«", "»" -> addFixedColumnOrder(newMoreKeys)
                else -> newMoreKeys
            }
        }
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
            "shift_symbol_tablet" -> labelShiftSymbolTablet = split.last() // never used, but could be...
            "comma" -> labelComma = split.last()
            "period" -> labelPeriod = split.last()
            "question" -> labelQuestion = split.last()
        }
    }

    // set number row only, does not affect moreKeys
    // setting more than 10 number keys will cause crashes, but could actually be implemented at some point
    private fun setNumberRow(split: List<String>, onlyAddToMoreKeys: Boolean) {
        if (onlyAddToMoreKeys) {
            // as of now this should never be used, but better have it
            numberKeys.forEachIndexed { i, n ->
                if (numberKeys[i] != n && n !in numbersMoreKeys[i])
                    numbersMoreKeys[i].add(0, n)
            }
            return
        }
        if (Settings.getInstance().current.mLocalizedNumberRow) {
            numberKeys.forEachIndexed { i, n -> numbersMoreKeys[i].add(0, n) }
            numberKeys = split
        } else {
            split.forEachIndexed { i, n -> numbersMoreKeys[i].add(0, n) }
        }
    }

    // get number row including moreKeys
    fun getNumberRow(): List<KeyData> =
        numberKeys.mapIndexed { i, label ->
            label.toTextKey(numbersMoreKeys[i])
        }

    fun getNumberLabel(numberIndex: Int?): String? = numberIndex?.let { numberKeys.getOrNull(it) }
}

private fun mergeMoreKeys(original: Array<String>, added: List<String>): Array<String> {
    if (original.any { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) } || added.any { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }) {
        val moreKeys = (original + added).toSet()
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
    return original + added
}

private fun addFixedColumnOrder(moreKeys: Array<String>): Array<String> {
    if (moreKeys.none { it.startsWith(Key.MORE_KEYS_FIXED_COLUMN_ORDER) })
        return arrayOf("${Key.MORE_KEYS_FIXED_COLUMN_ORDER}${moreKeys.size}", *moreKeys)
    val newMoreKeys = moreKeys.filterNot { it.startsWith(Key.MORE_KEYS_FIXED_COLUMN_ORDER) }
    return Array(newMoreKeys.size + 1) {
        if (it == 0) "${Key.MORE_KEYS_FIXED_COLUMN_ORDER}${newMoreKeys.size}"
        else newMoreKeys[it - 1]
    }
}

fun getOrCreate(context: Context, locale: Locale): LocaleKeyTexts =
    localeKeyTextsCache.getOrPut(locale.toString()) {
        LocaleKeyTexts(getStreamForLocale(locale, context), locale)
    }

fun addLocaleKeyTextsToParams(context: Context, params: KeyboardParams, moreKeysSetting: Int) {
    val locales = params.mSecondaryLocales + params.mId.locale
    params.mLocaleKeyTexts = localeKeyTextsCache.getOrPut(locales.joinToString { it.toString() }) {
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
        else context.assets.open("$LANGUAGE_TEXTS_FOLDER/${locale.toLanguageTag()}.txt")
    } catch (_: Exception) {
        try {
            context.assets.open("$LANGUAGE_TEXTS_FOLDER/${locale.language}.txt")
        } catch (_: Exception) {
            null
        }
    }

fun clearCache() = localeKeyTextsCache.clear()

// cache the texts, so they don't need to be read over and over
private val localeKeyTextsCache = hashMapOf<String, LocaleKeyTexts>()

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
    if (locale.country != "IN" && locale.language == "ta")
        return genericCurrencyKey("௹")
    if (locale.country == "IN" || locale.language.matches("hi|kn|ml|mr|ta|te|gu".toRegex()))
        return rupee
    if (locale.country == "GB")
        return pound
    return dollar
}

private fun genericCurrencyKey(currency: String) = currency to genericCurrencyMoreKeys
private val genericCurrencyMoreKeys = arrayOf("£", "€", "$", "¢", "¥", "₱")

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
private val euro = "€" to arrayOf("£", "¥", "$", "¢", "₱")
private val dram = "֏" to arrayOf("€", "₽", "$", "£", "¥")
private val rupee = "₹" to arrayOf("£", "€", "$", "¢", "¥", "₱")
private val pound = "£" to arrayOf("€", "¥", "$", "¢", "₱")
private val ruble = "₽" to arrayOf("€", "$", "£", "¥")
private val lira = "₺" to arrayOf("€", "$", "£", "¥")
private val dollar = "$" to arrayOf("£", "¢", "€", "¥", "₱")
private val euroCountries = "AD|AT|BE|BG|HR|CY|CZ|DA|EE|FI|FR|DE|GR|HU|IE|IT|XK|LV|LT|LU|MT|MO|ME|NL|PL|PT|RO|SM|SK|SI|ES|VA".toRegex()
private val euroLocales = "bg|ca|cs|da|de|el|en|es|et|eu|fi|fr|ga|gl|hr|hu|it|lb|lt|lv|mt|nl|pl|pt|ro|sk|sl|sq|sr|sv".toRegex()

const val MORE_KEYS_ALL = 2
const val MORE_KEYS_MORE = 1
const val MORE_KEYS_NORMAL = 0

const val LANGUAGE_TEXTS_FOLDER = "language_key_texts"
