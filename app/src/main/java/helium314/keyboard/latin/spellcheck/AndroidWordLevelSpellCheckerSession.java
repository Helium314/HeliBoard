/*
 * Copyright (C) 2012 The Android Open Source Project
 * modified
 * SPDX-License-Identifier: Apache-2.0 AND GPL-3.0-only
 */

package helium314.keyboard.latin.spellcheck;

import android.content.ContentResolver;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Binder;
import android.provider.UserDictionary.Words;
import android.service.textservice.SpellCheckerService.Session;
import android.text.TextUtils;
import helium314.keyboard.latin.settings.Settings;
import helium314.keyboard.latin.utils.Log;
import android.util.LruCache;
import android.view.inputmethod.InputMethodManager;
import android.view.inputmethod.InputMethodSubtype;
import android.view.textservice.SuggestionsInfo;
import android.view.textservice.TextInfo;

import helium314.keyboard.keyboard.Keyboard;
import helium314.keyboard.latin.NgramContext;
import helium314.keyboard.latin.SuggestedWords.SuggestedWordInfo;
import helium314.keyboard.latin.WordComposer;
import helium314.keyboard.latin.common.LocaleUtils;
import helium314.keyboard.latin.common.StringUtils;
import helium314.keyboard.latin.define.DebugFlags;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;

import helium314.keyboard.latin.utils.DeviceProtectedUtils;
import helium314.keyboard.latin.utils.ScriptUtils;
import helium314.keyboard.latin.utils.StatsUtils;
import helium314.keyboard.latin.utils.SubtypeSettingsKt;
import helium314.keyboard.latin.utils.SuggestionResults;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

public abstract class AndroidWordLevelSpellCheckerSession extends Session {
    private static final String TAG = AndroidWordLevelSpellCheckerSession.class.getSimpleName();

    public final static String[] EMPTY_STRING_ARRAY = new String[0];

    public final static int FLAG_UNCHECKABLE = 0;

    // Immutable, but not available in the constructor.
    private Locale mLocale;
    // Cache this for performance
    private String mScript;
    private final AndroidSpellCheckerService mService;
    protected final SuggestionsCache mSuggestionsCache = new SuggestionsCache();
    private final ContentObserver mObserver;

    private static final String quotesRegexp =
            "(\\u0022|\\u0027|\\u0060|\\u00B4|\\u2018|\\u2018|\\u201C|\\u201D)";

    private static final Map<String, String> scriptToPunctuationRegexMap = new TreeMap<>();
    private List <Locale> localesToCheck;

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
        public final Locale mLocale;
        public SuggestionsParams(String[] suggestions, int flags, Locale locale) {
            mSuggestions = suggestions;
            mFlags = flags;
            mLocale = locale;
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
                final String query, final String[] suggestions, final int flags, final Locale locale) {
            if (suggestions == null || TextUtils.isEmpty(query)) {
                return;
            }
            mUnigramSuggestionsInfoCache.put(
                    generateKey(query),
                    new SuggestionsParams(suggestions, flags, locale));
        }

