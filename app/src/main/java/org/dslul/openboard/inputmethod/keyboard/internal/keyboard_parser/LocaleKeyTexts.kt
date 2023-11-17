// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import org.dslul.openboard.inputmethod.keyboard.Key
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.latin.settings.Settings
import java.io.InputStream
import java.util.Locale
import kotlin.math.round

class LocaleKeyTexts(dataStream: InputStream?) {
    private val moreKeys = hashMapOf<String, Array<String>>()
    private val extraKeys = Array<MutableList<Pair<String, Array<String>?>>?>(5) { null }
    var labelSymbols = "\\?123"
    var labelAlphabet = "ABC"
    var labelShiftSymbols = "=\\<"
    init {
        readStream(dataStream, false)
        // set default quote moreKeys if necessary
        // should this also be done with punctuation moreKeys??
        if ("\'" !in moreKeys)
            moreKeys["\'"] = arrayOf("‚", "‘", "’", "‹", "›")
        if ("\"" !in moreKeys)
            moreKeys["\""] = arrayOf("„", "“", "”", "«", "»")
    }

    private fun readStream(stream: InputStream?, onlyMoreKeys: Boolean) {
        if (stream == null) return
        stream.reader().use { reader ->
            var mode = READER_MODE_NONE
            reader.forEachLine { l ->
                val line = l.trim()
                when (line) {
                    "[morekeys]" -> { mode = READER_MODE_MORE_KEYS; return@forEachLine }
                    "[extra_keys]" -> { mode = READER_MODE_EXTRA_KEYS; return@forEachLine }
                    "[labels]" -> { mode = READER_MODE_LABELS; return@forEachLine }
                }
                if (mode == READER_MODE_MORE_KEYS) {
                    val split = line.split(" ")
                    if (split.size == 1) return@forEachLine
                    val existingMoreKeys = moreKeys[split.first()]
                    if (existingMoreKeys == null)
                        moreKeys[split.first()] = Array(split.size - 1) { split[it + 1] }
                    else
                        moreKeys[split.first()] = mergeMoreKeys(existingMoreKeys, split.drop(1))
                } else if (mode == READER_MODE_EXTRA_KEYS && !onlyMoreKeys) {
                    val row = line.substringBefore(": ").toInt()
                    val split = line.substringAfter(": ").split(" ")
                    val morekeys = if (split.size == 1) null else Array(split.size - 1) { split[it + 1] }
                    if (extraKeys[row] == null)
                        extraKeys[row] = mutableListOf()
                    extraKeys[row]?.add(split.first() to morekeys)
                } else if (mode == READER_MODE_LABELS && !onlyMoreKeys) {
                    val split = line.split(": ")
                    when (split.first()) {
                        "symbols" -> labelSymbols = split.last()
                        "alphabet" -> labelAlphabet = split.last()
                        "shift_symbols" -> labelShiftSymbols = split.last()
                    }
                }
            }
        }

    }

    fun getMoreKeys(label: String): Array<String>? = moreKeys[label]

    fun getExtraKeys(row: Int): List<Pair<String, Array<String>?>>? =
        if (row > extraKeys.size) null
            else extraKeys[row]

    fun addFile(dataStream: InputStream?) {
        readStream(dataStream, true)
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
        return moreKeys.toTypedArray()
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
        moreKeys.addAll(original)
        moreKeys.addAll(added)
    }
    if (moreKeys.any { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }) {
        val originalColumnCount = original.firstOrNull { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }
            ?.substringAfter(Key.MORE_KEYS_AUTO_COLUMN_ORDER)?.toIntOrNull()
        val l = moreKeys.filterNot { it.startsWith(Key.MORE_KEYS_AUTO_COLUMN_ORDER) }
        if (originalColumnCount != null && moreKeys.size <= 20 // not for too wide layout
            && originalColumnCount == round((original.size - 1 + 0.1f) / 2f).toInt()) { // +0.1 f against rounding issues
            // we had 2 rows, and want it again
            return (l + "${Key.MORE_KEYS_AUTO_COLUMN_ORDER}${round(l.size / 2f).toInt()}").toTypedArray()
        }
        // just drop autoColumnOrder otherwise (maybe not? depends on arising issues)
        return l.toTypedArray()
    }
    return moreKeys.toTypedArray()
}

fun putLanguageMoreKeysAndLabels(context: Context, params: KeyboardParams) {
    val locales = Settings.getInstance().current.mSecondaryLocales + params.mId.locale
    params.mLocaleKeyTexts = moreKeysAndLabels.getOrPut(locales.joinToString { it.toString() }) {
        val lkt = LocaleKeyTexts(getStreamForLocale(params.mId.locale, context))
        locales.forEach { locale ->
            if (locale == params.mId.locale) return@forEach
            lkt.addFile(getStreamForLocale(locale, context))
        }
        lkt
    }
}

private fun getStreamForLocale(locale: Locale, context: Context) =
    try {
        if (locale.toString() == "zz") context.assets.open("language_key_texts/more_more_keys.txt")
        else context.assets.open("language_key_texts/${locale.toString().lowercase()}.txt")
    } catch (_: Exception) {
        try {
            context.assets.open("language_key_texts/${locale.language.lowercase()}.txt")
        } catch (_: Exception) {
            null
        }
    }

// cache the texts, so they don't need to be read over and over
private val moreKeysAndLabels = hashMapOf<String, LocaleKeyTexts>()

private const val READER_MODE_NONE = 0
private const val READER_MODE_MORE_KEYS = 1
private const val READER_MODE_EXTRA_KEYS = 2
private const val READER_MODE_LABELS = 3

// probably could be improved and extended
fun getCurrencyKey(locale: Locale): Pair<String, Array<String>> {
    if (locale.country.matches(euroCountries))
        return euro
    if (locale.toString().matches(euroLocales))
        return euro
    if (locale.language.matches("ca|eu|lb|mt".toRegex()))
        return euro
    if (locale.language.matches("fa|iw|ko|lo|mn|ne|th|uk|vi".toRegex()))
        return genericCurrencyKey(getCurrency(locale))
    if (locale.language == "hy")
        return dram
    if (locale.language == "tr")
        return lira
    if (locale.language == "ru")
        return ruble
    if (locale.country == "LK" || locale.country == "BD")
        return genericCurrencyKey(getCurrency(locale))
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
