/*
 * Copyright (C) 2011 The Android Open Source Project
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

package org.dslul.openboard.inputmethod.latin.utils;

import static android.view.KeyEvent.KEYCODE_SPACE;

import android.os.Build;
import android.util.Log;

import com.android.inputmethod.latin.utils.BinaryDictionaryUtils;

import org.dslul.openboard.inputmethod.annotations.UsedForTesting;
import org.dslul.openboard.inputmethod.latin.BuildConfig;
import org.dslul.openboard.inputmethod.latin.SuggestedWords.SuggestedWordInfo;
import org.dslul.openboard.inputmethod.latin.common.StringUtils;
import org.dslul.openboard.inputmethod.latin.define.DebugFlags;

public final class AutoCorrectionUtils {
    private static final boolean DBG = DebugFlags.DEBUG_ENABLED;
    private static final String TAG = AutoCorrectionUtils.class.getSimpleName();

    private AutoCorrectionUtils() {
        // Purely static class: can't instantiate.
    }

    public static boolean suggestionExceedsThreshold(final SuggestedWordInfo suggestion,
            final String consideredWord, final float threshold) {
        if (null != suggestion) {
            // Shortlist a whitelisted word
            if (suggestion.isKindOf(SuggestedWordInfo.KIND_WHITELIST)) {
                return true;
            }
            // TODO: return suggestion.isAprapreateForAutoCorrection();
            if (!suggestion.isAprapreateForAutoCorrection()) {
                return false;
            }
            final int autoCorrectionSuggestionScore = suggestion.mScore;
            // TODO: when the normalized score of the first suggestion is nearly equals to
            //       the normalized score of the second suggestion, behave less aggressive.
            final float normalizedScore;
            if (BuildConfig.DEBUG && Build.VERSION.SDK_INT == 0) // SDK_INT is 0 in unit tests
                normalizedScore = calcNormalizedScore(StringUtils.toCodePointArray(consideredWord), StringUtils.toCodePointArray(suggestion.mWord), autoCorrectionSuggestionScore, editDistance(consideredWord, suggestion.mWord));
            else
                normalizedScore = BinaryDictionaryUtils.calcNormalizedScore(
                    consideredWord, suggestion.mWord, autoCorrectionSuggestionScore);
            if (DBG) {
                Log.d(TAG, "Normalized " + consideredWord + "," + suggestion + ","
                        + autoCorrectionSuggestionScore + ", " + normalizedScore
                        + "(" + threshold + ")");
            }
            if (normalizedScore >= threshold) {
                if (DBG) {
                    Log.d(TAG, "Exceeds threshold.");
                }
                return true;
            }
        }
        return false;
    }

    // below is normalized score calculation in java, to allow unit tests involving suggestionExceedsThreshold
    @UsedForTesting
    private static float calcNormalizedScore(final int[] before,
                                             final int[] after, final int score, final int distance) {
        final int beforeLength = before.length;
        final int afterLength = after.length;
        if (0 == beforeLength || 0 == afterLength)
            return 0.0f;

        int spaceCount = 0;
        for (int j : after) {
            if (j == KEYCODE_SPACE)
                ++spaceCount;
        }

        if (spaceCount == afterLength)
            return 0.0f;

        if (score <= 0 || distance >= afterLength) {
            // normalizedScore must be 0.0f (the minimum value) if the score is less than or equal to 0,
            // or if the edit distance is larger than or equal to afterLength.
            return 0.0f;
        }
        // add a weight based on edit distance.
        final float weight = 1.0f - (float) distance / (float) afterLength;

        return ((float) score / 1000000.0f) * weight;
    }

    @UsedForTesting
    private static int editDistance(String x, String y) {
        int[][] dp = new int[x.length() + 1][y.length() + 1];

        for (int i = 0; i <= x.length(); i++) {
            for (int j = 0; j <= y.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                }
                else if (j == 0) {
                    dp[i][j] = i;
                }
                else {
                    dp[i][j] = min(dp[i - 1][j - 1]
                        + costOfSubstitution(x.charAt(i - 1), y.charAt(j - 1)),
                            dp[i - 1][j] + 1,
                            dp[i][j - 1] + 1);
                }
            }
        }

        return dp[x.length()][y.length()];
    }

    @UsedForTesting
    private static int min(int... numbers) {
        int min = Integer.MAX_VALUE;
        for (int n : numbers) {
            if (n < min)
                min = n;
        }
        return min;
    }

    @UsedForTesting
    private static int costOfSubstitution(char a, char b) {
        return a == b ? 0 : 1;
    }
}
