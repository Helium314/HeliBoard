/*
 * Copyright (C) 2012 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.dslul.openboard.inputmethod.latin.spellcheck;

import android.content.ContentResolver;
import android.database.ContentObserver;
import android.os.Binder;
import android.provider.UserDictionary.Words;
import android.service.textservice.SpellCheckerService.Session;
import android.text.TextUtils;
import android.util.Log;
import android.util.LruCache;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.latin.NgramContext;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.WordComposer;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.LocaleUtils;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import org.dslul.openboard.inputmethod.latin.utils.ScriptUtils;
import org.dslul.openboard.inputmethod.latin.utils.StatsUtils;
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public abstract class AndroidWordLevelSpellCheckerSession extends Session {
    private static final String TAG = AndroidWordLevelSpellCheckerSession.class.getSimpleName();

    public final static String[] EMPTY_STRING_ARRAY = new String[0];

    // Immutable, but not available in the constructor.
    private Locale mLocale;
    // Cache this for performance
    private int mScript; // One of SCRIPT_LATIN or SCRIPT_CYRILLIC for now.
    private final AndroidSpellCheckerService mService;
    protected final SuggestionsCache mSuggestionsCache = new SuggestionsCache();
    private final ContentObserver mObserver;

    private static final String quotesRegexp =
            "(\\u0022|\\u0027|\\u0060|\\u00B4|\\u2018|\\u2018|\\u201C|\\u201D)";

    private static final Map<Integer, String> scriptToPunctuationRegexMap = new TreeMap<>();

    static {
        // TODO: add other non-English language specific punctuation later.
        scriptToPunctuationRegexMap.put(
            ScriptUtils.SCRIPT_ARMENIAN,
            "(\\u0028|\\u0029|\\u0027|\\u2026|\\u055E|\\u055C|\\u055B|\\u055D|\\u058A|\\u2015|\\u00AB|\\u00BB|\\u002C|\\u0589|\\u2024)"
        );
    }

    private static final class SuggestionsParams {
        public final String[] mSuggestions;
        public final int mFlags;
        public SuggestionsParams(String[] suggestions, int flags) {
            mSuggestions = suggestions;
            mFlags = flags;
        }
    }

    protected static final class SuggestionsCache {
        private static final int MAX_CACHE_SIZE = 50;
        private final LruCache<String, SuggestionsParams> mUnigramSuggestionsInfoCache =
                new LruCache<>(MAX_CACHE_SIZE);

        private static String generateKey(final String query) {
            return query + "";
        }

        public SuggestionsParams getSuggestionsFromCache(final String query) {
            return mUnigramSuggestionsInfoCache.get(query);
        }

        public void putSuggestionsToCache(
                final String query, final String[] suggestions, final int flags) {
            if (suggestions == null || TextUtils.isEmpty(query)) {
                return;
            }
            mUnigramSuggestionsInfoCache.put(
                    generateKey(query),
                    new SuggestionsParams(suggestions, flags));
        }

        public void clearCache() {
            mUnigramSuggestionsInfoCache.evictAll();
        }
    }

    AndroidWordLevelSpellCheckerSession(final AndroidSpellCheckerService service) {
        mService = service;
        final ContentResolver cres = service.getContentResolver();

        mObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean self) {
                mSuggestionsCache.clearCache();
            }
        };
        cres.registerContentObserver(Words.CONTENT_URI, true, mObserver);
    }

    private void updateLocale() {
        final String localeString = getLocale();

        if (mLocale == null || !mLocale.toString().equals(localeString)) {
            final String oldLocal = mLocale == null ? "null" : mLocale.toString();
            Log.d(TAG, "Updating locale from " + oldLocal + " to " + localeString);

            mLocale = (null == localeString) ? null
                    : LocaleUtils.constructLocaleFromString(localeString);
            mScript = ScriptUtils.getScriptFromSpellCheckerLocale(mLocale);
        }
    }

    @Override
    public void onCreate() {
        updateLocale();
    }

    @Override
    public String getLocale() {
        // This function was taken from https://github.com/LineageOS/android_frameworks_base/blob/1235c24a0f092d0e41fd8e86f332f8dc03896a7b/services/core/java/com/android/server/TextServicesManagerService.java#L544 and slightly adopted.

        final InputMethodManager imm;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            imm = mService.getApplicationContext()
                    .getSystemService(InputMethodManager.class);
            if (imm != null) {
                final InputMethodSubtype currentInputMethodSubtype =
                        imm.getCurrentInputMethodSubtype();
                if (currentInputMethodSubtype != null) {
                    final String localeString = currentInputMethodSubtype.getLocale();
                    if (!TextUtils.isEmpty(localeString)) {
                        // Use keyboard locale if available in the spell checker
                        return localeString;
                    }
                }
            }
        }

        // Fallback to system locale
        return super.getLocale();
    }

    @Override
    public void onClose() {
        final ContentResolver cres = mService.getContentResolver();
        cres.unregisterContentObserver(mObserver);
    }

    private static final int CHECKABILITY_CHECKABLE = 0;
    private static final int CHECKABILITY_TOO_MANY_NON_LETTERS = 1;
    private static final int CHECKABILITY_CONTAINS_PERIOD = 2;
    private static final int CHECKABILITY_EMAIL_OR_URL = 3;
    private static final int CHECKABILITY_FIRST_LETTER_UNCHECKABLE = 4;
    private static final int CHECKABILITY_TOO_SHORT = 5;
    /**
     * Finds out whether a particular string should be filtered out of spell checking.
     *
     * This will loosely match URLs, numbers, symbols. To avoid always underlining words that
     * we know we will never recognize, this accepts a script identifier that should be one
     * of the SCRIPT_* constants defined above, to rule out quickly characters from very
     * different languages.
     *
     * @param text the string to evaluate.
     * @param script the identifier for the script this spell checker recognizes
     * @return one of the FILTER_OUT_* constants above.
     */
    private static int getCheckabilityInScript(final String text, final int script) {
        if (TextUtils.isEmpty(text) || text.length() <= 1) return CHECKABILITY_TOO_SHORT;

        // TODO: check if an equivalent processing can't be done more quickly with a
        // compiled regexp.
        // Filter by first letter
        final int firstCodePoint = text.codePointAt(0);
        // Filter out words that don't start with a letter or an apostrophe
        if (!ScriptUtils.isLetterPartOfScript(firstCodePoint, script)
                && '\'' != firstCodePoint) return CHECKABILITY_FIRST_LETTER_UNCHECKABLE;

        // Filter contents
        final int length = text.length();
        int letterCount = 0;
        for (int i = 0; i < length; i = text.offsetByCodePoints(i, 1)) {
            final int codePoint = text.codePointAt(i);
            // Any word containing a COMMERCIAL_AT is probably an e-mail address
            // Any word containing a SLASH is probably either an ad-hoc combination of two
            // words or a URI - in either case we don't want to spell check that
            if (Constants.CODE_COMMERCIAL_AT == codePoint || Constants.CODE_SLASH == codePoint) {
                return CHECKABILITY_EMAIL_OR_URL;
            }
            // If the string contains a period, native returns strange suggestions (it seems
            // to return suggestions for everything up to the period only and to ignore the
            // rest), so we suppress lookup if there is a period.
            // TODO: investigate why native returns these suggestions and remove this code.
            if (Constants.CODE_PERIOD == codePoint) {
                return CHECKABILITY_CONTAINS_PERIOD;
            }
            if (ScriptUtils.isLetterPartOfScript(codePoint, script)) ++letterCount;
        }
        // Guestimate heuristic: perform spell checking if at least 3/4 of the characters
        // in this word are letters
        return (letterCount * 4 < length * 3)
                ? CHECKABILITY_TOO_MANY_NON_LETTERS : CHECKABILITY_CHECKABLE;
    }

    /**
     * Helper method to test valid capitalizations of a word.
     *
     * If the "text" is lower-case, we test only the exact string.
     * If the "Text" is capitalized, we test the exact string "Text" and the lower-cased
     *  version of it "text".
     * If the "TEXT" is fully upper case, we test the exact string "TEXT", the lower-cased
     *  version of it "text" and the capitalized version of it "Text".
     */
    private boolean isInDictForAnyCapitalization(final String text, final int capitalizeType) {
        // If the word is in there as is, then it's in the dictionary. If not, we'll test lower
        // case versions, but only if the word is not already all-lower case or mixed case.
        if (mService.isValidWord(mLocale, text)) return true;
        if (StringUtils.CAPITALIZE_NONE == capitalizeType) return false;

        // If we come here, we have a capitalized word (either First- or All-).
        // Downcase the word and look it up again. If the word is only capitalized, we
        // tested all possibilities, so if it's still negative we can return false.
        final String lowerCaseText = text.toLowerCase(mLocale);
        if (mService.isValidWord(mLocale, lowerCaseText)) return true;
        if (StringUtils.CAPITALIZE_FIRST == capitalizeType) return false;

        // If the lower case version is not in the dictionary, it's still possible
        // that we have an all-caps version of a word that needs to be capitalized
        // according to the dictionary. E.g. "GERMANS" only exists in the dictionary as "Germans".
        return mService.isValidWord(mLocale,
                StringUtils.capitalizeFirstAndDowncaseRest(lowerCaseText, mLocale));
    }

    // Note : this must be reentrant
    /**
     * Gets a list of suggestions for a specific string. This returns a list of possible
     * corrections for the text passed as an argument. It may split or group words, and
     * even perform grammatical analysis.
     */
    private SuggestionsInfo onGetSuggestionsInternal(final TextInfo textInfo,
            final int suggestionsLimit) {
        return onGetSuggestionsInternal(textInfo, null, suggestionsLimit);
    }

    protected SuggestionsInfo onGetSuggestionsInternal(
            final TextInfo textInfo, final NgramContext ngramContext, final int suggestionsLimit) {
        try {
            updateLocale();
            // It's good to keep this not local specific since the standard
            // ones may show up in other languages also.
            String text = textInfo.getText().
                    replaceAll(AndroidSpellCheckerService.APOSTROPHE,
                            AndroidSpellCheckerService.SINGLE_QUOTE).
                    replaceAll("^" + quotesRegexp, "").
                    replaceAll(quotesRegexp + "$", "");

            final String localeRegex = scriptToPunctuationRegexMap.get(
                    ScriptUtils.getScriptFromSpellCheckerLocale(mLocale)
            );

            if (localeRegex != null) {
                text = text.replaceAll(localeRegex, "");
            }

            if (!mService.hasMainDictionaryForLocale(mLocale)) {
                return AndroidSpellCheckerService.getNotInDictEmptySuggestions(
                        false /* reportAsTypo */);
            }

            // Handle special patterns like email, URI, telephone number.
            final int checkability = getCheckabilityInScript(text, mScript);
            if (CHECKABILITY_CHECKABLE != checkability) {
                // CHECKABILITY_CONTAINS_PERIOD Typo should not be reported when text is a valid word followed by a single period (end of sentence).
                boolean periodOnlyAtLastIndex = text.indexOf(Constants.CODE_PERIOD) == (text.length() - 1);
                if (CHECKABILITY_CONTAINS_PERIOD == checkability) {
                    final String[] splitText = text.split(Constants.REGEXP_PERIOD);
                    boolean allWordsAreValid = true;
                    // Validate all words on both sides of periods, skip empty tokens due to periods at first/last index
                    for (final String word : splitText) {
                        if (!word.isEmpty() && !mService.isValidWord(mLocale, word) && !mService.isValidWord(mLocale, word.toLowerCase(mLocale))) {
                            allWordsAreValid = false;
                            break;
                        }
                    }
                    if (allWordsAreValid && !periodOnlyAtLastIndex) {
                        return new SuggestionsInfo(SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                                | SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS,
                                new String[] {
                                        TextUtils.join(Constants.STRING_SPACE, splitText) });
                    }
                }
                return mService.isValidWord(mLocale, text) ?
                        AndroidSpellCheckerService.getInDictEmptySuggestions() :
                        AndroidSpellCheckerService.getNotInDictEmptySuggestions(!periodOnlyAtLastIndex);
            }

            // Handle normal words.
            final int capitalizeType = StringUtils.getCapitalizationType(text);

            if (isInDictForAnyCapitalization(text, capitalizeType)) {
                if (DebugFlags.DEBUG_ENABLED) {
                    Log.i(TAG, "onGetSuggestionsInternal() : [" + text + "] is a valid word");
                }
                return AndroidSpellCheckerService.getInDictEmptySuggestions();
            }
            if (DebugFlags.DEBUG_ENABLED) {
                Log.i(TAG, "onGetSuggestionsInternal() : [" + text + "] is NOT a valid word");
            }

            final Keyboard keyboard = mService.getKeyboardForLocale(mLocale);
            if (null == keyboard) {
                Log.w(TAG, "onGetSuggestionsInternal() : No keyboard for locale: " + mLocale);
                // If there is no keyboard for this locale, don't do any spell-checking.
                return AndroidSpellCheckerService.getNotInDictEmptySuggestions(
                        false /* reportAsTypo */);
            }

            final WordComposer composer = new WordComposer();
            final int[] codePoints = StringUtils.toCodePointArray(text);
            final int[] coordinates;
            coordinates = keyboard.getCoordinates(codePoints);
            composer.setComposingWord(codePoints, coordinates);
            // TODO: Don't gather suggestions if the limit is <= 0 unless necessary
            final SuggestionResults suggestionResults = mService.getSuggestionResults(
                    mLocale, composer.getComposedDataSnapshot(), ngramContext, keyboard);
            final Result result = getResult(capitalizeType, mLocale, suggestionsLimit,
                    mService.getRecommendedThreshold(), text, suggestionResults);
            if (DebugFlags.DEBUG_ENABLED) {
                if (result.mSuggestions != null && result.mSuggestions.length > 0) {
                    final StringBuilder builder = new StringBuilder();
                    for (String suggestion : result.mSuggestions) {
                        builder.append(" [");
                        builder.append(suggestion);
                        builder.append("]");
                    }
                    Log.i(TAG, "onGetSuggestionsInternal() : Suggestions =" + builder);
                }
            }
            // Handle word not in dictionary.
            // This is called only once per unique word, so entering multiple
            // instances of the same word does not result in more than one call
            // to this method.
            // Also, upon changing the orientation of the device, this is called
            // again for every unique invalid word in the text box.
            StatsUtils.onInvalidWordIdentification(text);

            final int flags =
                    SuggestionsInfo.RESULT_ATTR_LOOKS_LIKE_TYPO
                    | (result.mHasRecommendedSuggestions
                            ? SuggestionsInfo.RESULT_ATTR_HAS_RECOMMENDED_SUGGESTIONS
                            : 0);
            final SuggestionsInfo retval = new SuggestionsInfo(flags, result.mSuggestions);
            mSuggestionsCache.putSuggestionsToCache(text, result.mSuggestions, flags);
            return retval;
        } catch (RuntimeException e) {
            // Don't kill the keyboard if there is a bug in the spell checker
            Log.e(TAG, "Exception while spellchecking", e);
            return AndroidSpellCheckerService.getNotInDictEmptySuggestions(
                    false /* reportAsTypo */);
        }
    }

    private static final class Result {
        public final String[] mSuggestions;
        public final boolean mHasRecommendedSuggestions;
        public Result(final String[] gatheredSuggestions, final boolean hasRecommendedSuggestions) {
            mSuggestions = gatheredSuggestions;
            mHasRecommendedSuggestions = hasRecommendedSuggestions;
        }
    }

    private static Result getResult(final int capitalizeType, final Locale locale,
            final int suggestionsLimit, final float recommendedThreshold, final String originalText,
            final SuggestionResults suggestionResults) {
        if (suggestionResults.isEmpty() || suggestionsLimit <= 0) {
            return new Result(null /* gatheredSuggestions */,
                    false /* hasRecommendedSuggestions */);
        }
        final ArrayList<String> suggestions = new ArrayList<>();
        for (final SuggestedWordInfo suggestedWordInfo : suggestionResults) {
            final String suggestion;
            if (StringUtils.CAPITALIZE_ALL == capitalizeType) {
                suggestion = suggestedWordInfo.mWord.toUpperCase(locale);
            } else if (StringUtils.CAPITALIZE_FIRST == capitalizeType) {
                suggestion = StringUtils.capitalizeFirstCodePoint(
                        suggestedWordInfo.mWord, locale);
            } else {
                suggestion = suggestedWordInfo.mWord;
            }
            suggestions.add(suggestion);
        }
        StringUtils.removeDupes(suggestions);
        // This returns a String[], while toArray() returns an Object[] which cannot be cast
        // into a String[].
        final List<String> gatheredSuggestionsList =
                suggestions.subList(0, Math.min(suggestions.size(), suggestionsLimit));
        final String[] gatheredSuggestions =
                gatheredSuggestionsList.toArray(new String[gatheredSuggestionsList.size()]);

        final int bestScore = suggestionResults.first().mScore;
        final String bestSuggestion = suggestions.get(0);
        final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                originalText, bestSuggestion, bestScore);
        final boolean hasRecommendedSuggestions = (normalizedScore > recommendedThreshold);
        return new Result(gatheredSuggestions, hasRecommendedSuggestions);
    }

    /*
     * The spell checker acts on its own behalf. That is needed, in particular, to be able to
     * access the dictionary files, which the provider restricts to the identity of Latin IME.
     * Since it's called externally by the application, the spell checker is using the identity
     * of the application by default unless we clearCallingIdentity.
     * That's what the following method does.
     */
    @Override
    public SuggestionsInfo onGetSuggestions(final TextInfo textInfo, final int suggestionsLimit) {
        long ident = Binder.clearCallingIdentity();
        try {
            return onGetSuggestionsInternal(textInfo, suggestionsLimit);
        } finally {
            Binder.restoreCallingIdentity(ident);
        }
    }
}
