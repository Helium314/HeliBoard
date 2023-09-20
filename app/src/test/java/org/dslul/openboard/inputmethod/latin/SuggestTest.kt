package org.dslul.openboard.inputmethod.latin

import androidx.core.content.edit
import androidx.test.runner.AndroidJUnit4
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_FLAG_APPROPRIATE_FOR_AUTO_CORRECTION
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo.KIND_WHITELIST
import org.dslul.openboard.inputmethod.latin.common.ComposedData
import org.dslul.openboard.inputmethod.latin.common.StringUtils
import org.dslul.openboard.inputmethod.latin.settings.Settings
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion
import org.dslul.openboard.inputmethod.latin.utils.DeviceProtectedUtils
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import org.robolectric.shadows.ShadowLog
import java.util.*

@RunWith(AndroidJUnit4::class)
@Config(shadows = [
    ShadowLocaleManagerCompat::class,
    ShadowInputMethodManager2::class,
    ShadowBinaryDictionaryUtils::class,
    ShadowFacilitator::class,
])
class SuggestTest {
    private lateinit var latinIME: LatinIME
    private val suggest get() = latinIME.mInputLogic.mSuggest

    // values taken from the string array auto_correction_threshold_mode_indexes
    private val thresholdModest = "0"
    private val thresholdAggressive = "1"
    private val thresholdVeryAggressive = "2"

    @Before fun setUp() {
        latinIME = Robolectric.setupService(LatinIME::class.java)
        // start logging only after latinIME is created, avoids showing the stack traces if library is not found
        ShadowLog.setupLogging()
        ShadowLog.stream = System.out
        DeviceProtectedUtils.getSharedPreferences(latinIME)
            .edit { putBoolean(Settings.PREF_AUTO_CORRECTION, true) } // need to enable, off by default
    }

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

    @Test fun `"ill" to "I'll" if both have same ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 200, locale),
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no "ill" to "I'll" if "ill" has somewhat better ngram score`() {
        val locale = Locale.ENGLISH
        val result = shouldBeAutoCorrected(
            "ill",
            listOf(suggestion("I'll", Int.MAX_VALUE, locale), suggestion("ill", 1500000, locale)),
            suggestion("I'll", 200, locale),
            suggestion("ill", 211, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
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

    @Test fun `no "né" instead of "ne"`() {
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, Locale.FRENCH), suggestion("né", 1900000-1, Locale.FRENCH)),
            null,
            null,
            Locale.FRENCH,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not corrected because score is lower
    }

    @Test fun `"né" instead of "ne" if "né" in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            null,
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `"né" instead of "ne" if "né" has clearly better score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 215, locale),
            suggestion("ne", 200, locale),
            locale,
            thresholdModest
        )
        assert(result.last()) // should be corrected
    }

    @Test fun `no "né" instead of "ne" if both with same score in ngram context`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "ne",
            listOf(suggestion("ne", 1900000, locale), suggestion("né", 1900000-1, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
    }

    @Test fun `no "ne" instead of "né"`() {
        val locale = Locale.FRENCH
        val result = shouldBeAutoCorrected(
            "né",
            listOf(suggestion("ne", 600000, locale), suggestion("né", 1600000, locale)),
            suggestion("né", 200, locale),
            suggestion("ne", 200, locale),
            locale,
            thresholdModest
        )
        assert(!result.last()) // should not be corrected
        // not even allowed to check because of low score for ne
    }

    private fun setAutCorrectThreshold(threshold: String) {
        val prefs = DeviceProtectedUtils.getSharedPreferences(latinIME)
        prefs.edit { putString(Settings.PREF_AUTO_CORRECTION_CONFIDENCE, threshold) }
    }

    private fun shouldBeAutoCorrected(word: String, // typed word
                              suggestions: List<SuggestedWordInfo>, // suggestions ordered by score, including suggestion for typed word if in dictionary
                              firstSuggestionForEmpty: SuggestedWordInfo?, // first suggestion if typed word would be empty (null if none)
                              typedWordSuggestionForEmpty: SuggestedWordInfo?, // suggestion for actually typed word if typed word would be empty (null if none)
                              typingLocale: Locale, // used for checking whether suggestion locale is the same, relevant e.g. for English i -> I shortcut, but we want Polish i
                              autoCorrectThreshold: String // 0, 1, or 2, but better use the vals on top with the corresponding name
    ): List<Boolean> {
        setAutCorrectThreshold(autoCorrectThreshold)
        currentTypingLocale = typingLocale
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

        return suggest.shouldBeAutoCorrected(
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
            firstOccurrenceOfTypedWordInSuggestions,
            typedWordFirstOccurrenceWordInfo
        ).toList()
    }
}

private var currentTypingLocale = Locale.ENGLISH

fun suggestion(word: String, score: Int, locale: Locale) =
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

@Implements(DictionaryFacilitatorImpl::class)
class ShadowFacilitator {
    @Implementation
    fun getCurrentLocale() = currentTypingLocale
    @Implementation
    fun hasAtLeastOneInitializedMainDictionary() = true // otherwise no autocorrect
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
