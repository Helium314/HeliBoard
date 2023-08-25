/*
 * Copyright (C) 2008 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin;

import android.text.TextUtils;

import org.dslul.openboard.inputmethod.keyboard.Keyboard;
import org.dslul.openboard.inputmethod.keyboard.KeyboardId;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.common.ComposedData;
import org.dslul.openboard.inputmethod.latin.common.Constants;
import org.dslul.openboard.inputmethod.latin.common.InputPointers;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion;
import org.dslul.openboard.inputmethod.latin.utils.AutoCorrectionUtils;
import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;
import org.dslul.openboard.inputmethod.latin.utils.SuggestionResults;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;

import javax.annotation.Nonnull;

import static org.dslul.openboard.inputmethod.latin.define.DecoderSpecificConstants.SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION;
import static org.dslul.openboard.inputmethod.latin.define.DecoderSpecificConstants.SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION;

/**
 * This class loads a dictionary and provides a list of suggestions for a given sequence of
 * characters. This includes corrections and completions.
 */
public final class Suggest {
    public static final String TAG = Suggest.class.getSimpleName();

    // Session id for
    // {@link #getSuggestedWords(WordComposer,String,ProximityInfo,boolean,int)}.
    // We are sharing the same ID between typing and gesture to save RAM footprint.
    public static final int SESSION_ID_TYPING = 0;
    public static final int SESSION_ID_GESTURE = 0;

    // Close to -2**31
    private static final int SUPPRESS_SUGGEST_THRESHOLD = -2000000000;

    private static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private final DictionaryFacilitator mDictionaryFacilitator;

    private static final int MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN = 12;
    private static final HashMap<String, Integer> sLanguageToMaximumAutoCorrectionWithSpaceLength =
            new HashMap<>();
    static {
        // TODO: should we add Finnish here?
        // TODO: This should not be hardcoded here but be written in the dictionary header
        sLanguageToMaximumAutoCorrectionWithSpaceLength.put(Locale.GERMAN.getLanguage(),
                MAXIMUM_AUTO_CORRECT_LENGTH_FOR_GERMAN);
    }

    private float mAutoCorrectionThreshold;
    private float mPlausibilityThreshold;

    public Suggest(final DictionaryFacilitator dictionaryFacilitator) {
        mDictionaryFacilitator = dictionaryFacilitator;
    }

    /**
     * Set the normalized-score threshold for a suggestion to be considered strong enough that we
     * will auto-correct to this.
     * @param threshold the threshold
     */
    public void setAutoCorrectionThreshold(final float threshold) {
        mAutoCorrectionThreshold = threshold;
    }

    /**
     * Set the normalized-score threshold for what we consider a "plausible" suggestion, in
     * the same dimension as the auto-correction threshold.
     * @param threshold the threshold
     */
    public void setPlausibilityThreshold(final float threshold) {
        mPlausibilityThreshold = threshold;
    }

    public interface OnGetSuggestedWordsCallback {
        void onGetSuggestedWords(final SuggestedWords suggestedWords);
    }

    public void getSuggestedWords(final WordComposer wordComposer,
            final NgramContext ngramContext, final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final boolean isCorrectionEnabled, final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        if (wordComposer.isBatchMode()) {
            getSuggestedWordsForBatchInput(wordComposer, ngramContext, keyboard,
                    settingsValuesForSuggestion, inputStyle, sequenceNumber, callback);
        } else {
            getSuggestedWordsForNonBatchInput(wordComposer, ngramContext, keyboard,
                    settingsValuesForSuggestion, inputStyle, isCorrectionEnabled,
                    sequenceNumber, callback);
        }
    }

