package org.dslul.openboard.inputmethod.latin

import android.content.Context
import android.util.LruCache
import org.dslul.openboard.inputmethod.keyboard.Keyboard
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_WHITELIST
import org.dslul.openboard.inputmethod.latin.common.ComposedData
import org.dslul.openboard.inputmethod.latin.common.StringUtils
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults
import org.junit.Test
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit

class SuggestTest {
    private val thresholdModest = 0.185f
    private val thresholdAggressive = 0.067f
    private val thresholdVeryAggressive = Float.NEGATIVE_INFINITY

    @Test fun `"on" to "in" if "in" was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "on",
            listOf(suggestion("on", 1800000, locale), suggestion("in", 600000, locale)),
            suggestion("in", 240, locale),
            null, // never typed "on" in this context
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first suggestion score is too low
    }

    @Test fun `"ill" to "I'll" if "ill" not used before in this context, and I'll has shortcut`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            null,
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
        // correction because both empty scores are 0, which should be fine (next check is comparing empty scores)
    }

    @Test fun `not "ill" to "I'll" if only "ill" was used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            null,
            suggestion("ill", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because first empty score not high enough
    }

    @Test fun `not "ill" to "I'll" if both were used before in this context`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // essentially same as `not "ill" to "I'll" if only "ill" was used before in this context`
    }

    @Test fun `no English "I" for Polish "i" when typing in Polish`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale("pl"),
            thresholdVeryAggressive
        )
        assert(!result.last()) // should not be corrected
        // not even checking at modest and aggressive thresholds, this is a locale thing
        // if very aggressive, still no correction because locale matches with typed word only
    }

    @Test fun `English "I" instead of Polish "i" when typing in English`() {
        val result = shouldBeAutoCorrected(
            "i",
            listOf(suggestion("I", Int.MAX_VALUE, Locale.ENGLISH), suggestion("i", 1500000, Locale("pl"))),
            null,
            null,
            Locale.ENGLISH,
            thresholdModest
        )
        assert(result.last()) // should be corrected
        // only corrected because it's whitelisted (int max value)
        // if it wasn't whitelisted, it would never be allowed due to utoCorrectionUtils.suggestionExceedsThreshold (unless set to very aggressive)
        //  -> maybe normalizedScore needs adjustment if the only difference is upper/lowercase
        //     todo: consider special score for case-only difference?
    }

    @Test fun `no English "in" instead of French "un" when typing in French`() {
        val result = shouldBeAutoCorrected(
            "un",
            listOf(suggestion("in", Int.MAX_VALUE, Locale.ENGLISH), suggestion("un", 1500000, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because of locale matching
    }

}

private fun suggestion(word: String, score: Int, locale: Locale) =
    SuggestedWordInfo(
        /* word */ word,
        /* prevWordsContext */ "", // irrelevant

        // typically 2B for shortcut, 1.5M for exact match, 600k for close match
        // when previous word context is empty, scores are usually 200+ if word is known and somewhat often used, 0 if unknown
        /* score */ score,

        /* kindAndFlags */ if (score == Int.MAX_VALUE) KIND_WHITELIST else KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION,
        /* sourceDict */ TestDict(locale),
        /* indexOfTouchPointOfSecondWord */ 0, // irrelevant
        /* autoCommitFirstWordConfidence */ 0 // irrelevant?
    )

fun shouldBeAutoCorrected(word: String, // typed word
         suggestions: List<SuggestedWordInfo>, // suggestions ordered by score, including suggestion for typed word if in dictionary
         firstSuggestionForEmpty: SuggestedWordInfo?, // first suggestion if typed word would be empty (null if none)
         typedWordSuggestionForEmpty: SuggestedWordInfo?, // suggestion for actually typed word if typed word would be empty (null if none)
         currentTypingLocale: Locale, // used for checking whether suggestion locale is the same, relevant e.g. for English i -> I shortcut, but we want Polish i
         autoCorrectThreshold: Float // -inf, 0.067, 0.185 (for very aggressive, aggressive, modest)
): List<Boolean> {
    val suggestionsContainer = ArrayList<SuggestedWordInfo>().apply { addAll(suggestions) }
    val suggestionResults = SuggestionResults(suggestions.size, false, false)
    suggestions.forEach { suggestionResults.add(it) }

    // store the original SuggestedWordInfo for typed word, as it will be removed
    // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
    var typedWordFirstOccurrenceWordInfo: SuggestedWordInfo? = null
    var foundInDictionary = false
    var sourceDictionaryOfRemovedWord: Dictionary? = null
    for (info in suggestionsContainer) {
        // Search for the best dictionary, defined as the first one with the highest match
        // quality we can find.
        if (!foundInDictionary && word == info.mWord) {
            // Use this source if the old match had lower quality than this match
            sourceDictionaryOfRemovedWord = info.mSourceDict
            foundInDictionary = true
            typedWordFirstOccurrenceWordInfo = info
            break
        }
    }

    val firstOccurrenceOfTypedWordInSuggestions =
        SuggestedWordInfo.removeDupsAndTypedWord(word, suggestionsContainer)

    return Suggest.shouldBeAutoCorrected(
        StringUtils.getTrailingSingleQuotesCount(word),
        word,
        suggestionsContainer, // todo: get from suggestions? mostly it's just removing the typed word, right?
        sourceDictionaryOfRemovedWord,
        listOf(firstSuggestionForEmpty, typedWordSuggestionForEmpty),
        {}, // only used to fill above if needed
        true, // doesn't make sense otherwise
        0, // not really relevant here
        WordComposer.getComposerForTest(false),
        suggestionResults,
        facilitator(currentTypingLocale),
        autoCorrectThreshold,
        firstOccurrenceOfTypedWordInSuggestions,
        typedWordFirstOccurrenceWordInfo
    ).toList()
}

private fun facilitator(currentTypingLocale: Locale): DictionaryFacilitator =
    object : DictionaryFacilitator {
        override fun setValidSpellingWordReadCache(cache: LruCache<String, Boolean>?) {
            TODO("Not yet implemented")
        }
        override fun setValidSpellingWordWriteCache(cache: LruCache<String, Boolean>?) {
            TODO("Not yet implemented")
        }
        override fun isForLocale(locale: Locale?): Boolean {
            TODO("Not yet implemented")
        }
        override fun isForAccount(account: String?): Boolean {
            TODO("Not yet implemented")
        }
        override fun onStartInput() {
            TODO("Not yet implemented")
        }
        override fun onFinishInput(context: Context?) {
            TODO("Not yet implemented")
        }
        override fun isActive(): Boolean {
            TODO("Not yet implemented")
        }
        override fun getLocale(): Locale {
            TODO("Not yet implemented")
        }
        override fun getCurrentLocale(): Locale = currentTypingLocale
        override fun usesContacts(): Boolean {
            TODO("Not yet implemented")
        }
        override fun getAccount(): String {
            TODO("Not yet implemented")
        }
        override fun resetDictionaries(
            context: Context?,
            newLocale: Locale?,
            useContactsDict: Boolean,
            usePersonalizedDicts: Boolean,
            forceReloadMainDictionary: Boolean,
            account: String?,
            dictNamePrefix: String?,
            listener: DictionaryFacilitator.DictionaryInitializationListener?
        ) {
            TODO("Not yet implemented")
        }
        override fun removeWord(word: String?) {
            TODO("Not yet implemented")
        }
        override fun resetDictionariesForTesting(
            context: Context?,
            locale: Locale?,
            dictionaryTypes: java.util.ArrayList<String>?,
            dictionaryFiles: HashMap<String, File>?,
            additionalDictAttributes: MutableMap<String, MutableMap<String, String>>?,
            account: String?
        ) {
            TODO("Not yet implemented")
        }
        override fun closeDictionaries() {
            TODO("Not yet implemented")
        }
        override fun getSubDictForTesting(dictName: String?): ExpandableBinaryDictionary {
            TODO("Not yet implemented")
        }
        override fun hasAtLeastOneInitializedMainDictionary(): Boolean = true
        override fun hasAtLeastOneUninitializedMainDictionary(): Boolean {
            TODO("Not yet implemented")
        }
        override fun waitForLoadingMainDictionaries(timeout: Long, unit: TimeUnit?) {
            TODO("Not yet implemented")
        }
        override fun waitForLoadingDictionariesForTesting(timeout: Long, unit: TimeUnit?) {
            TODO("Not yet implemented")
        }
        override fun addToUserHistory(
            suggestion: String?,
            wasAutoCapitalized: Boolean,
            ngramContext: NgramContext,
            timeStampInSeconds: Long,
            blockPotentiallyOffensive: Boolean
        ) {
            TODO("Not yet implemented")
        }
        override fun unlearnFromUserHistory(
            word: String?,
            ngramContext: NgramContext,
            timeStampInSeconds: Long,
            eventType: Int
        ) {
            TODO("Not yet implemented")
        }
        override fun getSuggestionResults(
            composedData: ComposedData?,
            ngramContext: NgramContext?,
            keyboard: Keyboard,
            settingsValuesForSuggestion: SettingsValuesForSuggestion?,
            sessionId: Int,
            inputStyle: Int
        ): SuggestionResults {
            TODO("Not yet implemented")
        }
        override fun isValidSpellingWord(word: String?): Boolean {
            TODO("Not yet implemented")
        }
        override fun isValidSuggestionWord(word: String?): Boolean {
            TODO("Not yet implemented")
        }
        override fun clearUserHistoryDictionary(context: Context?): Boolean {
            TODO("Not yet implemented")
        }
        override fun dump(context: Context?): String {
            TODO("Not yet implemented")
        }
        override fun dumpDictionaryForDebug(dictName: String?) {
            TODO("Not yet implemented")
        }
        override fun getDictionaryStats(context: Context?): MutableList<DictionaryStats> {
            TODO("Not yet implemented")
        }

    }

private class TestDict(locale: Locale) : Dictionary("testDict", locale) {
    override fun getSuggestions(
        composedData: ComposedData?,
        ngramContext: NgramContext?,
        proximityInfoHandle: Long,
        settingsValuesForSuggestion: SettingsValuesForSuggestion?,
        sessionId: Int,
        weightForLocale: Float,
        inOutWeightOfLangModelVsSpatialModel: FloatArray?
    ): ArrayList<SuggestedWordInfo> {
        TODO("Not yet implemented")
    }

    override fun isInDictionary(word: String?): Boolean {
        TODO("Not yet implemented")
    }

}