        public void clearCache() {
            mUnigramSuggestionsInfoCache.evictAll();
        }
    }

    AndroidWordLevelSpellCheckerSession(final AndroidSpellCheckerService service) {
        mService = service;
        mObserver = new ContentObserver(null) {
            @Override
            public void onChange(boolean self) {
                mSuggestionsCache.clearCache();
            }
        };
        service.getContentResolver().registerContentObserver(Words.CONTENT_URI, true, mObserver);
    }

    private void updateLocale() {
        final String localeString = getLocale();

        if (mLocale == null || !mLocale.toString().equals(localeString)) {
            final String oldLocale = mLocale == null ? "null" : mLocale.toString();
            Log.d(TAG, "Updating locale from " + oldLocale + " to " + localeString);

            mLocale = (null == localeString) ? null
                    : LocaleUtils.constructLocale(localeString);
            if (mLocale == null) mScript = ScriptUtils.SCRIPT_UNKNOWN;
            else mScript = ScriptUtils.script(mLocale);
        }
        if (localesToCheck.isEmpty())
            localesToCheck.add(mLocale);
        else if (!localesToCheck.get(0).equals(mLocale)) {
            // Set mLocale to be at the beginning so it is checked first
            localesToCheck.add(0, mLocale);
            // Make sure no other locales with the same script are checked
            for (int i = localesToCheck.size() - 1; i >= 1; i--) {
                Locale locale = localesToCheck.get(i);
                if (ScriptUtils.script(locale).equals(mScript)) {
                    localesToCheck.remove(i);
                }
            }
        }
    }

    @Override
    public void onCreate() {
        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(mService);
        localesToCheck = SubtypeSettingsKt.getUniqueScriptLocalesFromEnabledSubtypes(prefs);
        updateLocale();
    }

    @Override
    public String getLocale() { // unfortunately this can only return a string, with the obvious issues for
        // This function was taken from https://github.com/LineageOS/android_frameworks_base/blob/1235c24a0f092d0e41fd8e86f332f8dc03896a7b/services/core/java/com/android/server/TextServicesManagerService.java#L544 and slightly adopted.

        final InputMethodManager imm;
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            imm = mService.getApplicationContext().getSystemService(InputMethodManager.class);
            if (imm != null) {
                final InputMethodSubtype currentInputMethodSubtype = imm.getCurrentInputMethodSubtype();
                if (currentInputMethodSubtype != null) {
                    final String localeString = currentInputMethodSubtype.getLocale();
                    if (!TextUtils.isEmpty(localeString)) {
                        // Use keyboard locale if available in the spell checker
                        return localeString;
                    }
                    // localeString for this app is always empty, get it from settings if possible
                    // and we're sure this app is used
                    if (SubtypeSettingsKt.getInitialized() && "dummy".equals(currentInputMethodSubtype.getExtraValue())) {
                        final SharedPreferences prefs = DeviceProtectedUtils.getSharedPreferences(mService);
                        return SubtypeSettingsKt.getSelectedSubtype(prefs).getLocale();
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

    private static final int CHECKABILITY_EMAIL_OR_URL = 1;

    private static final int CHECKABILITY_TOO_SHORT = 2;
    /**
     * Finds out whether a particular string should be filtered out of spell checking.
     * <p>
     * This will match URLs if URL detection is enabled, as well as text that is too short.
     * @param text the string to evaluate.
     * @return one of the FILTER_OUT_* constants above.
     */
    private static int getCheckability(final String text) {
        if (TextUtils.isEmpty(text) || text.length() <= 1) return CHECKABILITY_TOO_SHORT;

        // TODO: check if an equivalent processing can't be done more quickly with a
        // compiled regexp.
        // Filter out e-mail address and URL
        if (Settings.getInstance().getCurrent().mUrlDetectionEnabled && StringUtils.findURLEndIndex(text) != -1) {
            return CHECKABILITY_EMAIL_OR_URL;
        }
        return CHECKABILITY_CHECKABLE;
    }

    /**
     * Helper method to test valid capitalizations of a word.
     * <p>
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
        return mService.isValidWord(mLocale, StringUtils.capitalizeFirstAndDowncaseRest(lowerCaseText, mLocale));
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
            final String textWithLocalePunctuations = textInfo.getText()
                    .replaceAll(AndroidSpellCheckerService.APOSTROPHE, AndroidSpellCheckerService.SINGLE_QUOTE)
                    .replaceAll("^" + quotesRegexp, "")
                    .replaceAll(quotesRegexp + "$", "");

            // Return quickly when suggestions are cached
            final SuggestionsParams suggestionsParams = mSuggestionsCache.getSuggestionsFromCache(textWithLocalePunctuations);
            if (suggestionsParams != null) {
                final int flag = suggestionsParams.mFlags;
                if (flag == FLAG_UNCHECKABLE) {
                    return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false);
                } else if (flag == SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY) {
                    return AndroidSpellCheckerService.getInDictEmptySuggestions();
                } else if (suggestionsParams.mLocale.equals(mLocale)) {
                    // Return corrective suggestions only if the locales match
                    return new SuggestionsInfo(suggestionsParams.mFlags, suggestionsParams.mSuggestions);
                }
            }

            // Find out which locale should be used
            boolean foundLocale = false;
            for (int i = 0, length = textWithLocalePunctuations.length(); !foundLocale && i < length; i++) {
                final int codePoint = textWithLocalePunctuations.codePointAt(i);
                for (Locale locale : localesToCheck) {
                    String localeScript = ScriptUtils.script(locale);
                    if (ScriptUtils.isLetterPartOfScript(codePoint, localeScript)) {
                        if (!mLocale.equals(locale)) {
                            Log.d(TAG, "Updating locale from " + mLocale + " to " + locale);
                            mLocale = locale;
                            mScript = localeScript;
                        }
                        foundLocale = true;
                        break;
                    }
                }
            }
            // If no locales were found, then the text probably contains numbers
            // or special characters only, so it should not be spell checked.
            if (!foundLocale || !mService.hasMainDictionaryForLocale(mLocale)) {
                mSuggestionsCache.putSuggestionsToCache(textWithLocalePunctuations, EMPTY_STRING_ARRAY, FLAG_UNCHECKABLE, mLocale);
                return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false);
            }
            final String localeRegex = scriptToPunctuationRegexMap.get(ScriptUtils.script(mLocale));
            final String text;
            if (localeRegex != null) {
                text = textWithLocalePunctuations.replaceAll(localeRegex, "");
            } else {
                text = textWithLocalePunctuations;
            }

            // Check if the text is too short and handle special patterns like email, URI.
            final int checkability = getCheckability(text);
            // Do not check uncheckable words against the dictionary.
            if (CHECKABILITY_CHECKABLE != checkability) {
                mSuggestionsCache.putSuggestionsToCache(textWithLocalePunctuations, EMPTY_STRING_ARRAY, FLAG_UNCHECKABLE, mLocale);
                return AndroidSpellCheckerService.getNotInDictEmptySuggestions(false);
            }

            // Handle normal words.
            final int capitalizeType = StringUtils.getCapitalizationType(text);

            if (isInDictForAnyCapitalization(text, capitalizeType)) {
                if (DebugFlags.DEBUG_ENABLED) {
                    Log.i(TAG, "onGetSuggestionsInternal() : [" + text + "] is a valid word");
                }
                mSuggestionsCache.putSuggestionsToCache(textWithLocalePunctuations, EMPTY_STRING_ARRAY,
                        SuggestionsInfo.RESULT_ATTR_IN_THE_DICTIONARY, mLocale);
                return AndroidSpellCheckerService.getInDictEmptySuggestions();
            }
            if (DebugFlags.DEBUG_ENABLED) {
                Log.i(TAG, "onGetSuggestionsInternal() : [" + text + "] is NOT a valid word");
            }

            final Keyboard keyboard = mService.getKeyboardForLocale(mLocale);

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
            mSuggestionsCache.putSuggestionsToCache(textWithLocalePunctuations, result.mSuggestions, flags, mLocale);
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