    private static ArrayList<SuggestedWordInfo> getTransformedSuggestedWordInfoList(
            final WordComposer wordComposer, final SuggestionResults results,
            final int trailingSingleQuotesCount, final Locale defaultLocale) {
        final boolean shouldMakeSuggestionsAllUpperCase = wordComposer.isAllUpperCase()
                && !wordComposer.isResumed();
        final boolean isOnlyFirstCharCapitalized =
                wordComposer.isOrWillBeOnlyFirstCharCapitalized();

        final ArrayList<SuggestedWordInfo> suggestionsContainer = new ArrayList<>(results);
        final int suggestionsCount = suggestionsContainer.size();
        if (isOnlyFirstCharCapitalized || shouldMakeSuggestionsAllUpperCase
                || 0 != trailingSingleQuotesCount) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final Locale wordLocale = wordInfo.mSourceDict.mLocale;
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, null == wordLocale ? defaultLocale : wordLocale,
                        shouldMakeSuggestionsAllUpperCase, isOnlyFirstCharCapitalized,
                        trailingSingleQuotesCount);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }
        return suggestionsContainer;
    }

    private static SuggestedWordInfo getWhitelistedWordInfoOrNull(
            @Nonnull final ArrayList<SuggestedWordInfo> suggestions) {
        if (suggestions.isEmpty()) {
            return null;
        }
        final SuggestedWordInfo firstSuggestedWordInfo = suggestions.get(0);
        if (!firstSuggestedWordInfo.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) {
            return null;
        }
        return firstSuggestedWordInfo;
    }

    // Retrieves suggestions for non-batch input (typing, recorrection, predictions...)
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForNonBatchInput(final WordComposer wordComposer,
            final NgramContext ngramContext, final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int inputStyleIfNotPrediction, final boolean isCorrectionEnabled,
            final int sequenceNumber, final OnGetSuggestedWordsCallback callback) {
        final String typedWordString = wordComposer.getTypedWord();
        final int trailingSingleQuotesCount =
                StringUtils.getTrailingSingleQuotesCount(typedWordString);
        final String consideredWord = trailingSingleQuotesCount > 0
                ? typedWordString.substring(0, typedWordString.length() - trailingSingleQuotesCount)
                : typedWordString;

        final SuggestionResults suggestionResults = mDictionaryFacilitator.getSuggestionResults(
                wordComposer.getComposedDataSnapshot(), ngramContext, keyboard,
                settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyleIfNotPrediction);
        final Locale locale = mDictionaryFacilitator.getLocale();
        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                getTransformedSuggestedWordInfoList(wordComposer, suggestionResults,
                        trailingSingleQuotesCount, locale);

        boolean foundInDictionary = false;
        Dictionary sourceDictionaryOfRemovedWord = null;
        // store the original SuggestedWordInfo for typed word, as it will be removed
        // we may want to re-add it in case auto-correction happens, so that the original word can at least be selected
        SuggestedWordInfo typedWordFirstOccurrenceWordInfo = null;
        for (final SuggestedWordInfo info : suggestionsContainer) {
            // Search for the best dictionary, defined as the first one with the highest match
            // quality we can find.
            if (!foundInDictionary && typedWordString.equals(info.mWord)) {
                // Use this source if the old match had lower quality than this match
                sourceDictionaryOfRemovedWord = info.mSourceDict;
                foundInDictionary = true;
                typedWordFirstOccurrenceWordInfo = info;
                break;
            }
        }

        final int firstOccurrenceOfTypedWordInSuggestions =
                SuggestedWordInfo.removeDupsAndTypedWord(typedWordString, suggestionsContainer);

        final SuggestedWordInfo whitelistedWordInfo =
                getWhitelistedWordInfoOrNull(suggestionsContainer);
        final String whitelistedWord = whitelistedWordInfo == null
                ? null : whitelistedWordInfo.mWord;
        final boolean resultsArePredictions = !wordComposer.isComposingWord();

        final SuggestedWordInfo firstSuggestionInContainer = suggestionsContainer.isEmpty() ? null : suggestionsContainer.get(0);
        // SuggestedWordInfos for suggestions for empty word (based only on previously typed words)
        // done in a weird way to imitate what kotlin does with lazy
        final ArrayList<SuggestedWordInfo> firstAndTypedWordEmptyInfos = new ArrayList<>(2);

        // We allow auto-correction if whitelisting is not required or the word is whitelisted,
        // or if the word had more than one char and was not suggested.
        final boolean allowsToBeAutoCorrected;
        if ((SHOULD_AUTO_CORRECT_USING_NON_WHITE_LISTED_SUGGESTION || whitelistedWord != null)
                || (consideredWord.length() > 1 && (sourceDictionaryOfRemovedWord == null)) // more than 1 letter and not in dictionary
        ) {
            allowsToBeAutoCorrected = true;
        } else if (firstSuggestionInContainer != null && !typedWordString.isEmpty()) {
            // maybe allow autocorrect, depending on emptyWordSuggestions
            putEmptyWordSuggestions(firstAndTypedWordEmptyInfos,
                    ngramContext, keyboard, settingsValuesForSuggestion, inputStyleIfNotPrediction,
                    firstSuggestionInContainer.getWord(), typedWordString);
            final SuggestedWordInfo first = firstAndTypedWordEmptyInfos.get(0);
            final SuggestedWordInfo typed = firstAndTypedWordEmptyInfos.get(1);
            if (first == null) {
                allowsToBeAutoCorrected = false; // no autocorrect if first suggestion unknown in this context
            } else if (typed == null) {
                allowsToBeAutoCorrected = true; // autocorrect if typed word not known in this context (this may be too aggressive)
            } else {
                // autocorrect only if suggested word has clearly higher score
                // todo: maybe adjust the score difference? but already 15 requires typing several times (but doesn't go back quickly...)
                //  maybe this should depend on mAutoCorrectionThreshold
                //  0.185 for modest, 0.067 for aggressive, negative infinity for very aggressive
                allowsToBeAutoCorrected = (first.mScore - typed.mScore) > 20;
            }
        } else
            allowsToBeAutoCorrected = false;
        // todo: hope autocorrect doesn't trigger too often now (remove this comment if ok)
        //  yes, it triggered too often / weirdly in some cases, but hopefully improved

        final boolean hasAutoCorrection;
        // If correction is not enabled, we never auto-correct. This is for example for when
        // the setting "Auto-correction" is "off": we still suggest, but we don't auto-correct.
        if (!isCorrectionEnabled
                // If the word does not allow to be auto-corrected, then we don't auto-correct.
                || !allowsToBeAutoCorrected
                // If we are doing prediction, then we never auto-correct of course
                || resultsArePredictions
                // If we don't have suggestion results, we can't evaluate the first suggestion
                // for auto-correction
                || suggestionResults.isEmpty()
                // If the word has digits, we never auto-correct because it's likely the word
                // was type with a lot of care
                || wordComposer.hasDigits()
                // If the word is mostly caps, we never auto-correct because this is almost
                // certainly intentional (and careful input)
                || wordComposer.isMostlyCaps()
                // We never auto-correct when suggestions are resumed because it would be unexpected
                || wordComposer.isResumed()
                // We don't autocorrect in URL or email input, since websites and emails can be
                // deliberate misspellings of actual words
                || keyboard.mId.mMode == KeyboardId.MODE_URL
                || keyboard.mId.mMode == KeyboardId.MODE_EMAIL
                // If we don't have a main dictionary, we never want to auto-correct. The reason
                // for this is, the user may have a contact whose name happens to match a valid
                // word in their language, and it will unexpectedly auto-correct. For example, if
                // the user types in English with no dictionary and has a "Will" in their contact
                // list, "will" would always auto-correct to "Will" which is unwanted. Hence, no
                // main dict => no auto-correct. Also, it would probably get obnoxious quickly.
                // TODO: now that we have personalization, we may want to re-evaluate this decision
                || !mDictionaryFacilitator.hasAtLeastOneInitializedMainDictionary()
                // If the first suggestion is a shortcut we never auto-correct to it, regardless
                // of how strong it is (whitelist entries are not KIND_SHORTCUT but KIND_WHITELIST).
                // TODO: we may want to have shortcut-only entries auto-correct in the future.
                || suggestionResults.first().isKindOf(SuggestedWordInfo.KIND_SHORTCUT)) {
            hasAutoCorrection = false;
        } else {
            final SuggestedWordInfo firstSuggestion = suggestionResults.first();
            if (suggestionResults.mFirstSuggestionExceedsConfidenceThreshold
                    && firstOccurrenceOfTypedWordInSuggestions != 0) {
                // todo: mFirstSuggestionExceedsConfidenceThreshold is always false, so currently
                //  this branch is useless. remove the related logic, or actually use it
                hasAutoCorrection = true;
            } else if (!AutoCorrectionUtils.suggestionExceedsThreshold(
                    firstSuggestion, consideredWord, mAutoCorrectionThreshold)) {
                // todo: maybe also do something here depending on ngram context?
                // Score is too low for autocorrect
                hasAutoCorrection = false;
            } else {
                // We have a high score, so we need to check if this suggestion is in the correct
                // form to allow auto-correcting to it in this language. For details of how this
                // is determined, see #isAllowedByAutoCorrectionWithSpaceFilter.
                // TODO: this should not have its own logic here but be handled by the dictionary.
                final boolean allowed = isAllowedByAutoCorrectionWithSpaceFilter(firstSuggestion);
                // todo: the threshold (currently 1000000) may need tuning
                if (allowed && typedWordFirstOccurrenceWordInfo != null && typedWordFirstOccurrenceWordInfo.mScore > 1000000) {
                    // typed word is valid and has good score
                    // do not auto-correct if typed word is better prediction than possible correction from ngram context alone
                    final SuggestedWordInfo first = firstSuggestionInContainer != null ? firstSuggestionInContainer : firstSuggestion;
                    putEmptyWordSuggestions(firstAndTypedWordEmptyInfos,
                            ngramContext, keyboard, settingsValuesForSuggestion, inputStyleIfNotPrediction,
                            first.getWord(), typedWordString);
                    int firstScoreForEmpty = firstAndTypedWordEmptyInfos.get(0) != null ? firstAndTypedWordEmptyInfos.get(0).mScore : 0;
                    int typedScoreForEmpty = firstAndTypedWordEmptyInfos.get(1) != null ? firstAndTypedWordEmptyInfos.get(1).mScore : 0;
                    final Locale dictLocale = mDictionaryFacilitator.getCurrentLocale();
                    // slightly prefer suggestion for the current locale, this is very useful e.g.
                    // for Polish i vs English I, or French un vs un->in shortcut in English default dictionary
                    if (dictLocale == first.mSourceDict.mLocale)
                        firstScoreForEmpty += 1;
                    if (dictLocale == typedWordFirstOccurrenceWordInfo.mSourceDict.mLocale)
                        typedScoreForEmpty += 1;
                    hasAutoCorrection = firstScoreForEmpty >= typedScoreForEmpty;
                } else
                    hasAutoCorrection = allowed;
            }
        }

        final SuggestedWordInfo typedWordInfo = new SuggestedWordInfo(typedWordString,
                "" /* prevWordsContext */, SuggestedWordInfo.MAX_SCORE,
                SuggestedWordInfo.KIND_TYPED,
                null == sourceDictionaryOfRemovedWord ? Dictionary.DICTIONARY_USER_TYPED
                        : sourceDictionaryOfRemovedWord,
                SuggestedWordInfo.NOT_AN_INDEX /* indexOfTouchPointOfSecondWord */,
                SuggestedWordInfo.NOT_A_CONFIDENCE /* autoCommitFirstWordConfidence */);
        if (!TextUtils.isEmpty(typedWordString)) {
            suggestionsContainer.add(0, typedWordInfo);
        }

        final ArrayList<SuggestedWordInfo> suggestionsList;
        if (DBG && !suggestionsContainer.isEmpty()) {
            suggestionsList = getSuggestionsInfoListWithDebugInfo(typedWordString,
                    suggestionsContainer);
        } else {
            suggestionsList = suggestionsContainer;
        }

        final int inputStyle;
        if (resultsArePredictions) {
            inputStyle = suggestionResults.mIsBeginningOfSentence
                    ? SuggestedWords.INPUT_STYLE_BEGINNING_OF_SENTENCE_PREDICTION
                    : SuggestedWords.INPUT_STYLE_PREDICTION;
        } else {
            inputStyle = inputStyleIfNotPrediction;
        }

        final boolean isTypedWordValid = firstOccurrenceOfTypedWordInSuggestions > -1
                || (!resultsArePredictions && !allowsToBeAutoCorrected);

        if (hasAutoCorrection && typedWordFirstOccurrenceWordInfo != null) {
            // typed word is valid (in suggestions), but will not be shown if hasAutoCorrection
            // -> add it after the auto-correct suggestion
            // todo: it would be better to adjust this in SuggestedWords (getWordCountToShow, maybe more)
            //  and SuggestionStripView (shouldOmitTypedWord, getStyledSuggestedWord)
            //  but this could become more complicated than simply adding a duplicate word in a case
            //  where the first occurrence of that word is ignored
            suggestionsList.add(2, typedWordFirstOccurrenceWordInfo);
        }

        callback.onGetSuggestedWords(new SuggestedWords(suggestionsList,
                suggestionResults.mRawSuggestions, typedWordInfo,
                isTypedWordValid,
                hasAutoCorrection /* willAutoCorrect */,
                false /* isObsoleteSuggestions */, inputStyle, sequenceNumber));
    }

    // annoyingly complicated thing to avoid getting emptyWordSuggestions more than once
    /** puts word infos for suggestions with an empty word in [infos], based on previously typed words */
    private ArrayList<SuggestedWordInfo> putEmptyWordSuggestions(ArrayList<SuggestedWordInfo> infos, NgramContext ngramContext,
                    Keyboard keyboard, SettingsValuesForSuggestion settingsValuesForSuggestion,
                    int inputStyleIfNotPrediction, String firstSuggestionInContainer, String typedWordString) {
        if (infos.size() != 0) return infos;
        infos.add(null);
        infos.add(null);
        final SuggestionResults emptyWordSuggestions = mDictionaryFacilitator.getSuggestionResults(
                new ComposedData(new InputPointers(1), false, ""), ngramContext,
                keyboard, settingsValuesForSuggestion, SESSION_ID_TYPING, inputStyleIfNotPrediction);
        for (SuggestedWordInfo info : emptyWordSuggestions) {
            if (infos.get(1) == null && typedWordString.equals(info.getWord())) {
                infos.set(1, info);
            } else if (infos.get(0) == null && firstSuggestionInContainer.equals(info.getWord())) {
                infos.set(0, info);
            } else if (infos.get(1) != null && infos.get(0) != null)
                break;
        }
        return infos;
    }

    // Retrieves suggestions for the batch input
    // and calls the callback function with the suggestions.
    private void getSuggestedWordsForBatchInput(final WordComposer wordComposer,
            final NgramContext ngramContext, final Keyboard keyboard,
            final SettingsValuesForSuggestion settingsValuesForSuggestion,
            final int inputStyle, final int sequenceNumber,
            final OnGetSuggestedWordsCallback callback) {
        final SuggestionResults suggestionResults = mDictionaryFacilitator.getSuggestionResults(
                wordComposer.getComposedDataSnapshot(), ngramContext, keyboard,
                settingsValuesForSuggestion, SESSION_ID_GESTURE, inputStyle);

        // For transforming words that don't come from a dictionary, because it's our best bet
        final Locale locale = mDictionaryFacilitator.getLocale();
        final ArrayList<SuggestedWordInfo> suggestionsContainer =
                new ArrayList<>(suggestionResults);
        final int suggestionsCount = suggestionsContainer.size();
        final boolean isFirstCharCapitalized = wordComposer.wasShiftedNoLock();
        final boolean isAllUpperCase = wordComposer.isAllUpperCase();
        if (isFirstCharCapitalized || isAllUpperCase) {
            for (int i = 0; i < suggestionsCount; ++i) {
                final SuggestedWordInfo wordInfo = suggestionsContainer.get(i);
                final Locale wordlocale = wordInfo.mSourceDict.mLocale;
                final SuggestedWordInfo transformedWordInfo = getTransformedSuggestedWordInfo(
                        wordInfo, null == wordlocale ? locale : wordlocale, isAllUpperCase,
                        isFirstCharCapitalized, 0 /* trailingSingleQuotesCount */);
                suggestionsContainer.set(i, transformedWordInfo);
            }
        }

        if (SHOULD_REMOVE_PREVIOUSLY_REJECTED_SUGGESTION
                && suggestionsContainer.size() > 1
                && TextUtils.equals(suggestionsContainer.get(0).mWord,
                   wordComposer.getRejectedBatchModeSuggestion())) {
            final SuggestedWordInfo rejected = suggestionsContainer.remove(0);
            suggestionsContainer.add(1, rejected);
        }
        SuggestedWordInfo.removeDupsAndTypedWord(null /* typedWord */, suggestionsContainer);

        // For some reason some suggestions with MIN_VALUE are making their way here.
        // TODO: Find a more robust way to detect distracters.
        for (int i = suggestionsContainer.size() - 1; i >= 0; --i) {
            if (suggestionsContainer.get(i).mScore < SUPPRESS_SUGGEST_THRESHOLD) {
                suggestionsContainer.remove(i);
            }
        }

        // In the batch input mode, the most relevant suggested word should act as a "typed word"
        // (typedWordValid=true), not as an "auto correct word" (willAutoCorrect=false).
        // Note that because this method is never used to get predictions, there is no need to
        // modify inputType such in getSuggestedWordsForNonBatchInput.
        final SuggestedWordInfo pseudoTypedWordInfo = suggestionsContainer.isEmpty() ? null
                : suggestionsContainer.get(0);

        callback.onGetSuggestedWords(new SuggestedWords(suggestionsContainer,
                suggestionResults.mRawSuggestions,
                pseudoTypedWordInfo,
                true /* typedWordValid */,
                false /* willAutoCorrect */,
                false /* isObsoleteSuggestions */,
                inputStyle, sequenceNumber));
    }

    private static ArrayList<SuggestedWordInfo> getSuggestionsInfoListWithDebugInfo(
            final String typedWord, final ArrayList<SuggestedWordInfo> suggestions) {
        final SuggestedWordInfo typedWordInfo = suggestions.get(0);
        typedWordInfo.setDebugString("+");
        final int suggestionsSize = suggestions.size();
        final ArrayList<SuggestedWordInfo> suggestionsList = new ArrayList<>(suggestionsSize);
        suggestionsList.add(typedWordInfo);
        // Note: i here is the index in mScores[], but the index in mSuggestions is one more
        // than i because we added the typed word to mSuggestions without touching mScores.
        for (int i = 0; i < suggestionsSize - 1; ++i) {
            final SuggestedWordInfo cur = suggestions.get(i + 1);
            final float normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                    typedWord, cur.toString(), cur.mScore);
            final String scoreInfoString;
            if (normalizedScore > 0) {
                scoreInfoString = String.format(
                        Locale.ROOT, "%d (%4.2f), %s", cur.mScore, normalizedScore,
                        cur.mSourceDict.mDictType);
            } else {
                scoreInfoString = Integer.toString(cur.mScore);
            }
            cur.setDebugString(scoreInfoString);
            suggestionsList.add(cur);
        }
        return suggestionsList;
    }

    /**
     * Computes whether this suggestion should be blocked or not in this language
     *
     * This function implements a filter that avoids auto-correcting to suggestions that contain
     * spaces that are above a certain language-dependent character limit. In languages like German
     * where it's possible to concatenate many words, it often happens our dictionary does not
     * have the longer words. In this case, we offer a lot of unhelpful suggestions that contain
     * one or several spaces. Ideally we should understand what the user wants and display useful
     * suggestions by improving the dictionary and possibly having some specific logic. Until
     * that's possible we should avoid displaying unhelpful suggestions. But it's hard to tell
     * whether a suggestion is useful or not. So at least for the time being we block
     * auto-correction when the suggestion is long and contains a space, which should avoid the
     * worst damage.
     * This function is implementing that filter. If the language enforces no such limit, then it
     * always returns true. If the suggestion contains no space, it also returns true. Otherwise,
     * it checks the length against the language-specific limit.
     *
     * @param info the suggestion info
     * @return whether it's fine to auto-correct to this.
     */
    private static boolean isAllowedByAutoCorrectionWithSpaceFilter(final SuggestedWordInfo info) {
        final Locale locale = info.mSourceDict.mLocale;
        if (null == locale) {
            return true;
        }
        final Integer maximumLengthForThisLanguage =
                sLanguageToMaximumAutoCorrectionWithSpaceLength.get(locale.getLanguage());
        if (null == maximumLengthForThisLanguage) {
            // This language does not enforce a maximum length to auto-correction
            return true;
        }
        return info.mWord.length() <= maximumLengthForThisLanguage
                || -1 == info.mWord.indexOf(Constants.CODE_SPACE);
    }

    /* package for test */ static SuggestedWordInfo getTransformedSuggestedWordInfo(
            final SuggestedWordInfo wordInfo, final Locale locale, final boolean isAllUpperCase,
            final boolean isOnlyFirstCharCapitalized, final int trailingSingleQuotesCount) {
        final StringBuilder sb = new StringBuilder(wordInfo.mWord.length());
        if (isAllUpperCase) {
            sb.append(wordInfo.mWord.toUpperCase(locale));
        } else if (isOnlyFirstCharCapitalized) {
            sb.append(StringUtils.capitalizeFirstCodePoint(wordInfo.mWord, locale));
        } else {
            sb.append(wordInfo.mWord);
        }
        // Appending quotes is here to help people quote words. However, it's not helpful
        // when they type words with quotes toward the end like "it's" or "didn't", where
        // it's more likely the user missed the last character (or didn't type it yet).
        final int quotesToAppend = trailingSingleQuotesCount
                - (-1 == wordInfo.mWord.indexOf(Constants.CODE_SINGLE_QUOTE) ? 0 : 1);
        for (int i = quotesToAppend - 1; i >= 0; --i) {
            sb.appendCodePoint(Constants.CODE_SINGLE_QUOTE);
        }
        return new SuggestedWordInfo(sb.toString(), wordInfo.mPrevWordsContext,
                wordInfo.mScore, wordInfo.mKindAndFlags,
                wordInfo.mSourceDict, wordInfo.mIndexOfTouchPointOfSecondWord,
                wordInfo.mAutoCommitFirstWordConfidence);
    }
}