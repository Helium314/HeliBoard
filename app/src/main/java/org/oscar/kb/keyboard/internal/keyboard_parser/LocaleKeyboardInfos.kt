// SPDX-License-Identifier: GPL-3.0-only
package org.oscar.kb.keyboard.internal.keyboard_parser

import android.content.Context
import org.oscar.kb.keyboard.Key
import org.oscar.kb.keyboard.KeyboardId
import org.oscar.kb.keyboard.internal.KeyboardParams
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.KeyData
import org.oscar.kb.keyboard.internal.keyboard_parser.floris.toTextKey
import org.oscar.kb.latin.common.splitOnFirstSpacesOnly
import org.oscar.kb.latin.common.splitOnWhitespace
import org.oscar.kb.latin.settings.Settings
import org.oscar.kb.latin.utils.SubtypeLocaleUtils
import java.io.InputStream
import java.util.Locale
import kotlin.math.round

class LocaleKeyboardInfos(dataStream: InputStream?, locale: Locale) {
    private val popupKeys = hashMapOf<String, MutableCollection<String>>()
    private val priorityPopupKeys = hashMapOf<String, MutableCollection<String>>()
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
    private val numbersPopupKeys = arrayOf(
        mutableListOf("¹", "½", "⅓","¼", "⅛"),
        mutableListOf("²", "⅔"),
        mutableListOf("³", "¾", "⅜"),
        mutableListOf("⁴"),
        mutableListOf("⁵", "⅝"),
        mutableListOf("⁶"),
        mutableListOf("⁷", "⅞"),
        mutableListOf("⁸"),
        mutableListOf("⁹"),
        mutableListOf("⁰", "ⁿ", "∅"),
    )
    val hasZwnjKey = when (locale.language) { // todo: move to the info file
        "fa", "ne", "kn", "te" -> true
        else -> false
    }
    val labelFlags = when (locale.language) { // todo: move to the info file
        "hy", "ar", "be", "fa", "hi", "lo", "mr", "ne", "th", "ur" -> Key.LABEL_FLAGS_FONT_NORMAL
        "km", "ml", "si", "ta", "te" -> Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_AUTO_X_SCALE
        "kn" -> Key.LABEL_FLAGS_FONT_NORMAL or Key.LABEL_FLAGS_AUTO_X_SCALE or Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
        "mns" -> Key.LABEL_FLAGS_FOLLOW_KEY_LETTER_RATIO
        else -> 0
    }

    init {
        readStream(dataStream, false, true)
        // set default quote popupKeys if necessary
        // should this also be done with punctuation popupKeys?
        // todo: those defaults should not be in here
        if ("\'" !in popupKeys)
            popupKeys["\'"] = mutableListOf("!fixedColumnOrder!5", "‚", "‘", "’", "‹", "›")
        if ("\"" !in popupKeys)
            popupKeys["\""] = mutableListOf("!fixedColumnOrder!5", "„", "“", "”", "«", "»")
        if ("!" !in popupKeys)
            popupKeys["!"] = mutableListOf("¡")
        if (labelQuestion !in popupKeys)
            popupKeys[labelQuestion] = if (labelQuestion == "?") mutableListOf("¿") else mutableListOf("?", "¿")
        if ("punctuation" !in popupKeys)
            popupKeys["punctuation"] = mutableListOf("${Key.POPUP_KEYS_AUTO_COLUMN_ORDER}8", "\\,", "?", "!", "#", ")", "(", "/", ";", "'", "@", ":", "-", "\"", "+", "\\%", "&")
    }

