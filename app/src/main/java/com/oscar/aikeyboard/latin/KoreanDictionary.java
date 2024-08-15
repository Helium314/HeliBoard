// SPDX-License-Identifier: GPL-3.0-only

package com.oscar.aikeyboard.latin;


import com.oscar.aikeyboard.event.HangulCombiner;
import com.oscar.aikeyboard.latin.common.ComposedData;
import com.oscar.aikeyboard.latin.settings.SettingsValuesForSuggestion;

import java.text.Normalizer;
import java.util.ArrayList;

/*
 * For Korean dictionary, there are too many cases of characters to store on dictionary, which makes it slow.
 * To solve that, Unicode normalization is used to decompose Hangul syllables into Hangul jamos.
 */
public class KoreanDictionary extends com.oscar.aikeyboard.latin.Dictionary {

    private static final String COMPAT_JAMO = HangulCombiner.HangulJamo.COMPAT_CONSONANTS + HangulCombiner.HangulJamo.COMPAT_VOWELS;
    private static final String STANDARD_JAMO = HangulCombiner.HangulJamo.CONVERT_INITIALS + HangulCombiner.HangulJamo.CONVERT_MEDIALS;

    private final com.oscar.aikeyboard.latin.Dictionary mDictionary;

    public KoreanDictionary(com.oscar.aikeyboard.latin.Dictionary dictionary) {
        super(dictionary.mDictType, dictionary.mLocale);
        mDictionary = dictionary;
    }

    private String processInput(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD);
        StringBuilder result = new StringBuilder();
        for (char c : normalized.toCharArray()) {
            int index = COMPAT_JAMO.indexOf(c);
            if (index == -1) result.append(c);
            else result.append(STANDARD_JAMO.charAt(index));
        }
        return result.toString();
    }

    private String processOutput(String output) {
        return Normalizer.normalize(output, Normalizer.Form.NFC);
    }

    @Override
    public ArrayList<com.oscar.aikeyboard.latin.SuggestedWords.SuggestedWordInfo> getSuggestions(ComposedData composedData,
                                                                                                 NgramContext ngramContext, long proximityInfoHandle, SettingsValuesForSuggestion settingsValuesForSuggestion,
                                                                                                 int sessionId, float weightForLocale, float[] inOutWeightOfLangModelVsSpatialModel) {
        composedData = new ComposedData(composedData.mInputPointers,
                composedData.mIsBatchMode, processInput(composedData.mTypedWord));
        ArrayList<com.oscar.aikeyboard.latin.SuggestedWords.SuggestedWordInfo> suggestions = mDictionary.getSuggestions(composedData,
                ngramContext, proximityInfoHandle, settingsValuesForSuggestion, sessionId,
                weightForLocale, inOutWeightOfLangModelVsSpatialModel);
        ArrayList<com.oscar.aikeyboard.latin.SuggestedWords.SuggestedWordInfo> result = new ArrayList<>();
        for (com.oscar.aikeyboard.latin.SuggestedWords.SuggestedWordInfo info : suggestions) {
            result.add(new com.oscar.aikeyboard.latin.SuggestedWords.SuggestedWordInfo(processOutput(info.mWord), info.mPrevWordsContext,
                    info.mScore, info.mKindAndFlags, info.mSourceDict, info.mIndexOfTouchPointOfSecondWord, info.mAutoCommitFirstWordConfidence));
        }
        return result;
    }

    @Override
    public boolean isInDictionary(String word) {
        return mDictionary.isInDictionary(processInput(word));
    }

    @Override
    public int getFrequency(String word) {
        return mDictionary.getFrequency(processInput(word));
    }

    @Override
    public int getMaxFrequencyOfExactMatches(String word) {
        return mDictionary.getMaxFrequencyOfExactMatches(processInput(word));
    }

    @Override
    protected boolean same(char[] word, int length, String typedWord) {
        word = processInput(new String(word)).toCharArray();
        typedWord = processInput(typedWord);
        return mDictionary.same(word, length, typedWord);
    }

    @Override
    public void close() {
        mDictionary.close();
    }

    @Override
    public void onFinishInput() {
        mDictionary.onFinishInput();
    }

    @Override
    public boolean isInitialized() {
        return mDictionary.isInitialized();
    }

    @Override
    public boolean shouldAutoCommit(SuggestedWords.SuggestedWordInfo candidate) {
        return mDictionary.shouldAutoCommit(candidate);
    }

    @Override
    public boolean isUserSpecific() {
        return mDictionary.isUserSpecific();
    }
}
