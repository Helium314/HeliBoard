// SPDX-License-Identifier: GPL-3.0-only

package helium314.keyboard.latin;

import android.content.Context;

import androidx.annotation.Nullable;

import com.android.inputmethod.latin.BinaryDictionary;

import java.io.File;
import java.util.Locale;

import helium314.keyboard.latin.common.StringUtils;
import helium314.keyboard.latin.utils.Log;

public class AppsBinaryDictionary extends ExpandableBinaryDictionary {
    private static final String TAG = AppsBinaryDictionary.class.getSimpleName();
    private static final String NAME = "apps";

    private static final boolean DEBUG = false;
    private static final boolean DEBUG_DUMP = false;

    private final AppsManager mAppsManager;

    protected AppsBinaryDictionary(final Context ctx, final Locale locale,
            final File dictFile, final String name) {
        super(ctx, getDictName(name, locale, dictFile), locale, Dictionary.TYPE_APPS, dictFile);
        mAppsManager = new AppsManager(ctx);
        reloadDictionaryIfRequired();
    }

    public static AppsBinaryDictionary getDictionary(final Context context, final Locale locale,
            final File dictFile, final String dictNamePrefix, @Nullable final String account) {
        return new AppsBinaryDictionary(context, locale, dictFile, dictNamePrefix + NAME);
    }

    /**
     * Typically called whenever the dictionary is created for the first time or recreated when we
     * think that there are updates to the dictionary. This is called asynchronously.
     */
    @Override
    public void loadInitialContentsLocked() {
        loadDictionaryLocked();
    }

    /**
     * Loads app names to the dictionary.
     */
    private void loadDictionaryLocked() {
        for (final String name : mAppsManager.getNames()) {
            addNameLocked(name);
        }
    }

    /**
     * Adds the words in an app label to the binary dictionary along with their n-grams.
     */
    private void addNameLocked(final String appLabel) {
        NgramContext ngramContext = NgramContext.getEmptyPrevWordsContext(
                BinaryDictionary.MAX_PREV_WORD_COUNT_FOR_N_GRAM);
        // TODO: Better tokenization for non-Latin writing systems
        for (final String word : new LatinTokens(appLabel)) {
            if (DEBUG_DUMP) {
                Log.d(TAG, "addName word = " + word);
            }
            final int wordLen = StringUtils.codePointCount(word);
            // Don't add single letter words, possibly confuses capitalization of i.
            if (1 < wordLen && wordLen <= MAX_WORD_LENGTH) {
                if (DEBUG) {
                    Log.d(TAG, "addName " + appLabel + ", " + word + ", "  + ngramContext);
                }
                runGCIfRequiredLocked(true /* mindsBlockByGC */);
                addUnigramLocked(word, AppsDictionaryConstantsKt.FREQUENCY_FOR_APPS,
                        null /* shortcut */, 0 /* shortcutFreq */, false /* isNotAWord */,
                        false /* isPossiblyOffensive */,
                        BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                if (ngramContext.isValid()) {
                    runGCIfRequiredLocked(true /* mindsBlockByGC */);
                    addNgramEntryLocked(ngramContext,
                            word,
                            AppsDictionaryConstantsKt.FREQUENCY_FOR_APPS_BIGRAM,
                            BinaryDictionary.NOT_A_VALID_TIMESTAMP);
                }
                ngramContext = ngramContext.getNextNgramContext(
                        new NgramContext.WordInfo(word));
            }
        }
    }
}