    private fun readStream(stream: InputStream?, onlyPopupKeys: Boolean, priority: Boolean) {
        if (stream == null) return
        stream.reader().use { reader ->
            var mode = READER_MODE_NONE
            val colonSpaceRegex = ":\\s+".toRegex()
            reader.forEachLine { l ->
                val line = l.trim()
                if (line.isEmpty()) return@forEachLine
                when (line) {
                    "[popup_keys]" -> { mode = READER_MODE_POPUP_KEYS; return@forEachLine }
                    "[extra_keys]" -> { mode = READER_MODE_EXTRA_KEYS; return@forEachLine }
                    "[labels]" -> { mode = READER_MODE_LABELS; return@forEachLine }
                    "[number_row]" -> { mode = READER_MODE_NUMBER_ROW; return@forEachLine }
                }
                when (mode) {
                    READER_MODE_POPUP_KEYS -> addPopupKeys(line, priority)
                    READER_MODE_EXTRA_KEYS -> if (!onlyPopupKeys) addExtraKey(line.split(colonSpaceRegex, 2))
                    READER_MODE_LABELS -> if (!onlyPopupKeys) addLabel(line.split(colonSpaceRegex, 2))
                    READER_MODE_NUMBER_ROW -> setNumberRow(line.splitOnWhitespace(), onlyPopupKeys)
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

    fun getPopupKeys(label: String): Collection<String>? = popupKeys[label]
    fun getPriorityPopupKeys(label: String): Collection<String>? = priorityPopupKeys[label]

    // used by simple parser only, but could be possible for json as well (if necessary)
    fun getExtraKeys(row: Int): List<KeyData>? =
        if (row > extraKeys.size) null
            else extraKeys[row]

    fun addFile(dataStream: InputStream?, priority: Boolean) {
        readStream(dataStream, true, priority)
    }

    private fun addPopupKeys(line: String, priority: Boolean) {
        val split = if (line.contains("|"))
                // if a popup key contains label/code separately, there are cases where space can be in there too
                // normally this should work for all popup keys, but if we split them on whitespace there is less chance for unnecessary issues
                line.splitOnFirstSpacesOnly()
            else line.splitOnWhitespace()
        if (split.size == 1) return
        val key = split.first()
        val popupsMap = if (priority) priorityPopupKeys else popupKeys
        if (popupsMap[key] is MutableList)
            popupsMap[key] = popupsMap[key]!!.toMutableSet().also { it.addAll(split.drop(1)) }
        else if (popupsMap.containsKey(key)) popupsMap[key]!!.addAll(split.drop(1))
        else popupsMap[key] = split.drop(1).toMutableList() // first use a list because usually it's enough
        adjustAutoColumnOrder(popupsMap[key]!!)
        when (key) {
            "'", "\"", "«", "»" -> addFixedColumnOrder(popupsMap[key]!!)
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

    // set number row only, does not affect popupKeys
    // setting more than 10 number keys will cause crashes, but could actually be implemented at some point
    private fun setNumberRow(split: List<String>, onlyAddToPopupKeys: Boolean) {
        if (onlyAddToPopupKeys) {
            // as of now this should never be used, but better have it
            numberKeys.forEachIndexed { i, n ->
                if (numberKeys[i] != n && n !in numbersPopupKeys[i])
                    numbersPopupKeys[i].add(0, n)
            }
            return
        }
        if (Settings.getInstance().current.mLocalizedNumberRow) {
            numberKeys.forEachIndexed { i, n -> numbersPopupKeys[i].add(0, n) }
            numberKeys = split
        } else {
            split.forEachIndexed { i, n -> numbersPopupKeys[i].add(0, n) }
        }
    }

    // get number row including popupKeys
    fun getNumberRow(): List<KeyData> =
        numberKeys.mapIndexed { i, label ->
            label.toTextKey(numbersPopupKeys[i])
        }

    fun getNumberLabel(numberIndex: Int?): String? = numberIndex?.let { numberKeys.getOrNull(it) }
}

private fun mergePopupKeys(original: List<String>, added: List<String>): List<String> {
    if (original.any { it.startsWith(Key.POPUP_KEYS_AUTO_COLUMN_ORDER) } || added.any { it.startsWith(
            Key.POPUP_KEYS_AUTO_COLUMN_ORDER) }) {
        val popupKeys = (original + added).toSet()
        val originalColumnCount = original.firstOrNull { it.startsWith(Key.POPUP_KEYS_AUTO_COLUMN_ORDER) }
            ?.substringAfter(Key.POPUP_KEYS_AUTO_COLUMN_ORDER)?.toIntOrNull()
        val l = popupKeys.filterNot { it.startsWith(Key.POPUP_KEYS_AUTO_COLUMN_ORDER) }
        if (originalColumnCount != null && popupKeys.size <= 20 // not for too wide layout
            && originalColumnCount == round((original.size - 1 + 0.1f) / 2f).toInt()) { // +0.1 f against rounding issues
            // we had 2 rows, and want it again
            return (l + "${Key.POPUP_KEYS_AUTO_COLUMN_ORDER}${round(l.size / 2f).toInt()}")
        }
        // just drop autoColumnOrder otherwise
        return l
    }
    return original + added
}

private fun addFixedColumnOrder(popupKeys: MutableCollection<String>) {
    // use intermediate list, because we can't add first in a LinkedHashSet (i.e. MutableSet)
    popupKeys.removeAll { it.startsWith(Key.POPUP_KEYS_FIXED_COLUMN_ORDER) }
    val temp = popupKeys.toList()
    popupKeys.clear()
    popupKeys.add("${Key.POPUP_KEYS_FIXED_COLUMN_ORDER}${temp.size}")
    popupKeys.addAll(temp)
}

private fun adjustAutoColumnOrder(popupKeys: MutableCollection<String>) {
    // same style as above
    // currently, POPUP_KEYS_AUTO_COLUMN_ORDER is only used for 2 lines of punctuation popups, so assume 2 lines
    if (!popupKeys.removeAll { it.startsWith(Key.POPUP_KEYS_AUTO_COLUMN_ORDER) })
        return
    val temp = popupKeys.toList()
    popupKeys.clear()
    popupKeys.add("${Key.POPUP_KEYS_AUTO_COLUMN_ORDER}${((temp.size + 1) / 2).coerceAtMost(10)}")
    popupKeys.addAll(temp)
}

// no caching because this might get called first, and thus can mess with the cache
// those 2 ways of creating could be unified, but whatever...
fun getOrCreate(context: Context, locale: Locale): LocaleKeyboardInfos =
    localeKeyboardInfosCache[locale.toString()]
        ?: LocaleKeyboardInfos(getStreamForLocale(locale, context), locale)

fun addLocaleKeyTextsToParams(context: Context, params: KeyboardParams, popupKeysSetting: Int) {
    val locales = params.mSecondaryLocales + params.mId.locale
    params.mLocaleKeyboardInfos = localeKeyboardInfosCache.getOrPut(locales.joinToString { it.toString() }) {
        createLocaleKeyTexts(context, params, popupKeysSetting)
    }
}

private fun createLocaleKeyTexts(context: Context, params: KeyboardParams, popupKeysSetting: Int): LocaleKeyboardInfos {
    val lkt = LocaleKeyboardInfos(getStreamForLocale(params.mId.locale, context), params.mId.locale)
    params.mSecondaryLocales.forEach { locale ->
        if (locale == params.mId.locale) return@forEach
        lkt.addFile(getStreamForLocale(locale, context), true)
    }
    when (popupKeysSetting) {
        POPUP_KEYS_MAIN -> lkt.addFile(context.assets.open("$LOCALE_TEXTS_FOLDER/more_popups_main.txt"), false)
        POPUP_KEYS_MORE -> lkt.addFile(context.assets.open("$LOCALE_TEXTS_FOLDER/more_popups_more.txt"), false)
        POPUP_KEYS_ALL -> lkt.addFile(context.assets.open("$LOCALE_TEXTS_FOLDER/more_popups_all.txt"), false)
    }
    return lkt
}

private fun getStreamForLocale(locale: Locale, context: Context) =
    try {
        if (locale.toLanguageTag() == SubtypeLocaleUtils.NO_LANGUAGE) context.assets.open("$LOCALE_TEXTS_FOLDER/more_popup_keys.txt")
        else context.assets.open("$LOCALE_TEXTS_FOLDER/${locale.toLanguageTag()}.txt")
    } catch (_: Exception) {
        try {
            context.assets.open("$LOCALE_TEXTS_FOLDER/${locale.language}.txt")
        } catch (_: Exception) {
            null
        }
    }

fun clearCache() = localeKeyboardInfosCache.clear()

// cache the texts, so they don't need to be read over and over
private val localeKeyboardInfosCache = hashMapOf<String, LocaleKeyboardInfos>()

private const val READER_MODE_NONE = 0
private const val READER_MODE_POPUP_KEYS = 1
private const val READER_MODE_EXTRA_KEYS = 2
private const val READER_MODE_LABELS = 3
private const val READER_MODE_NUMBER_ROW = 4

// probably could be improved and extended, currently this is what's done in key_styles_currency.xml
private fun getCurrencyKey(locale: Locale): Pair<String, List<String>> {
    Settings.getInstance().readCustomCurrencyKey().takeIf { it.isNotBlank() }?.let {
        val split = it.trim().splitOnWhitespace()
        if (split.isNotEmpty())
            return split[0] to (split.toSet() + genericCurrencyPopupKeys).filterNot { it == split[0] }.take(6)
    }
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

private fun genericCurrencyKey(currency: String) = currency to genericCurrencyPopupKeys
private val genericCurrencyPopupKeys = listOf("£", "€", "$", "¢", "¥", "₱")

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

// needs at least 4 popupKeys for working shift-symbol keyboard
private val euro = "€" to listOf("£", "¥", "$", "¢", "₱")
private val dram = "֏" to listOf("€", "₽", "$", "£", "¥")
private val rupee = "₹" to listOf("£", "€", "$", "¢", "¥", "₱")
private val pound = "£" to listOf("€", "¥", "$", "¢", "₱")
private val ruble = "₽" to listOf("€", "$", "£", "¥", "₱")
private val lira = "₺" to listOf("€", "$", "£", "¥", "₱")
private val dollar = "$" to listOf("£", "¢", "€", "¥", "₱")
private val euroCountries = "AD|AT|BE|BG|HR|CY|CZ|DA|EE|FI|FR|DE|GR|HU|IE|IT|XK|LV|LT|LU|MT|MO|ME|NL|PL|PT|RO|SM|SK|SI|ES|VA".toRegex()
private val euroLocales = "bg|ca|cs|da|de|el|en|es|et|eu|fi|fr|ga|gl|hr|hu|it|lb|lt|lv|mt|nl|pl|pt|ro|sk|sl|sq|sr|sv".toRegex()

const val POPUP_KEYS_ALL = 2
const val POPUP_KEYS_MORE = 1
const val POPUP_KEYS_MAIN = 3
const val POPUP_KEYS_NORMAL = 0

private const val LOCALE_TEXTS_FOLDER = "locale_key_texts"
