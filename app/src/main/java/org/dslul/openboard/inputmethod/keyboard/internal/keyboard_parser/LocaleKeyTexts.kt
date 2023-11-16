// SPDX-License-Identifier: GPL-3.0-only
package org.dslul.openboard.inputmethod.keyboard.internal.keyboard_parser

import android.content.Context
import android.util.Log
import org.dslul.openboard.inputmethod.keyboard.internal.KeyboardParams
import org.dslul.openboard.inputmethod.latin.settings.Settings
import java.io.InputStream
import java.util.Locale

class LocaleKeyTexts(dataStream: InputStream?) {
    private val moreKeys = hashMapOf<String, Array<String>>()
    private val extraKeys = Array<MutableList<Pair<String, Array<String>?>>?>(5) { null }
    var labelSymbols = "\\?123"
    var labelAlphabet = "ABC"
    var labelShiftSymbols = "=\\<"
    init { readStream(dataStream, false) }

    private fun readStream(stream: InputStream?, onlyMoreKeys: Boolean) {
        if (stream == null) return
        stream.reader().use { reader ->
            var mode = MODE_NONE
            reader.forEachLine { l ->
                val line = l.trim()
                when (line) {
                    "[morekeys]" -> { mode = MODE_MORE_KEYS; return@forEachLine }
                    "[extra_keys]" -> { mode = MODE_EXTRA_KEYS; return@forEachLine }
                    "[labels]" -> { mode = MODE_LABELS; return@forEachLine }
                }
                if (mode == MODE_MORE_KEYS) {
                    val split = line.split(" ")
                    if (split.size == 1) return@forEachLine
                    val existingMoreKeys = moreKeys[split.first()]
                    if (existingMoreKeys == null)
                        moreKeys[split.first()] = Array(split.size - 1) { split[it + 1] }
                    else
                        moreKeys[split.first()] = mergeMoreKeys(existingMoreKeys, split.drop(1))
                } else if (mode == MODE_EXTRA_KEYS && !onlyMoreKeys) {
                    val row = line.substringBefore(": ").toInt()
                    val split = line.substringAfter(": ").split(" ")
                    val morekeys = if (split.size == 1) null else Array(split.size - 1) { split[it + 1] }
                    if (extraKeys[row] == null)
                        extraKeys[row] = mutableListOf()
                    extraKeys[row]?.add(split.first() to morekeys)
                } else if (mode == MODE_LABELS && !onlyMoreKeys) {
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

    fun addLanguage(dataStream: InputStream?) {
        readStream(dataStream, true)
    }

}

// todo: this is a little too simple, actually there may be more than one % which should be considered
//  further the first of the added moreKeys should be close to the beginning, as they are more likely to be used
// todo: careful about punctuation moreKeys, because of autoColumnOrder and stuff
//  is it possible to just take the larger list here for a start?
//  -> no, will be bad with es + ca, and that's probably not an unusual combination
private fun mergeMoreKeys(old: Array<String>, added: List<String>): Array<String> {
    val moreKeys = old.toMutableSet()
    moreKeys.addAll(added)
    return moreKeys.toTypedArray()
}

fun putLanguageMoreKeysAndLabels(context: Context, params: KeyboardParams) {
    val locales = Settings.getInstance().current.mSecondaryLocales + params.mId.locale
    params.mLocaleKeyTexts = moreKeysAndLabels.getOrPut(locales.joinToString { it.toString() }) {
        val lkt = LocaleKeyTexts(getStreamForLocale(params.mId.locale, context))
        locales.forEach { locale ->
            if (locale == params.mId.locale) return@forEach
            lkt.addLanguage(getStreamForLocale(locale, context))
        }
        lkt
    }
}

private fun getStreamForLocale(locale: Locale, context: Context) =
    try {
        context.assets.open("language_key_texts/${locale.toString().lowercase()}.txt")
    } catch (_: Exception) {
        try {
            context.assets.open("language_key_texts/${locale.language.lowercase()}.txt")
        } catch (_: Exception) {
            null
        }
    }

// cache the texts, so they don't need to be read over and over
private val moreKeysAndLabels = hashMapOf<String, LocaleKeyTexts>()

private const val MODE_NONE = 0
private const val MODE_MORE_KEYS = 1
private const val MODE_EXTRA_KEYS = 2
private const val MODE_LABELS = 3

// probably could be improved and extended
fun getCurrencyKey(locale: Locale): Pair<String, Array<String>> {
    if (locale.country.matches(euroCountries))
        return STYLE_EURO
    if (locale.toString().matches(euroLocales))
        return STYLE_EURO
    if (locale.language.matches("ca|eu|lb|mt".toRegex()))
        return STYLE_EURO
    if (locale.language.matches("fa|iw|ko|lo|mn|ne|th|uk|vi".toRegex()))
        return genericCurrencyKey(getCurrency(locale))
    if (locale.language == "hy")
        return STYLE_DRAM
    if (locale.language == "tr")
        return STYLE_LIRA
    if (locale.language == "ru")
        return STYLE_RUBLE
    if (locale.country == "LK" || locale.country == "BD")
        return genericCurrencyKey(getCurrency(locale))
    if (locale.country == "IN" || locale.language.matches("hi|kn|ml|mr|ta|te".toRegex()))
        return STYLE_RUPEE
    if (locale.country == "GB")
        return STYLE_POUND
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
private val STYLE_EURO = "€" to arrayOf("¢", "£", "$", "¥", "₱")
private val STYLE_DRAM = "֏" to arrayOf("€", "$", "₽", "¥", "£")
private val STYLE_RUPEE = "₹" to arrayOf("¢", "£", "€", "¥", "₱")
private val STYLE_POUND = "£" to arrayOf("¢", "$", "€", "¥", "₱")
private val STYLE_RUBLE = "₽" to arrayOf("€", "$", "£", "¥")
private val STYLE_LIRA = "₺" to arrayOf("€", "$", "£", "¥")
private val euroCountries = "AD|AT|BE|BG|HR|CY|CZ|DA|EE|FI|FR|DE|GR|HU|IE|IT|XK|LV|LT|LU|MT|MO|ME|NL|PL|PT|RO|SM|SK|SI|ES|VA".toRegex()
private val euroLocales = "bg|ca|cs|da|de|el|en|es|et|eu|fi|fr|ga|gl|hr|hu|it|lb|lt|lv|mt|nl|pl|pt|ro|sk|sl|sq|sr|sv".toRegex()
