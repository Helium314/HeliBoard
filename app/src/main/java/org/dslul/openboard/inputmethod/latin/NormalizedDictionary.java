package org.dslul.openboard.inputmethod.latin;

import org.dslul.openboard.inputmethod.latin.common.ComposedData;
import org.dslul.openboard.inputmethod.latin.settings.SettingsValuesForSuggestion;

import java.text.Normalizer;
import java.util.ArrayList;

/*
 * For Korean dictionary, there are too many cases of characters to store on dictionary, which makes it slow.
 * To solve that, Unicode normalization is used to decompose Hangul syllables into Hangul jamos.
 */
public class NormalizedDictionary extends Dictionary {
    private final Normalizer.Form mInputForm;
    private final Normalizer.Form mOutputForm;
    private final Dictionary mDictionary;
    public NormalizedDictionary(Normalizer.Form inputForm, Normalizer.Form outputForm, Dictionary dictionary) {
        super(dictionary.mDictType, dictionary.mLocale);
        mInputForm = inputForm;
        mOutputForm = outputForm;
        mDictionary = dictionary;
    }

    @Override
    public ArrayList<SuggestedWords.SuggestedWordInfo> getSuggestions(ComposedData composedData, NgramContext ngramContext, long proximityInfoHandle, SettingsValuesForSuggestion settingsValuesForSuggestion, int sessionId, float weightForLocale, float[] inOutWeightOfLangModelVsSpatialModel) {
        composedData = new ComposedData(composedData.mInputPointers, composedData.mIsBatchMode, Normalizer.normalize(composedData.mTypedWord, mInputForm));
        ArrayList<SuggestedWords.SuggestedWordInfo> suggestions = mDictionary.getSuggestions(composedData, ngramContext, proximityInfoHandle, settingsValuesForSuggestion, sessionId, weightForLocale, inOutWeightOfLangModelVsSpatialModel);
        ArrayList<SuggestedWords.SuggestedWordInfo> result = new ArrayList<>();
        for(SuggestedWords.SuggestedWordInfo info : suggestions) {
            result.add(new SuggestedWords.SuggestedWordInfo(Normalizer.normalize(info.mWord, mOutputForm), info.mPrevWordsContext,
                    info.mScore, info.mKindAndFlags, info.mSourceDict, info.mIndexOfTouchPointOfSecondWord, info.mAutoCommitFirstWordConfidence));
        }
        return result;
    }

    @Override
    public boolean isInDictionary(String word) {
        return mDictionary.isInDictionary(Normalizer.normalize(word, mInputForm));
    }

    @Override
    public int getFrequency(String word) {
        return mDictionary.getFrequency(Normalizer.normalize(word, mInputForm));
    }

    @Override
    public int getMaxFrequencyOfExactMatches(String word) {
        return mDictionary.getMaxFrequencyOfExactMatches(Normalizer.normalize(word, mInputForm));
    }

    @Override
    protected boolean same(char[] word, int length, String typedWord) {
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
